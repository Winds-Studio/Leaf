package org.dreeam.leaf.version;

import com.google.common.io.Resources;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import io.papermc.paper.ServerBuildInfo;
import org.galemc.gale.version.AbstractPaperVersionFetcher;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.StreamSupport;

public class LeafVersionFetcher extends AbstractPaperVersionFetcher {

    public LeafVersionFetcher() {
        super(
            "https://www.leafmc.one/download",
            "Winds Studio",
            "Leaf",
            "Winds-Studio",
            "Leaf"
        );
    }

    @Override
    protected int fetchDistanceFromAPI(final String repo, final ServerBuildInfo build) {
        int distance = DISTANCE_ERROR;

        final Optional<String> gitBranch = build.gitBranch();
        final Optional<String> gitCommit = build.gitCommit();
        final OptionalInt buildNumber = build.buildNumber();
        if (gitBranch.isPresent() && gitCommit.isPresent() && buildNumber.isPresent()) {
            distance = fetchDistanceFromLeafApi(build, buildNumber.getAsInt());
        }

        return distance;
    }

    private static int fetchDistanceFromLeafApi(final ServerBuildInfo build, final int current) {
        try {
            try (final BufferedReader reader = Resources.asCharSource(
                URI.create("https://api.leafmc.one/v2/projects/leaf/versions/" + build.minecraftVersionId()).toURL(),
                StandardCharsets.UTF_8
            ).openBufferedStream()) {
                final JsonObject json = new Gson().fromJson(reader, JsonObject.class);
                final JsonArray builds = json.getAsJsonArray("builds");
                final int latest = StreamSupport.stream(builds.spliterator(), false)
                    .mapToInt(JsonElement::getAsInt)
                    .max()
                    .orElseThrow();
                return latest - current;
            } catch (final JsonSyntaxException ex) {
                LOGGER.error("Error parsing json from Leaf's downloads API", ex);
                return DISTANCE_ERROR;
            }
        } catch (final IOException e) {
            LOGGER.error("Error while parsing version", e);
            return DISTANCE_ERROR;
        }
    }
}
