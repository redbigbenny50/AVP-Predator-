package com.blib.gradle;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.Project;

import java.util.ArrayList;
import java.util.List;

public class MavenExtension {

    private final Project project;

    private final List<String> repositoryUrls = new ArrayList<>();

    private final MavenPublishExtension publish = new MavenPublishExtension();

    public MavenExtension(Project project) {
        this.project = project;
    }

    public void repository(String url) {
        repositoryUrls.add(url);
    }

    public List<String> getRepositoryUrls() {
        return repositoryUrls;
    }

    public MavenPublishExtension getPublish() {
        return publish;
    }

    public void publish(Action<MavenPublishExtension> action) {
        action.execute(publish);
    }

    public void publish(Closure<?> closure) {
        closure.setDelegate(publish);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.call(publish);
    }

    void applyTo(Project targetProject) {
        for (var url : repositoryUrls) {
            targetProject.getRepositories().maven(repo -> repo.setUrl(url));
        }
    }
}
