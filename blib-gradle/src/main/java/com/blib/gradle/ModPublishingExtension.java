package com.blib.gradle;

import groovy.lang.Closure;
import org.gradle.api.Action;

public class ModPublishingExtension {

    private boolean enabled = false;

    private boolean dryRun = true;

    private final DiscordWebhookExtension discordWebhook = new DiscordWebhookExtension();

    private final CurseForgePublishingExtension curseforge = new CurseForgePublishingExtension();

    private final ModrinthPublishingExtension modrinth = new ModrinthPublishingExtension();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.enabled = true;
        this.dryRun = dryRun;
    }

    public DiscordWebhookExtension getDiscordWebhook() {
        return discordWebhook;
    }

    public void discordWebhook(Action<DiscordWebhookExtension> action) {
        this.enabled = true;
        action.execute(discordWebhook);
    }

    public void discordWebhook(Closure<?> closure) {
        this.enabled = true;
        closure.setDelegate(discordWebhook);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.call(discordWebhook);
    }

    public CurseForgePublishingExtension getCurseforge() {
        return curseforge;
    }

    public void curseforge(Action<CurseForgePublishingExtension> action) {
        this.enabled = true;
        action.execute(curseforge);
    }

    public void curseforge(Closure<?> closure) {
        this.enabled = true;
        closure.setDelegate(curseforge);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.call(curseforge);
    }

    public ModrinthPublishingExtension getModrinth() {
        return modrinth;
    }

    public void modrinth(Action<ModrinthPublishingExtension> action) {
        this.enabled = true;
        action.execute(modrinth);
    }

    public void modrinth(Closure<?> closure) {
        this.enabled = true;
        closure.setDelegate(modrinth);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.call(modrinth);
    }

    void validate() {
        if (!enabled) {
            return;
        }

        curseforge.validate();
        modrinth.validate();
    }
}
