package com.blib.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class BlibMultiloaderFabricPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(BlibMultiloaderLoaderPlugin.class);
        project.getPluginManager().apply("fabric-loom");

        BlibFabricConfigurator.configure(project);

        project.afterEvaluate(evaluated -> {
            var extension = evaluated.getExtensions().getByType(BlibMultiloaderExtension.class);
            var publishing = extension.getModPublishing();

            if (!publishing.isEnabled()) {
                return;
            }

            publishing.getCurseforge().requires("fabric-api");
            publishing.getModrinth().requires("fabric-api");

            evaluated.getPluginManager().apply("me.modmuss50.mod-publish-plugin");

            var remapJar = evaluated.getTasks().named("remapJar");
            var fileArtifact = remapJar.get().getOutputs().getFiles().getSingleFile();

            BlibModPublishConfigurator.configure(
                evaluated, "fabric", "Fabric", fileArtifact, publishing
            );
        });
    }
}
