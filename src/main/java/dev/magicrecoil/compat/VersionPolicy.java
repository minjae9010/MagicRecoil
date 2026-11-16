package dev.magicrecoil.compat;

import java.util.Locale;
import java.util.regex.Pattern;

/** Minimum versions, with numeric prerelease ordering instead of string equality. */
public final class VersionPolicy {
    public static final String MINIMUM_MAGIC_SPELLS = "4.0-Beta-17";
    public static final String MINIMUM_MINECRAFT = "1.21.4";
    private static final Pattern VERSION = Pattern.compile(
            "(?i)^v?(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:[- .]?(alpha|beta|rc)[- .]?(\\d+))?(?:[+].*|-(?:SNAPSHOT|dev)(?:[-+].*)?)?$");

    private VersionPolicy() {}

    public static void requireMagicSpells(String version) {
        requireAtLeast("MagicSpells", version, MINIMUM_MAGIC_SPELLS);
    }

    public static void requireMinecraft(String version) {
        requireAtLeast("Paper / Minecraft", version, MINIMUM_MINECRAFT);
    }

    private static void requireAtLeast(String component, String version, String minimum) {
        if (parse(version).compareTo(parse(minimum)) < 0) {
            throw new IllegalArgumentException(component + " " + minimum + " or newer is required; found " + version);
        }
    }

    private static Release parse(String text) {
        var match = VERSION.matcher(text == null ? "" : text.trim());
        if (!match.matches()) throw new IllegalArgumentException("Unrecognized version: " + text);
        try {
            String stage = match.group(4);
            int rank = stage == null ? (text.toLowerCase(Locale.ROOT).contains("snapshot") ? -1 : 3)
                    : switch (stage.toLowerCase(Locale.ROOT)) { case "alpha" -> 0; case "beta" -> 1; default -> 2; };
            return new Release(Integer.parseInt(match.group(1)), Integer.parseInt(match.group(2)),
                    match.group(3) == null ? 0 : Integer.parseInt(match.group(3)), rank,
                    match.group(5) == null ? 0 : Integer.parseInt(match.group(5)));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Invalid version: " + text, error);
        }
    }

    private record Release(int major, int minor, int patch, int stage, int build) implements Comparable<Release> {
        @Override public int compareTo(Release other) {
            int[] left = {major, minor, patch, stage, build};
            int[] right = {other.major, other.minor, other.patch, other.stage, other.build};
            for (int i = 0; i < left.length; i++) {
                int comparison = Integer.compare(left[i], right[i]);
                if (comparison != 0) return comparison;
            }
            return 0;
        }
    }
}
