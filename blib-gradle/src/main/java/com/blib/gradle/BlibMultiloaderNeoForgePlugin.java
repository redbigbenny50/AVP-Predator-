package com.blib.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class BlibMultiloaderNeoForgePlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(BlibMultiloaderLoaderPlugin.class);
        project.getPluginManager().apply("net.neoforged.moddev");

        BlibNeoForgeConfigurator.configure(project);

        project.afterEvaluate(evaluated -> {
            var extension = evaluated.getExtensions().getByType(BlibMultiloaderExtension.class);
            var publishing = extension.getModPublishing();

            if (!publishing.isEnabled()) {
                return;
            }

            evaluated.getPluginManager().apply("me.modmuss50.mod-publish-plugin");

            var jar = evaluated.getTasks().named("jar");
            var fileArtifact = jar.get().getOutputs().getFiles().getSingleFile();

            BlibModPublishConfigurator.configure(
                evaluated, "neoforge", "NeoForge", fileArtifact, publishing
            );
        });
    }
}
