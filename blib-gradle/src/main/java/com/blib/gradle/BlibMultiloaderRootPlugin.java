package com.blib.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class BlibMultiloaderRootPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        var extension = project.getExtensions().create("blib", BlibRootExtension.class, project);

        project.afterEvaluate(evaluated -> extension.validate());
    }
}
