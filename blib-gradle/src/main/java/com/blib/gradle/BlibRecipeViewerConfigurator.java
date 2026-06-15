package com.blib.gradle;

import org.gradle.api.Project;

final class BlibRecipeViewerConfigurator {

    static void configureCommon(Project project) {
        var minecraftVersion = BlibVersions.MINECRAFT_VERSION;

        project.getDependencies().add("compileOnly",
            "mezz.jei:jei-" + minecraftVersion + "-neoforge-api:" + BlibVersions.JEI_VERSION);

        project.getDependencies().add("compileOnly",
            "me.shedaniel:RoughlyEnoughItems-api-neoforge:" + BlibVersions.REI_VERSION);
        project.getDependencies().add("compileOnly",
            "me.shedaniel:RoughlyEnoughItems-neoforge:" + BlibVersions.REI_VERSION);
    }

    static void configureLoader(Project project, String loaderSuffix, String compileConfig, String runtimeConfig) {
        var minecraftVersion = BlibVersions.MINECRAFT_VERSION;

        var extension = project.getExtensions().findByType(BlibMultiloaderExtension.class);
        var recipeViewer = extension != null ? extension.getRecipeViewer() : "disabled";

        project.getDependencies().add(compileConfig,
            "dev.emi:emi-" + loaderSuffix + ":" + BlibVersions.EMI_VERSION);

        project.getDependencies().add(compileConfig,
            "mezz.jei:jei-" + minecraftVersion + "-" + loaderSuffix + ":" + BlibVersions.JEI_VERSION);

        project.getDependencies().add(compileConfig,
            "me.shedaniel:RoughlyEnoughItems-" + loaderSuffix + ":" + BlibVersions.REI_VERSION);

        switch (recipeViewer.toLowerCase()) {
            case "emi" -> project.getDependencies().add(runtimeConfig,
                "dev.emi:emi-" + loaderSuffix + ":" + BlibVersions.EMI_VERSION);
            case "jei" -> project.getDependencies().add(runtimeConfig,
                "mezz.jei:jei-" + minecraftVersion + "-" + loaderSuffix + ":" + BlibVersions.JEI_VERSION);
            case "rei" -> project.getDependencies().add(runtimeConfig,
                "me.shedaniel:RoughlyEnoughItems-" + loaderSuffix + ":" + BlibVersions.REI_VERSION);
            case "disabled" -> {}
            default -> project.getLogger().warn(
                "Unknown recipe viewer specified: {}. Must be EMI, REI, JEI, or disabled.",
                recipeViewer
            );
        }
    }

    private BlibRecipeViewerConfigurator() {
        throw new UnsupportedOperationException();
    }
}
