package com.blib.gradle;

import org.gradle.api.GradleException;

import java.util.ArrayList;
import java.util.List;

public class ModrinthPublishingExtension {

    private String projectId = null;

    private final List<String> extraRequires = new ArrayList<>();

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
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
                "Required modPublishing.modrinth property 'projectId' not set."
            );
        }
    }
}
