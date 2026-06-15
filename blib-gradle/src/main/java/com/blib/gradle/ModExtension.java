package com.blib.gradle;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.GradleException;

public class ModExtension {

    private String id = null;

    private final ModPublishingExtension publish = new ModPublishingExtension();

    private String name = null;

    private String author = null;

    private String group = null;

    private String version = null;

    private String icon = null;

    private String license = null;

    private String credits = null;

    private String description = null;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getLicense() {
        return license;
    }

    public void setLicense(String license) {
        this.license = license;
    }

    public String getCredits() {
        return credits;
    }

    public void setCredits(String credits) {
        this.credits = credits;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ModPublishingExtension getPublish() {
        return publish;
    }

    public void publish(Action<ModPublishingExtension> action) {
        action.execute(publish);
    }

    public void publish(Closure<?> closure) {
        closure.setDelegate(publish);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.call(publish);
    }

    void validate() {
        requireField(id, "id");
        requireField(name, "name");
        requireField(author, "author");
        requireField(group, "group");
        requireField(version, "version");
        requireField(icon, "icon");
        requireField(license, "license");
        requireField(credits, "credits");
        requireField(description, "description");

        publish.validate();
    }

    private static void requireField(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new GradleException(
                "Required mod property '" + fieldName + "' not set. Add it to the mod { } block in the blib { } configuration."
            );
        }
    }
}
