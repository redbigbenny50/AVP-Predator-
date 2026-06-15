package com.blib.gradle;

import net.neoforged.moddevgradle.dsl.NeoForgeExtension;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;

final class BlibNeoForgeConfigurator {

    static void configure(Project project) {
        configureLocalRuntime(project);
        configureNeoForge(project);
        configureBlibConfigurations(project);
        BlibRecipeViewerConfigurator.configureLoader(project, "neoforge", "compileOnly", "localRuntime");
    }

    private static void configureBlibConfigurations(Project project) {
        var group = project.getGroup().toString();

        var blibCompileOnly = project.getConfigurations().create("blibCompileOnly", config ->
            config.exclude(java.util.Map.of("group", group))
        );
        project.getConfigurations().getByName("compileOnly").extendsFrom(blibCompileOnly);

        var blibRuntimeOnly = project.getConfigurations().create("blibRuntimeOnly", config ->
            config.exclude(java.util.Map.of("group", group))
        );
        project.getConfigurations().getByName("localRuntime").extendsFrom(blibRuntimeOnly);

        var blibMod = project.getConfigurations().create("blibMod", config ->
            config.exclude(java.util.Map.of("group", group))
        );
        project.getConfigurations().getByName("compileOnly").extendsFrom(blibMod);
        project.getConfigurations().getByName("localRuntime").extendsFrom(blibMod);
    }

    private static void configureLocalRuntime(Project project) {
        var localRuntime = project.getConfigurations().maybeCreate("localRuntime");
        localRuntime.setCanBeConsumed(false);
        localRuntime.setVisible(false);

        var java = project.getExtensions().getByType(JavaPluginExtension.class);
        var runtimeClasspathName = java.getSourceSets().getByName("main").getRuntimeClasspathConfigurationName();

        project.getConfigurations().named(runtimeClasspathName, config ->
            config.extendsFrom(localRuntime)
        );
    }

    private static void configureNeoForge(Project project) {
        var modId = requireProperty(project, "mod_id");
        var neoforgeVersion = BlibVersions.NEOFORGE_VERSION;
        var parchmentMinecraft = BlibVersions.PARCHMENT_MINECRAFT;
        var parchmentVersion = BlibVersions.PARCHMENT_VERSION;

        var neoForge = project.getExtensions().getByType(NeoForgeExtension.class);

        neoForge.setVersion(neoforgeVersion);

        var blibVersion = BlibMultiloaderCommonPlugin.resolveBlibVersion(project);
        var minecraftVersion = BlibVersions.MINECRAFT_VERSION;

        project.getDependencies().add("implementation",
            "com.blib:blib-neoforge-" + minecraftVersion + ":" + blibVersion);

        var accessTransformer = project.project(":common")
            .file("src/main/resources/META-INF/accesstransformer.cfg");

        if (accessTransformer.exists()) {
            neoForge.getAccessTransformers().from(accessTransformer.getAbsolutePath());
        }

        neoForge.getParchment().getMinecraftVersion().set(parchmentMinecraft);
        neoForge.getParchment().getMappingsVersion().set(parchmentVersion);

        neoForge.getRuns().configureEach(run -> {
            run.getSystemProperties().put("neoforge.enabledGameTestNamespaces", modId);
            run.getIdeName().set("NeoForge " + capitalize(run.getName()) + " (" + project.getPath() + ")");
            run.getJvmArguments().add("-Dterminal.ansi=true");
        });

        neoForge.getRuns().create("client", run -> {
            run.client();
            run.getGameDirectory().set(project.file("runs/client"));
        });

        neoForge.getRuns().create("data", run -> {
            run.data();
            run.getProgramArguments().addAll(
                "--mod", modId,
                "--all",
                "--output", project.file("../common/src/main/generated/").getAbsolutePath(),
                "--existing", project.file("../common/src/main/resources/").getAbsolutePath()
            );
        });

        neoForge.getRuns().create("server", run -> {
            run.server();
            run.getProgramArguments().add("--nogui");
            run.getGameDirectory().set(project.file("runs/server"));
        });

        neoForge.getMods().create(modId, mod ->
            mod.sourceSet(
                project.getExtensions()
                    .getByType(JavaPluginExtension.class)
                    .getSourceSets()
                    .getByName("main")
            )
        );
    }

    private static String capitalize(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        return Character.toUpperCase(input.charAt(0)) + input.substring(1);
    }

    private static String requireProperty(Project project, String name) {
        var value = project.findProperty(name);

        if (value == null) {
            throw new GradleException("Required property '" + name + "' not found. Add it to gradle.properties.");
        }

        return value.toString();
    }

    private BlibNeoForgeConfigurator() {
        throw new UnsupportedOperationException();
    }
}
