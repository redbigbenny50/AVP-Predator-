package com.blib.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.language.jvm.tasks.ProcessResources;

public class BlibMultiloaderLoaderPlugin implements Plugin<Project> {

    static final String LOADER_MODULE_FLAG = "blib.isLoaderModule";

    @Override
    public void apply(Project project) {
        project.getExtensions().getExtraProperties().set(LOADER_MODULE_FLAG, true);
        project.getPluginManager().apply(BlibMultiloaderCommonPlugin.class);

        var modId = requireProperty(project, "mod_id");
        var commonJava = project.getConfigurations().maybeCreate("commonJava");
        commonJava.setCanBeResolved(true);

        var commonResources = project.getConfigurations().maybeCreate("commonResources");
        commonResources.setCanBeResolved(true);

        var commonProject = project.project(":common");

        var dependency = (ModuleDependency) project.getDependencies().create(commonProject);
        dependency.capabilities(capabilities -> capabilities.requireCapability(project.getGroup() + ":" + modId));
        project.getDependencies().add("compileOnly", dependency);

        project.getDependencies().add("commonJava", project.getDependencies().project(
            java.util.Map.of("path", ":common", "configuration", "commonJava")
        ));

        project.getDependencies().add("commonResources", project.getDependencies().project(
            java.util.Map.of("path", ":common", "configuration", "commonResources")
        ));

        project.getTasks().named("compileJava", JavaCompile.class, task -> {
            task.dependsOn(commonJava);
            task.source(commonJava);
        });

        project.getTasks().named("processResources", ProcessResources.class, task -> {
            task.dependsOn(commonResources);
            task.from(commonResources);
        });
    }

    private static String requireProperty(Project project, String name) {
        var value = project.findProperty(name);

        if (value == null) {
            throw new GradleException("Required property '" + name + "' not found. Add it to gradle.properties.");
        }

        return value.toString();
    }
}
