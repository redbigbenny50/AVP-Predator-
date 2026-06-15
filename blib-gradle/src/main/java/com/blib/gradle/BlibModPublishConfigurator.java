package com.blib.gradle;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.gradle.api.GradleException;
import org.gradle.api.Project;

import java.util.List;

final class BlibModPublishConfigurator {

    private static final String PUBLISH_SCRIPT = """
        def keyPropsFile = new File(project.rootProject.projectDir, 'key.properties')

        if (!keyPropsFile.exists()) {
            return
        }

        project.publishMods {
            def releaseProp = new Properties()
            releaseProp.load(new FileInputStream(keyPropsFile))

            changelog = new File(project.rootProject.projectDir, 'CHANGELOG.md').text
            type = STABLE
            file = fileArtifact
            modLoaders.add(loaderName)
            dryRun = isDryRun

            def discordPublishWebhookUrl = dryRun.get()
                ? releaseProp.getProperty('discordDryRunPublishWebhookUrl')
                : releaseProp.getProperty('discordPublishWebhookUrl')

            discord {
                webhookUrl = discordPublishWebhookUrl
                dryRunWebhookUrl = discordPublishWebhookUrl
                username = discordUser
                avatarUrl = discordAvatar

                content = changelog.map { text ->
                    def maxEmbedCharacterCount = 4096
                    def header = "# A new version of ${project.mod_name} for ${loaderDisplayName} has been released!\\n"
                    def footer = "\\n...and more! Check the mod page for more details."

                    def needsTruncation = (header.length() + text.length()) > maxEmbedCharacterCount
                    def maxContentLength = maxEmbedCharacterCount - header.length() - (needsTruncation ? footer.length() : 0)

                    def truncatedText = text

                    if (needsTruncation) {
                        if (text.length() > maxContentLength) {
                            def candidate = text.take(maxContentLength)
                            def lastNewline = candidate.lastIndexOf('\\n')
                            if (lastNewline != -1) {
                                candidate = candidate.take(lastNewline)
                            }
                            truncatedText = candidate
                        }
                    }

                    header + truncatedText + (needsTruncation ? footer : '')
                }

                setPlatforms(publishMods.platforms.curseforge, publishMods.platforms.modrinth)

                style {
                    look = 'MODERN'
                    thumbnailUrl = discordThumbnail
                }
            }

            curseforge {
                projectId = cfProjectId
                projectSlug = cfProjectSlug
                accessToken = releaseProp.getProperty('curseKey')
                minecraftVersions.add(project.minecraft_version)
                announcementTitle = 'Download from CurseForge'
                javaVersions.add(org.gradle.api.JavaVersion.VERSION_21)
                clientRequired = true
                serverRequired = true

                curseforgeExtraRequiresList.each { slug ->
                    requires { it.slug = slug }
                }
            }

            modrinth {
                projectId = mrProjectId
                accessToken = releaseProp.getProperty('modrinthKey')
                minecraftVersions.add(project.minecraft_version)
                announcementTitle = 'Download from Modrinth'

                modrinthExtraRequiresList.each { slug ->
                    requires { it.slug = slug }
                }
            }
        }
    """;

    static void configure(
        Project project,
        String loaderName,
        String loaderDisplayName,
        Object fileArtifact,
        ModPublishingExtension publishing
    ) {
        var binding = new Binding();

        binding.setVariable("project", project);
        binding.setVariable("loaderName", loaderName);
        binding.setVariable("loaderDisplayName", loaderDisplayName);
        binding.setVariable("fileArtifact", fileArtifact);
        binding.setVariable("cfProjectId", publishing.getCurseforge().getProjectId());
        binding.setVariable("cfProjectSlug", publishing.getCurseforge().getProjectSlug());
        binding.setVariable("mrProjectId", publishing.getModrinth().getProjectId());
        binding.setVariable("isDryRun", publishing.isDryRun());
        binding.setVariable("discordUser", publishing.getDiscordWebhook().getUsername());
        binding.setVariable("discordAvatar", publishing.getDiscordWebhook().getAvatarUrl());
        binding.setVariable("discordThumbnail", publishing.getDiscordWebhook().getThumbnailUrl());
        var curseforgeRequires = new java.util.ArrayList<>(publishing.getCurseforge().getExtraRequires());
        var modrinthRequires = new java.util.ArrayList<>(publishing.getModrinth().getExtraRequires());

        if (!curseforgeRequires.contains("blib")) {
            curseforgeRequires.add("blib");
        }

        if (!modrinthRequires.contains("blib")) {
            modrinthRequires.add("blib");
        }

        binding.setVariable("curseforgeExtraRequiresList", curseforgeRequires);
        binding.setVariable("modrinthExtraRequiresList", modrinthRequires);

        var shell = new GroovyShell(project.getBuildscript().getClassLoader(), binding);

        shell.evaluate(PUBLISH_SCRIPT);
    }

    private BlibModPublishConfigurator() {
        throw new UnsupportedOperationException();
    }
}
