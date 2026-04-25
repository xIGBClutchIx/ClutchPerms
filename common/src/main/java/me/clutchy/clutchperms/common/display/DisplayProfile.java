package me.clutchy.clutchperms.common.display;

import java.util.Objects;
import java.util.Optional;

/**
 * Optional prefix and suffix display values for a subject or group.
 *
 * @param prefix optional prefix
 * @param suffix optional suffix
 */
public record DisplayProfile(Optional<DisplayText> prefix, Optional<DisplayText> suffix) {

    /**
     * Creates a validated display profile.
     */
    public DisplayProfile {
        prefix = Objects.requireNonNull(prefix, "prefix");
        suffix = Objects.requireNonNull(suffix, "suffix");
    }

    /**
     * Returns an empty display profile.
     *
     * @return empty display profile
     */
    public static DisplayProfile empty() {
        return new DisplayProfile(Optional.empty(), Optional.empty());
    }

    /**
     * Returns whether this profile has no display values.
     *
     * @return {@code true} when both values are unset
     */
    public boolean isEmpty() {
        return prefix.isEmpty() && suffix.isEmpty();
    }

    /**
     * Returns this profile with a prefix.
     *
     * @param value prefix value
     * @return updated profile
     */
    public DisplayProfile withPrefix(DisplayText value) {
        return new DisplayProfile(Optional.of(Objects.requireNonNull(value, "value")), suffix);
    }

    /**
     * Returns this profile without a prefix.
     *
     * @return updated profile
     */
    public DisplayProfile withoutPrefix() {
        return new DisplayProfile(Optional.empty(), suffix);
    }

    /**
     * Returns this profile with a suffix.
     *
     * @param value suffix value
     * @return updated profile
     */
    public DisplayProfile withSuffix(DisplayText value) {
        return new DisplayProfile(prefix, Optional.of(Objects.requireNonNull(value, "value")));
    }

    /**
     * Returns this profile without a suffix.
     *
     * @return updated profile
     */
    public DisplayProfile withoutSuffix() {
        return new DisplayProfile(prefix, Optional.empty());
    }
}
