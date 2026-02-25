// Gale - semantic version

package org.galemc.gale.version;

import org.jspecify.annotations.NullMarked;

/**
 * A holder for the Gale semantic version.
 */
@NullMarked
public final class GaleSemanticVersion {

    private GaleSemanticVersion() {
        throw new RuntimeException();
    }

    /**
     * A semantic version in the format "<code>major.minor.patch</code>", for example "<code>1.5.1</code>".
     * The <code>major</code> version is incremented when a large and overarching set of features, with a large
     * and overarching common goal or effect, has been added compared to the first release with that major version.
     * The <code>minor</code> version is incremented for each build that has a different intended feature set
     * (for example, some features or part of them were added or removed).
     * The <code>patch</code> version is incremented for small changes that do not affect the goal of any feature,
     * such as bug fixes, performance improvements or changes in wording.
     */
    public static final String version = "0.6.15";

    /**
     * The "<code>major.minor</code>" portion of the {@link #version}.
     */
    public static final String majorMinorVersion;

    static {
        int firstDotIndex = version.indexOf('.');
        int secondDotIndex = version.indexOf('.', firstDotIndex + 1);
        majorMinorVersion = version.substring(0, secondDotIndex);
    }

}
