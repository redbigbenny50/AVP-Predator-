package com.blib.gradle;

import org.gradle.api.GradleException;

public class MavenCredentialsExtension {

    private String usernameEnv = null;

    private String passwordEnv = null;

    public String getUsernameEnv() {
        return usernameEnv;
    }

    public void setUsernameEnv(String usernameEnv) {
        this.usernameEnv = usernameEnv;
    }

    public String getPasswordEnv() {
        return passwordEnv;
    }

    public void setPasswordEnv(String passwordEnv) {
        this.passwordEnv = passwordEnv;
    }

    void validate() {
        if (usernameEnv == null || usernameEnv.isBlank()) {
            throw new GradleException(
                "Required maven.publish.credentials property 'usernameEnv' not set."
            );
        }

        if (passwordEnv == null || passwordEnv.isBlank()) {
            throw new GradleException(
                "Required maven.publish.credentials property 'passwordEnv' not set."
            );
        }
    }
}
