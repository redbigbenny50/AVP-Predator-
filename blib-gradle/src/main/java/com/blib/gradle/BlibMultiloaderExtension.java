package com.blib.gradle;

import org.gradle.api.Action;
import org.gradle.api.Project;

import java.util.ArrayList;
import java.util.List;

public class BlibMultiloaderExtension {

    private final Project project;

    private final List<PomExclusion> pomExclusions = new ArrayList<>();

    private final ModPublishingExtension modPublishing = new ModPublishingExtension();

    private String mavenUrl = null;

    private String credentialsUsernameEnv = "AVP_MAVEN_USERNAME";

    private String credentialsPasswordEnv = "AVP_MAVEN_PASSWORD";

    private boolean spotlessEnabled = true;

    private String recipeViewer = "disabled";

    private boolean jsonMinificationEnabled = true;

    public BlibMultiloaderExtension(Project project) {
        this.project = project;
    }

    public void mavenRepository(String url) {
        project.getRepositories().maven(repo -> repo.setUrl(url));
    }

    public void excludeFromPom(String groupId, String artifactId) {
        pomExclusions.add(new PomExclusion(groupId, artifactId));
    }

    public List<PomExclusion> getPomExclusions() {
        return pomExclusions;
    }

    public String getMavenUrl() {
        return mavenUrl;
    }

    public void setMavenUrl(String mavenUrl) {
        this.mavenUrl = mavenUrl;
    }

    public String getCredentialsUsernameEnv() {
        return credentialsUsernameEnv;
    }

    public void setCredentialsUsernameEnv(String credentialsUsernameEnv) {
        this.credentialsUsernameEnv = credentialsUsernameEnv;
    }

    public String getCredentialsPasswordEnv() {
        return credentialsPasswordEnv;
    }

    public void setCredentialsPasswordEnv(String credentialsPasswordEnv) {
        this.credentialsPasswordEnv = credentialsPasswordEnv;
    }

    public boolean isSpotlessEnabled() {
        return spotlessEnabled;
    }

    public void setSpotlessEnabled(boolean spotlessEnabled) {
        this.spotlessEnabled = spotlessEnabled;
    }

    public String getRecipeViewer() {
        return recipeViewer;
    }

    public void setRecipeViewer(String recipeViewer) {
        this.recipeViewer = recipeViewer;
    }

    public boolean isJsonMinificationEnabled() {
        return jsonMinificationEnabled;
    }

    public void setJsonMinificationEnabled(boolean jsonMinificationEnabled) {
        this.jsonMinificationEnabled = jsonMinificationEnabled;
    }

    public ModPublishingExtension getModPublishing() {
        return modPublishing;
    }

    public void modPublishing(Action<ModPublishingExtension> action) {
        action.execute(modPublishing);
    }

    public record PomExclusion(String groupId, String artifactId) {}
}
