package org.dreeam.leaf.version;

import org.galemc.gale.version.AbstractPaperVersionFetcher;

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
}
