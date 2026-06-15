package com.blib.gradle;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.GradleException;

import java.util.ArrayList;
import java.util.List;

public class BlibRootExtension {

    private String version = null;

    private final ModExtension mod = new ModExtension();

    private final MavenExtension maven;

    private final List<BlibMultiloaderExtension.PomExclusion> pomExclusions = new ArrayList<>();

    private boolean spotlessEnabled = true;

    private boolean jsonMinificationEnabled = true;

    private String recipeViewer = "disabled";

    public BlibRootExtension(org.gradle.api.Project project) {
        this.maven = new MavenExtension(project);
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public ModExtension getMod() {
        return mod;
    }

    public void mod(Action<ModExtension> action) {
        action.execute(mod);
    }

    public void mod(Closure<?> closure) {
        closure.setDelegate(mod);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.call(mod);
    }

    public MavenExtension getMaven() {
        return maven;
    }

    public void maven(Action<MavenExtension> action) {
        action.execute(maven);
    }

    public void maven(Closure<?> closure) {
        closure.setDelegate(maven);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.call(maven);
    }

    public void excludeFromPom(String groupId, String artifactId) {
        pomExclusions.add(new BlibMultiloaderExtension.PomExclusion(groupId, artifactId));
    }

    public List<BlibMultiloaderExtension.PomExclusion> getPomExclusions() {
        return pomExclusions;
    }

    public boolean isSpotlessEnabled() {
        return spotlessEnabled;
    }

    public void setSpotlessEnabled(boolean spotlessEnabled) {
        this.spotlessEnabled = spotlessEnabled;
    }

    public boolean isJsonMinificationEnabled() {
        return jsonMinificationEnabled;
    }

    public void setJsonMinificationEnabled(boolean jsonMinificationEnabled) {
        this.jsonMinificationEnabled = jsonMinificationEnabled;
    }

    public String getRecipeViewer() {
        return recipeViewer;
    }

    public void setRecipeViewer(String recipeViewer) {
        this.recipeViewer = recipeViewer;
    }

    void validate() {
        requireField(version, "version");
        mod.validate();
        maven.getPublish().validate();
    }

    private static void requireField(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new GradleException("Required blib property '" + name + "' not set. Add it to the blib { } block in the root build.gradle.");
        }
    }
}
