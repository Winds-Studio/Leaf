package org.dreeam.leaf.util.list;

import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/// A world name list read from the config, matching either exact names or `*` wildcard patterns.
///
/// `*` matches any (possibly empty) sequence of characters, so `hub*` matches `hub01` and `hub02`,
/// and `*hub*` matches every world whose name contains `hub`. Entries without `*` are matched
/// exactly, as before. Matching is case sensitive, like world folder names.
@NullMarked
public final class WorldList {

    private static final Pattern[] NO_PATTERNS = {};

    public static final WorldList EMPTY = new WorldList(List.of());

    private final Set<String> names = new HashSet<>();
    private final Pattern[] patterns;

    public WorldList(List<String> entries) {
        List<Pattern> patterns = new ArrayList<>();
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            if (entry.indexOf('*') == -1) {
                this.names.add(entry);
            } else {
                patterns.add(compile(entry));
            }
        }
        this.patterns = patterns.toArray(NO_PATTERNS);
    }

    public boolean isEmpty() {
        return this.names.isEmpty() && this.patterns.length == 0;
    }

    public boolean contains(String worldName) {
        if (this.names.contains(worldName)) {
            return true;
        }
        for (Pattern pattern : this.patterns) {
            if (pattern.matcher(worldName).matches()) {
                return true;
            }
        }
        return false;
    }

    private static Pattern compile(String glob) {
        String[] literals = glob.split("\\*", -1);
        StringBuilder regex = new StringBuilder(glob.length() + 8);
        for (int i = 0; i < literals.length; i++) {
            if (i != 0) {
                regex.append(".*");
            }
            if (!literals[i].isEmpty()) {
                regex.append(Pattern.quote(literals[i]));
            }
        }
        // DOTALL so '*' also covers line terminators, world names are not required to be single line
        return Pattern.compile(regex.toString(), Pattern.DOTALL);
    }
}
