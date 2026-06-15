package com.blib.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;

import java.util.Map;

public class BlibMultiloaderSettingsPlugin implements Plugin<Settings> {

    private static final Map<String, String> PLUGIN_VERSION_PROPERTIES = Map.of();

    @Override
    public void apply(Settings settings) {
        settings.getPluginManager().apply("org.gradle.toolchains.foojay-resolver-convention");
        configurePluginRepositories(settings);
        configurePluginVersionResolution(settings);
    }

    private void configurePluginRepositories(Settings settings) {
        var repositories = settings.getPluginManagement().getRepositories();

        repositories.exclusiveContent(exclusive -> {
            exclusive.forRepository(() -> repositories.maven(repo -> {
                repo.setName("Fabric");
                repo.setUrl("https://maven.fabricmc.net");
            }));
            exclusive.filter(filter -> {
                filter.includeGroupAndSubgroups("net.fabricmc");
                filter.includeGroup("fabric-loom");
            });
        });

        repositories.exclusiveContent(exclusive -> {
            exclusive.forRepository(() -> repositories.maven(repo -> {
                repo.setName("Sponge");
                repo.setUrl("https://repo.spongepowered.org/repository/maven-public");
            }));
            exclusive.filter(filter -> filter.includeGroupAndSubgroups("org.spongepowered"));
        });

        repositories.exclusiveContent(exclusive -> {
            exclusive.forRepository(() -> repositories.maven(repo -> {
                repo.setName("Forge");
                repo.setUrl("https://maven.minecraftforge.net");
            }));
            exclusive.filter(filter -> filter.includeGroupAndSubgroups("net.minecraftforge"));
        });
    }

    private void configurePluginVersionResolution(Settings settings) {
        settings.getPluginManagement().resolutionStrategy(strategy ->
            strategy.eachPlugin(details -> {
                var pluginId = details.getRequested().getId().getId();
                var propertyName = PLUGIN_VERSION_PROPERTIES.get(pluginId);

                if (propertyName == null) {
                    return;
                }

                var version = settings.getProviders().gradleProperty(propertyName).getOrNull();

                if (version != null) {
                    details.useVersion(version);
                }
            })
        );
    }
}
