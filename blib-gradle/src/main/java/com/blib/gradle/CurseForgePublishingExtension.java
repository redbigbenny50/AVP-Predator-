package com.blib.gradle;

import org.gradle.api.GradleException;

import java.util.ArrayList;
import java.util.List;

public class CurseForgePublishingExtension {

    private String projectId = null;

    private String projectSlug = null;

    private final List<String> extraRequires = new ArrayList<>();

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getProjectSlug() {
        return projectSlug;
    }

    public void setProjectSlug(String projectSlug) {
        this.projectSlug = projectSlug;
    }

    public List<String> getExtraRequires() {
        return extraRequires;
    }

    public void requires(String... slugs) {
        extraRequires.addAll(List.of(slugs));
    }

    void validate() {
        if (projectId == null || projectId.isBlank()) {
            throw new GradleException(
                "Required modPublishing.curseforge property 'projectId' not set."
            );
        }

        if (projectSlug == null || projectSlug.isBlank()) {
            throw new GradleException(
                "Required modPublishing.curseforge property 'projectSlug' not set."
            );
        }
    }
}
