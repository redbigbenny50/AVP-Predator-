package com.blib.gradle;

import net.fabricmc.loom.api.LoomGradleExtensionAPI;
import org.gradle.api.GradleException;
import org.gradle.api.Project;

import java.io.File;

final class BlibFabricConfigurator {

    static void configure(Project project) {
        configureDependencies(project);
        configureLoom(project);
        configureDatagen(project);
        configureBlibConfigurations(project);
        BlibRecipeViewerConfigurator.configureLoader(project, "fabric", "modCompileOnly", "modLocalRuntime");
    }

    private static void configureBlibConfigurations(Project project) {
        var group = project.getGroup().toString();

        var blibModCompileOnly = project.getConfigurations().create("blibModCompileOnly", config ->
            config.exclude(java.util.Map.of("group", group))
        );
        project.getConfigurations().getByName("modCompileOnly").extendsFrom(blibModCompileOnly);

        var blibModRuntimeOnly = project.getConfigurations().create("blibModRuntimeOnly", config ->
            config.exclude(java.util.Map.of("group", group))
        );
        project.getConfigurations().getByName("modLocalRuntime").extendsFrom(blibModRuntimeOnly);

        var blibMod = project.getConfigurations().create("blibMod", config ->
            config.exclude(java.util.Map.of("group", group))
        );
        project.getConfigurations().getByName("modCompileOnly").extendsFrom(blibMod);
        project.getConfigurations().getByName("modLocalRuntime").extendsFrom(blibMod);
    }

    private static void configureDependencies(Project project) {
        var minecraftVersion = BlibVersions.MINECRAFT_VERSION;
        var parchmentMinecraft = BlibVersions.PARCHMENT_MINECRAFT;
        var parchmentVersion = BlibVersions.PARCHMENT_VERSION;
        var fabricLoaderVersion = BlibVersions.FABRIC_LOADER_VERSION;
        var fabricVersion = BlibVersions.FABRIC_VERSION;

        var dependencies = project.getDependencies();
        var loom = project.getExtensions().getByType(LoomGradleExtensionAPI.class);

        dependencies.add("minecraft", "com.mojang:minecraft:" + minecraftVersion);

        dependencies.add("mappings", loom.layered(spec -> {
            spec.officialMojangMappings();
            spec.parchment(
                "org.parchmentmc.data:parchment-" + parchmentMinecraft + ":" + parchmentVersion + "@zip"
            );
        }));

        dependencies.add("modImplementation", "net.fabricmc:fabric-loader:" + fabricLoaderVersion);
        dependencies.add("modImplementation", "net.fabricmc.fabric-api:fabric-api:" + fabricVersion);

        var blibVersion = BlibMultiloaderCommonPlugin.resolveBlibVersion(project);

        dependencies.add("modImplementation",
            "com.blib:blib-fabric-" + minecraftVersion + ":" + blibVersion);
    }

    private static void configureLoom(Project project) {
        var modId = requireProperty(project, "mod_id");
        var loom = project.getExtensions().getByType(LoomGradleExtensionAPI.class);

        var accessWidener = project.project(":common").file("src/main/resources/" + modId + ".accesswidener");

        if (accessWidener.exists()) {
            loom.getAccessWidenerPath().set(accessWidener);
        }

        loom.getMixin().getDefaultRefmapName().set(modId + ".refmap.json");

        loom.runs(runs -> {
            runs.named("client", client -> {
                client.client();
                client.setConfigName("Fabric Client");
                client.ideConfigGenerated(true);
                client.runDir("runs/client");
            });
            runs.named("server", server -> {
                server.server();
                server.setConfigName("Fabric Server");
                server.ideConfigGenerated(true);
                server.runDir("runs/server");
            });
        });
    }

    private static void configureDatagen(Project project) {
        var modId = requireProperty(project, "mod_id");

        var binding = new groovy.lang.Binding();
        binding.setVariable("project", project);
        binding.setVariable("datagenModId", modId);

        new groovy.lang.GroovyShell(project.getBuildscript().getClassLoader(), binding).evaluate("""
            project.extensions.getByName('fabricApi').configureDataGeneration {
                it.modId = datagenModId
            }
        """);

        project.afterEvaluate(evaluated ->
            evaluated.getTasks().named("runDatagen", task ->
                task.doLast(action -> copyDatagenOutput(evaluated))
            )
        );
    }

    private static void copyDatagenOutput(Project project) {
        var generatedDir = new File(project.getProjectDir(), "src/main/generated");
        var targetDir = new File(project.getRootProject().getProjectDir(), "common/src/main/generated");

        if (!generatedDir.exists()) {
            project.getLogger().lifecycle("No generated data found at: {}", generatedDir);
            return;
        }

        targetDir.mkdirs();

        project.copy(spec -> {
            spec.from(generatedDir);
            spec.into(targetDir);
        });

        project.getLogger().lifecycle("Copied generated data to: {}", targetDir);
        project.delete(generatedDir);
        project.getLogger().lifecycle("Deleted original generated directory: {}", generatedDir);
    }

    private static String requireProperty(Project project, String name) {
        var value = project.findProperty(name);

        if (value == null) {
            throw new GradleException("Required property '" + name + "' not found. Add it to gradle.properties.");
        }

        return value.toString();
    }

    private BlibFabricConfigurator() {
        throw new UnsupportedOperationException();
    }
}
