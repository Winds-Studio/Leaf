package org.dreeam.leaf.version;

import org.galemc.gale.version.AbstractPaperVersionFetcher;

public class LeafVersionFetcher extends AbstractPaperVersionFetcher {

    public static final String DOWNLOAD_PAGE = "https://www.leafmc.one/download";
    public static final String API_URL = "https://api.leafmc.one/v2/projects/leaf/versions/" + AbstractPaperVersionFetcher.BUILD_INFO.minecraftVersionId() + "/builds";

    public LeafVersionFetcher() {
        super(
            DOWNLOAD_PAGE,
            "Winds Studio",
            "Leaf",
            "Winds-Studio",
            "Leaf",
            API_URL,
            null,
            ApiType.BIBLIOTHEK
        );
    }
}
