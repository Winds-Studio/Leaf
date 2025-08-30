// Gale - branding changes - version fetcher

package org.galemc.gale.version;

import com.destroystokyo.paper.PaperVersionFetcher;
import com.destroystokyo.paper.VersionHistoryManager;
import com.destroystokyo.paper.util.VersionFetcher;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.logging.LogUtils;
import io.papermc.paper.ServerBuildInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.TextColor.color;

/**
 * An abstract version fetcher, derived from {@link PaperVersionFetcher}.
 * This class was then made to be a superclass of both {@link PaperVersionFetcher}
 * and {@link GaleVersionFetcher}.
 * <br>
 * Changes to {@link PaperVersionFetcher} are indicated by Gale marker comments.
 */
public abstract class AbstractPaperVersionFetcher implements VersionFetcher {
    protected static final Logger LOGGER = LogUtils.getClassLogger();
    protected static final int DISTANCE_ERROR = -1;
    protected static final int DISTANCE_UNKNOWN = -2;

    // Gale start - branding changes - version fetcher
    protected final String downloadPage;
    protected final String organizationDisplayName;
    protected final String projectDisplayName;
    protected final String gitHubOrganizationName;
    protected final String gitHubRepoName;

    protected AbstractPaperVersionFetcher(String downloadPage, String organizationDisplayName, String projectDisplayName, String gitHubOrganizationName, String gitHubRepoName) {
        this.downloadPage = downloadPage;
        this.organizationDisplayName = organizationDisplayName;
        this.projectDisplayName = projectDisplayName;
        this.gitHubOrganizationName = gitHubOrganizationName;
        this.gitHubRepoName = gitHubRepoName;
    }
    // Gale end - branding changes - version fetcher

    @Override
    public long getCacheTime() {
        return 720000;
    }

    @Override
    public @NotNull Component getVersionMessage() {
        final Component updateMessage;
        final ServerBuildInfo build = ServerBuildInfo.buildInfo();
        if (build.buildNumber().isEmpty() || build.gitCommit().isEmpty()) { // Gale - branding changes - version fetcher
            updateMessage = text("You are running a development version without access to version information", color(0xFF5300));
        } else {
            updateMessage = getUpdateStatusMessage(this.gitHubOrganizationName + "/" + this.gitHubRepoName, build); // Gale - branding changes - version fetcher
        }
        final @Nullable Component history = this.getHistory();

        return history != null ? Component.textOfChildren(updateMessage, Component.newline(), history) : updateMessage;
    }

    // Gale start - branding changes - version fetcher
    protected int fetchDistanceFromAPI(final String repo, final ServerBuildInfo build) {
        int distance = DISTANCE_ERROR;

        // Gale start - branding changes - version fetcher
        final Optional<String> gitBranch = build.gitBranch();
        final Optional<String> gitCommit = build.gitCommit();
        if (gitBranch.isPresent() && gitCommit.isPresent()) {
            distance = fetchDistanceFromGitHub(repo, gitBranch.get(), gitCommit.get());
        }

        return distance;
    }
    // Gale end - branding changes - version fetcher

    private Component getUpdateStatusMessage(final String repo, final ServerBuildInfo build) {
        int distance = fetchDistanceFromAPI(repo, build); // Gale - branding changes - version fetcher

        return switch (distance) {
            case DISTANCE_ERROR -> text("* Error obtaining version information", NamedTextColor.RED); // Purpur - Rebrand
            case 0 -> text("* You are running the latest version", NamedTextColor.GREEN); // Purpur - Rebrand
            case DISTANCE_UNKNOWN -> text("* Unknown version", NamedTextColor.YELLOW); // Purpur - Rebrand
            default -> text("* You are " + distance + " version(s) behind", NamedTextColor.YELLOW) // Purpur - Rebrand
                .append(Component.newline())
                .append(text("Download the new version at: ")
                    .append(text(this.downloadPage, NamedTextColor.GOLD) // Gale - branding changes - version fetcher
                        .hoverEvent(text("Click to open", NamedTextColor.WHITE))
                        .clickEvent(ClickEvent.openUrl(this.downloadPage)))); // Gale - branding changes - version fetcher
        };
    }

    // Contributed by Techcable <Techcable@outlook.com> in GH-65
    private static int fetchDistanceFromGitHub(final String repo, final String branch, final String hash) {
        try {
            final HttpURLConnection connection = (HttpURLConnection) URI.create("https://api.github.com/repos/%s/compare/%s...%s".formatted(repo, branch, hash)).toURL().openConnection();
            connection.connect();
            if (connection.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND) return DISTANCE_UNKNOWN; // Unknown commit
            try (final BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                final JsonObject obj = new Gson().fromJson(reader, JsonObject.class);
                final String status = obj.get("status").getAsString();
                return switch (status) {
                    case "identical" -> 0;
                    case "behind" -> obj.get("behind_by").getAsInt();
                    default -> DISTANCE_ERROR;
                };
            } catch (final JsonSyntaxException | NumberFormatException e) {
                LOGGER.error("Error parsing json from GitHub's API", e);
                return DISTANCE_ERROR;
            }
        } catch (final IOException e) {
            LOGGER.error("Error while parsing version", e);
            return DISTANCE_ERROR;
        }
    }

    private @Nullable Component getHistory() {
        final VersionHistoryManager.VersionData data = VersionHistoryManager.INSTANCE.getVersionData();
        if (data == null) {
            return null;
        }

        final @Nullable String oldVersion = data.getOldVersion();
        if (oldVersion == null) {
            return null;
        }

        return text("Previous: " + oldVersion, NamedTextColor.GRAY, TextDecoration.ITALIC); // Purpur - Rebrand
    }
}
