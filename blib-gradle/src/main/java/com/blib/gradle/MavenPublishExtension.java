package com.blib.gradle;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.GradleException;

public class MavenPublishExtension {

    private String url = null;

    private final MavenCredentialsExtension credentials = new MavenCredentialsExtension();

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public MavenCredentialsExtension getCredentials() {
        return credentials;
    }

    public void credentials(Action<MavenCredentialsExtension> action) {
        action.execute(credentials);
    }

    public void credentials(Closure<?> closure) {
        closure.setDelegate(credentials);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.call(credentials);
    }

    void validate() {
        if (url == null || url.isBlank()) {
            throw new GradleException(
                "Required maven.publish property 'url' not set."
            );
        }

        credentials.validate();
    }
}
