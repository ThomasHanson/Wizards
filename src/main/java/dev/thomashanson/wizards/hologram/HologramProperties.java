package dev.thomashanson.wizards.hologram;

import java.awt.Color;

import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * Defines the immutable properties of a Hologram.
 * Use the {@link Builder} to construct an instance of this class.
 *
 * @param visibility The default visibility behavior of the hologram.
 * @param viewDistance The maximum distance from which players can see the hologram.
 * @param attachmentOffset The positional offset to apply when attached to an entity.
 * @param lineWidth The maximum width of a text line before it wraps.
 * @param backgroundColor The background color of the text display.
 * @param textShadow Whether the text should have a shadow.
 * @param seeThrough Whether the text is visible through blocks.
 */
public record HologramProperties(
    @NotNull Visibility visibility,
    int viewDistance,
    @NotNull Vector attachmentOffset,
    int lineWidth,
    int backgroundColor,
    boolean textShadow,
    boolean seeThrough
) {

    public static final int DEFAULT_VIEW_DISTANCE = 64;
    public static final int TRANSPARENT_BACKGROUND = new Color(0, 0, 0, 0).getRGB();

    /**
     * Enum defining the two primary visibility modes for a hologram.
     */
    public enum Visibility {
        /**
         * Visible to all players within the view distance by default.
         * Can still be hidden from specific players via the API.
         */
        PUBLIC,
        /**
         * Invisible to all players by default.
         * Must be explicitly shown to individual players via the API.
         */
        PRIVATE
    }

    /**
     * Creates a new builder for {@link HologramProperties}.
     *
     * @return A new builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A fluent builder for creating {@link HologramProperties} instances.
     */
    public static class Builder {
        private Visibility visibility = Visibility.PUBLIC;
        private int viewDistance = DEFAULT_VIEW_DISTANCE;
        private Vector attachmentOffset = new Vector(0, 0, 0);
        private int lineWidth = 200; // Default Minecraft value
        private int backgroundColor = TRANSPARENT_BACKGROUND;
        private boolean textShadow = false;
        private boolean seeThrough = false;

        private Builder() {}

        /**
         * Sets the visibility mode for the hologram.
         *
         * @param visibility The desired {@link Visibility}.
         * @return This builder instance for chaining.
         */
        public Builder visibility(@NotNull Visibility visibility) {
            this.visibility = visibility;
            return this;
        }

        /**
         * Sets the maximum distance players can see this hologram from.
         *
         * @param viewDistance The distance in blocks.
         * @return This builder instance for chaining.
         */
        public Builder viewDistance(int viewDistance) {
            this.viewDistance = viewDistance;
            return this;
        }

        /**
         * Sets the offset from an entity's location when attached.
         *
         * @param offset The {@link Vector} offset.
         * @return This builder instance for chaining.
         */
        public Builder attachmentOffset(@NotNull Vector offset) {
            this.attachmentOffset = offset;
            return this;
        }

        /**
         * Sets the line width for text wrapping.
         *
         * @param lineWidth The width in pixels.
         * @return This builder instance for chaining.
         */
        public Builder lineWidth(int lineWidth) {
            this.lineWidth = lineWidth;
            return this;
        }

        /**
         * Sets the background color of the text display.
         *
         * @param color The desired {@link Color}. Use an alpha value of 0 for transparent.
         * @return This builder instance for chaining.
         */
        public Builder backgroundColor(@NotNull Color color) {
            this.backgroundColor = color.getRGB();
            return this;
        }

        /**
         * Enables or disables the text shadow.
         *
         * @param textShadow True to enable, false to disable.
         * @return This builder instance for chaining.
         */
        public Builder textShadow(boolean textShadow) {
            this.textShadow = textShadow;
            return this;
        }

        /**
         * Sets whether the hologram can be seen through solid blocks.
         *
         * @param seeThrough True to enable, false to disable.
         * @return This builder instance for chaining.
         */
        public Builder seeThrough(boolean seeThrough) {
            this.seeThrough = seeThrough;
            return this;
        }

        /**
         * Builds the final, immutable {@link HologramProperties} object.
         *
         * @return The configured properties object.
         */
        public HologramProperties build() {
            return new HologramProperties(
                visibility, viewDistance, attachmentOffset,
                lineWidth, backgroundColor, textShadow, seeThrough
            );
        }
    }
}