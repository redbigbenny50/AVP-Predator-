package com.blib.gradle;

import com.diffplug.gradle.spotless.SpotlessExtension;
import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.BasePluginExtension;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.authentication.http.BasicAuthentication;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.language.jvm.tasks.ProcessResources;
import org.gradle.plugins.ide.idea.model.IdeaModel;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlibMultiloaderCommonPlugin implements Plugin<Project> {

    private static final List<String> RESOURCE_EXPANSION_FILES = List.of(
        "pack.mcmeta",
        "fabric.mod.json",
        "META-INF/mods.toml",
        "META-INF/neoforge.mods.toml",
        "*.mixins.json"
    );

    private static final List<String> BASE_MAVEN_URLS = List.of(
        "https://maven.bvanseghi.dev/blib",
        "https://maven.bvanseghi.dev/just",
        "https://maven.azuredoom.com/mods",
        "https://maven.terraformersmc.com/releases",
        "https://maven.parchmentmc.org",
        "https://maven.shedaniel.me",
        "https://maven.blamejared.com/",
        "https://modmaven.dev",
        "https://api.modrinth.com/maven"
    );

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("com.diffplug.spotless");
        project.getPluginManager().apply("idea");
        project.getPluginManager().apply("java-library");
        project.getPluginManager().apply("maven-publish");

        var extension = project.getExtensions().create("blib", BlibMultiloaderExtension.class, project);

        exposeBlibProperties(project);
        inheritRootExtension(project, extension);

        configureBase(project);
        configureJava(project);
        configureRepositories(project);
        configureCapabilities(project);
        configureJar(project);
        configureModuleMetadata(project);
        configureIdea(project);
        applyAndConfigureNeoForm(project);
        configureMixinDependencies(project);
        configureSlf4jResolution(project);
        configureBlibCommonDependency(project);
        configureRecipeViewers(project);
        configureCommonArtifacts(project);
        configureRunAllDatagen(project);

        project.afterEvaluate(evaluated -> {
            configureProcessResources(evaluated, extension);
            configurePublishing(evaluated, extension);
            configureSpotless(evaluated, extension);
        });
    }

    private void inheritRootExtension(Project project, BlibMultiloaderExtension extension) {
        var rootExtension = project.getRootProject().getExtensions().findByType(BlibRootExtension.class);

        if (rootExtension == null) {
            return;
        }

        rootExtension.getMaven().applyTo(project);

        extension.getPomExclusions().addAll(rootExtension.getPomExclusions());

        var mavenPublish = rootExtension.getMaven().getPublish();

        if (mavenPublish.getUrl() != null) {
            extension.setMavenUrl(mavenPublish.getUrl());
        }

        extension.setCredentialsUsernameEnv(mavenPublish.getCredentials().getUsernameEnv());
        extension.setCredentialsPasswordEnv(mavenPublish.getCredentials().getPasswordEnv());
        extension.setSpotlessEnabled(rootExtension.isSpotlessEnabled());
        extension.setJsonMinificationEnabled(rootExtension.isJsonMinificationEnabled());
        extension.setRecipeViewer(rootExtension.getRecipeViewer());

        var rootPublishing = rootExtension.getMod().getPublish();
        var subPublishing = extension.getModPublishing();

        if (rootPublishing.isEnabled()) {
            subPublishing.setDryRun(rootPublishing.isDryRun());

            var rootDiscord = rootPublishing.getDiscordWebhook();
            var subDiscord = subPublishing.getDiscordWebhook();
            subDiscord.setUsername(rootDiscord.getUsername());
            subDiscord.setAvatarUrl(rootDiscord.getAvatarUrl());
            subDiscord.setThumbnailUrl(rootDiscord.getThumbnailUrl());

            var rootCf = rootPublishing.getCurseforge();
            var subCf = subPublishing.getCurseforge();
            subCf.setProjectId(rootCf.getProjectId());
            subCf.setProjectSlug(rootCf.getProjectSlug());
            subCf.getExtraRequires().addAll(rootCf.getExtraRequires());

            var rootMr = rootPublishing.getModrinth();
            var subMr = subPublishing.getModrinth();
            subMr.setProjectId(rootMr.getProjectId());
            subMr.getExtraRequires().addAll(rootMr.getExtraRequires());
        }
    }

    private void exposeBlibProperties(Project project) {
        var extra = project.getExtensions().getExtraProperties();
        var rootExtension = project.getRootProject().getExtensions().findByType(BlibRootExtension.class);

        // Ecosystem versions
        extra.set("minecraft_version", BlibVersions.MINECRAFT_VERSION);
        extra.set("minecraft_version_range", BlibVersions.MINECRAFT_VERSION_RANGE);
        extra.set("java_version", BlibVersions.JAVA_VERSION);
        extra.set("fabric_loader_version", BlibVersions.FABRIC_LOADER_VERSION);
        extra.set("fabric_version", BlibVersions.FABRIC_VERSION);
        extra.set("neoforge_version", BlibVersions.NEOFORGE_VERSION);
        extra.set("neo_form_version", BlibVersions.NEO_FORM_VERSION);
        extra.set("neoforge_loader_version_range", BlibVersions.NEOFORGE_LOADER_VERSION_RANGE);
        extra.set("parchment_minecraft", BlibVersions.PARCHMENT_MINECRAFT);
        extra.set("parchment_version", BlibVersions.PARCHMENT_VERSION);

        // Mod properties from root extension (pattern: mod_{propertyName})
        if (rootExtension != null) {
            var mod = rootExtension.getMod();

            extra.set("mod_id", mod.getId());
            extra.set("mod_name", mod.getName());
            extra.set("mod_author", mod.getAuthor());
            extra.set("mod_group", mod.getGroup());
            extra.set("mod_version", mod.getVersion());
            extra.set("mod_icon", mod.getIcon());
            extra.set("mod_license", mod.getLicense());
            extra.set("mod_credits", mod.getCredits());
            extra.set("mod_description", mod.getDescription());

            project.setGroup(mod.getGroup());
            project.setVersion(mod.getVersion());
            project.setDescription(mod.getDescription());
        }
    }

    private void configureBase(Project project) {
        var base = project.getExtensions().getByType(BasePluginExtension.class);
        var modId = requireProperty(project, "mod_id");
        var minecraftVersion = BlibVersions.MINECRAFT_VERSION;

        base.getArchivesName().set(modId + "-" + project.getName() + "-" + minecraftVersion);
    }

    private void configureJava(Project project) {
        var java = project.getExtensions().getByType(JavaPluginExtension.class);
        var javaVersion = BlibVersions.JAVA_VERSION;

        java.withSourcesJar();
        java.withJavadocJar();
        java.getToolchain().getLanguageVersion().set(JavaLanguageVersion.of(javaVersion));
    }

    private void configureRepositories(Project project) {
        var repositories = project.getRepositories();

        repositories.mavenCentral();

        repositories.exclusiveContent(exclusive -> {
            exclusive.forRepository(() -> repositories.maven(repo -> {
                repo.setName("Sponge");
                repo.setUrl("https://repo.spongepowered.org/repository/maven-public");
            }));
            exclusive.filter(filter -> filter.includeGroupAndSubgroups("org.spongepowered"));
        });

        repositories.exclusiveContent(exclusive -> {
            exclusive.forRepositories(
                repositories.maven(repo -> {
                    repo.setName("ParchmentMC");
                    repo.setUrl("https://maven.parchmentmc.org/");
                }),
                repositories.maven(repo -> {
                    repo.setName("NeoForge");
                    repo.setUrl("https://maven.neoforged.net/releases");
                })
            );
            exclusive.filter(filter -> filter.includeGroup("org.parchmentmc.data"));
        });

        repositories.maven(repo -> {
            repo.setName("BlameJared");
            repo.setUrl("https://maven.blamejared.com");
        });

        for (var url : BASE_MAVEN_URLS) {
            repositories.maven(repo -> repo.setUrl(url));
        }
    }

    private void configureCapabilities(Project project) {
        var base = project.getExtensions().getByType(BasePluginExtension.class);
        var modId = requireProperty(project, "mod_id");
        var minecraftVersion = BlibVersions.MINECRAFT_VERSION;
        var publishing = project.getExtensions().getByType(PublishingExtension.class);

        for (var variant : List.of("apiElements", "runtimeElements")) {
            project.getConfigurations().getByName(variant).getOutgoing().capability(
                project.getGroup() + ":" + base.getArchivesName().get() + ":" + project.getVersion()
            );
            project.getConfigurations().getByName(variant).getOutgoing().capability(
                project.getGroup() + ":" + modId + "-" + project.getName() + "-" + minecraftVersion + ":" + project.getVersion()
            );
            project.getConfigurations().getByName(variant).getOutgoing().capability(
                project.getGroup() + ":" + modId + ":" + project.getVersion()
            );

            publishing.getPublications().configureEach(publication -> {
                if (publication instanceof MavenPublication mavenPublication) {
                    mavenPublication.suppressPomMetadataWarningsFor(variant);
                }
            });
        }
    }

    private void configureJar(Project project) {
        var modName = requireProperty(project, "mod_name");
        var modAuthor = requireProperty(project, "mod_author");
        var minecraftVersion = BlibVersions.MINECRAFT_VERSION;

        project.getTasks().named("jar", org.gradle.jvm.tasks.Jar.class, jar -> {
            jar.from(project.getRootProject().file("LICENSE"), spec -> {
                spec.rename(name -> name + "_" + modName);
            });

            jar.manifest(manifest -> {
                var attributes = manifest.getAttributes();
                attributes.put("Specification-Title", modName);
                attributes.put("Specification-Vendor", modAuthor);
                attributes.put("Specification-Version", jar.getArchiveVersion().get());
                attributes.put("Implementation-Title", project.getName());
                attributes.put("Implementation-Version", jar.getArchiveVersion().get());
                attributes.put("Implementation-Vendor", modAuthor);
                attributes.put("Built-On-Minecraft", minecraftVersion);
            });
        });
    }

    private void configureModuleMetadata(Project project) {
        project.getTasks().withType(
            org.gradle.api.publish.tasks.GenerateModuleMetadata.class,
            task -> task.setEnabled(false)
        );
    }

    private void configureIdea(Project project) {
        var idea = project.getExtensions().getByType(IdeaModel.class);

        idea.getModule().setDownloadSources(true);
        idea.getModule().setDownloadJavadoc(true);
    }

    private void configureBlibCommonDependency(Project project) {
        if (project.getExtensions().getExtraProperties().has(BlibMultiloaderLoaderPlugin.LOADER_MODULE_FLAG)) {
            return;
        }

        var blibVersion = resolveBlibVersion(project);
        var minecraftVersion = BlibVersions.MINECRAFT_VERSION;

        project.getDependencies().add("compileOnly",
            "com.blib:blib-common-" + minecraftVersion + ":" + blibVersion);
    }

    private void configureRecipeViewers(Project project) {
        if (project.getExtensions().getExtraProperties().has(BlibMultiloaderLoaderPlugin.LOADER_MODULE_FLAG)) {
            return;
        }

        BlibRecipeViewerConfigurator.configureCommon(project);
    }

    private void configureRunAllDatagen(Project project) {
        var rootProject = project.getRootProject();

        if (rootProject.getTasks().findByName("runAllDatagen") != null) {
            return;
        }

        rootProject.getTasks().register("runAllDatagen", task -> {
            task.setDescription("Runs data generation for both NeoForge and Fabric (in that order).");
            task.dependsOn(":neoforge:runData", ":fabric:runDatagen");
        });

        project.getGradle().projectsEvaluated(gradle ->
            rootProject.project(":fabric").getTasks().named("runDatagen").configure(task ->
                task.mustRunAfter(":neoforge:runData")
            )
        );
    }

    private void applyAndConfigureNeoForm(Project project) {
        if (project.getExtensions().getExtraProperties().has(BlibMultiloaderLoaderPlugin.LOADER_MODULE_FLAG)) {
            return;
        }

        project.getPluginManager().apply("net.neoforged.moddev");

        var neoForge = project.getExtensions().getByType(
            net.neoforged.moddevgradle.dsl.NeoForgeExtension.class
        );

        neoForge.setNeoFormVersion(BlibVersions.NEO_FORM_VERSION);

        var accessTransformer = project.file("src/main/resources/META-INF/accesstransformer.cfg");

        if (accessTransformer.exists()) {
            neoForge.getAccessTransformers().from(accessTransformer.getAbsolutePath());
        }

        neoForge.getParchment().getMinecraftVersion().set(BlibVersions.PARCHMENT_MINECRAFT);
        neoForge.getParchment().getMappingsVersion().set(BlibVersions.PARCHMENT_VERSION);
    }

    private void configureMixinDependencies(Project project) {
        var mixinVersion = BlibVersions.MIXIN_VERSION;
        var mixinExtrasVersion = BlibVersions.MIXINEXTRAS_VERSION;

        project.getDependencies().add("compileOnly",
            "org.spongepowered:mixin:" + mixinVersion);
        project.getDependencies().add("compileOnly",
            "io.github.llamalad7:mixinextras-common:" + mixinExtrasVersion);
        project.getDependencies().add("annotationProcessor",
            "io.github.llamalad7:mixinextras-common:" + mixinExtrasVersion);
    }

    private void configureSlf4jResolution(Project project) {
        var slf4jVersion = BlibVersions.SLF4J_VERSION;
        var logbackVersion = BlibVersions.LOGBACK_CLASSIC_VERSION;

        project.getConfigurations().configureEach(config ->
            config.getResolutionStrategy().eachDependency(details -> {
                var requested = details.getRequested();

                if ("org.slf4j".equals(requested.getGroup()) && "slf4j-api".equals(requested.getName())) {
                    details.useVersion(slf4jVersion);
                    details.because("Aligning SLF4J version to satisfy NeoForge strict constraint");
                }

                if ("ch.qos.logback".equals(requested.getGroup()) && "logback-classic".equals(requested.getName())) {
                    details.useVersion(logbackVersion);
                    details.because("Logback 1.5.x requires slf4j-api 2.0.17+, incompatible with NeoForge");
                }
            })
        );
    }

    private void configureCommonArtifacts(Project project) {
        if (project.getExtensions().getExtraProperties().has(BlibMultiloaderLoaderPlugin.LOADER_MODULE_FLAG)) {
            return;
        }

        var configurations = project.getConfigurations();

        var commonJava = configurations.maybeCreate("commonJava");
        commonJava.setCanBeResolved(false);
        commonJava.setCanBeConsumed(true);

        var commonResources = configurations.maybeCreate("commonResources");
        commonResources.setCanBeResolved(false);
        commonResources.setCanBeConsumed(true);

        project.getTasks().register("prepareGeneratedDir", task ->
            task.doLast(action -> project.file("src/main/generated").mkdirs())
        );

        project.getTasks().named("processResources", task ->
            task.dependsOn("prepareGeneratedDir")
        );

        var java = project.getExtensions().getByType(JavaPluginExtension.class);

        java.getSourceSets().getByName("main", main ->
            main.getResources().srcDir("src/main/generated")
        );

        var packageCommonResources = project.getTasks().register("packageCommonResources",
            org.gradle.api.tasks.Sync.class, task -> {
                task.from(java.getSourceSets().getByName("main").getResources());
                task.into(project.getLayout().getBuildDirectory().dir("commonResourcesOut"));
            });

        project.getArtifacts().add("commonJava",
            java.getSourceSets().getByName("main").getJava().getSourceDirectories().getSingleFile());

        project.getArtifacts().add("commonResources",
            packageCommonResources.get().getOutputs().getFiles().getSingleFile(),
            artifact -> artifact.builtBy(packageCommonResources));
    }

    private void configureProcessResources(Project project, BlibMultiloaderExtension extension) {
        project.getTasks().named("processResources", ProcessResources.class, task -> {
            var expandProperties = buildExpandProperties(project);

            task.filesMatching(RESOURCE_EXPANSION_FILES, details -> details.expand(expandProperties));
            task.getInputs().properties(expandProperties);

            if (!extension.isJsonMinificationEnabled()) {
                return;
            }

            task.doLast(action -> {
                var startTime = System.currentTimeMillis();
                var minified = new int[]{0};
                var bytesSaved = new long[]{0};

                project.fileTree(task.getOutputs().getFiles().getAsPath(), tree -> tree.include("**/*.json"))
                    .forEach(file -> {
                        minified[0]++;
                        var originalLength = file.length();
                        minifyJsonFile(file);
                        bytesSaved[0] += originalLength - file.length();
                    });

                var elapsed = System.currentTimeMillis() - startTime;
                project.getLogger().lifecycle(
                    "Minified {} json files. Saved {} bytes. Took {}ms.",
                    minified[0], bytesSaved[0], elapsed
                );
            });
        });
    }

    private void configurePublishing(Project project, BlibMultiloaderExtension extension) {
        var resolvedUrl = extension.getMavenUrl();

        if (resolvedUrl == null) {
            return;
        }

        var base = project.getExtensions().getByType(BasePluginExtension.class);
        var publishing = project.getExtensions().getByType(PublishingExtension.class);
        var modId = requireProperty(project, "mod_id");
        var finalResolvedUrl = resolvedUrl;

        publishing.getRepositories().maven(repo -> {
            repo.setName(modId);
            repo.setUrl(project.uri(finalResolvedUrl));

            repo.credentials(credentials -> {
                credentials.setUsername(System.getenv(extension.getCredentialsUsernameEnv()));
                credentials.setPassword(System.getenv(extension.getCredentialsPasswordEnv()));
            });

            repo.getAuthentication().create("basic", BasicAuthentication.class);
        });

        publishing.getPublications().create("maven", MavenPublication.class, publication -> {
            publication.setArtifactId(base.getArchivesName().get());
            publication.from(project.getComponents().getByName("java"));

            publication.pom(pom -> pom.withXml(xml -> {
                var dependenciesNodeList = (groovy.util.NodeList) xml.asNode().get("dependencies");

                if (dependenciesNodeList.isEmpty()) {
                    return;
                }

                var dependenciesNode = (groovy.util.Node) dependenciesNodeList.get(0);
                var iterator = dependenciesNode.children().iterator();

                while (iterator.hasNext()) {
                    var dependency = (groovy.util.Node) iterator.next();
                    var groupId = ((groovy.util.Node) ((groovy.util.NodeList) dependency.get("groupId")).get(0)).text();
                    var artifactId = ((groovy.util.Node) ((groovy.util.NodeList) dependency.get("artifactId")).get(0)).text();

                    if ("com.terraformersmc".equals(groupId) && "modmenu".equals(artifactId)) {
                        iterator.remove();
                        continue;
                    }

                    for (var exclusion : extension.getPomExclusions()) {
                        if (exclusion.groupId().equals(groupId) && exclusion.artifactId().equals(artifactId)) {
                            iterator.remove();
                            break;
                        }
                    }
                }
            }));
        });
    }

    private void configureSpotless(Project project, BlibMultiloaderExtension extension) {
        if (!extension.isSpotlessEnabled()) {
            return;
        }

        var formatterFile = project.getRootProject().file("eclipse-formatter.xml");
        var spotless = project.getExtensions().getByType(SpotlessExtension.class);

        spotless.setEnforceCheck(false);

        spotless.java(java -> {
            if (formatterFile.exists()) {
                java.eclipse().configFile(formatterFile);
            }
            java.endWithNewline();
            java.importOrder("", "java", project.getGroup().toString(), "\\#");
            java.leadingTabsToSpaces(4);
            java.removeUnusedImports();
            java.trimTrailingWhitespace();
        });
    }

    private Map<String, Object> buildExpandProperties(Project project) {
        var properties = new HashMap<String, Object>();

        // All project extra properties (from gradle.properties + exposeBlibProperties)
        var extra = project.getExtensions().getExtraProperties();

        for (var name : extra.getProperties().keySet()) {
            var value = extra.getProperties().get(name);

            if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                properties.put(name, value);
            }
        }

        // Core project properties
        properties.put("version", project.getVersion());
        properties.put("group", project.getGroup());
        properties.put("description", project.getDescription());

        // Blib version properties
        var blibVersion = resolveBlibVersion(project);

        properties.put("blib_version", blibVersion);
        properties.put("blib_excl_end_version", calculateExclusiveEndVersion(blibVersion));

        return properties;
    }

    static String calculateExclusiveEndVersion(String version) {
        var baseVersion = version.split("-")[0];
        var parts = baseVersion.split("\\.");

        if (parts.length < 2) {
            throw new GradleException("Cannot calculate exclusive end version from '" + version + "'. Expected major.minor.patch format.");
        }

        var major = Integer.parseInt(parts[0]);
        var minor = Integer.parseInt(parts[1]) + 1;

        return major + "." + minor + ".0";
    }

    static String resolveBlibVersion(Project project) {
        var rootExtension = project.getRootProject().getExtensions().findByType(BlibRootExtension.class);

        if (rootExtension != null && rootExtension.getVersion() != null) {
            return rootExtension.getVersion();
        }

        throw new GradleException("BLib version not set. Add 'version = \"x.y.z\"' to the blib { } block in the root build.gradle.");
    }

    private static String requireProperty(Project project, String name) {
        var value = project.findProperty(name);

        if (value == null) {
            throw new GradleException("Required property '" + name + "' not found. Add it to gradle.properties.");
        }

        return value.toString();
    }

    @SuppressWarnings("unchecked")
    private static void minifyJsonFile(File file) {
        try {
            var content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            var parsed = new JsonSlurper().parseText(content);
            var minified = JsonOutput.toJson(parsed);
            Files.writeString(file.toPath(), minified, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new GradleException("Failed to minify JSON file: " + file.getAbsolutePath(), exception);
        }
    }
}
