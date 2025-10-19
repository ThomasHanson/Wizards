package dev.thomashanson.wizards.game;

/**
 * Represents a component that can be updated by the master game loop.
 * This provides a standardized way for various game components to perform actions
 * at their own specified frequency, managed by a single, efficient scheduler.
 */
public interface Tickable {

    /**
     * Executes a single tick of logic for this component.
     * <p>
     * This method is automatically called by the master game loop at the interval
     * defined by {@link #getTickInterval()}. The implementing class no longer needs
     * to perform its own interval checks (e.g., {@code if (gameTick % interval == 0)}).
     *
     * @param gameTick The current, total tick count of the game. This can be used
     * for duration-based calculations within the component's logic.
     */
    void tick(long gameTick);

    /**
     * Defines how often, in server ticks, the {@link #tick(long)} method should be called
     * for this component by the master game loop.
     * <p>
     * Overriding this method allows for significant performance optimization. For example:
     * <ul>
     * <li>Returning {@code 1}: Updates every server tick (20 times/sec). Ideal for projectiles.</li>
     * <li>Returning {@code 4}: Updates 5 times per second. Good for frequent checks like runes.</li>
     * <li>Returning {@code 20}: Updates once per second. Ideal for block-melting logic.</li>
     * </ul>
     * The master loop handles the interval check, so the {@link #tick(long)} method can focus
     * purely on its own logic.
     *
     * @return The desired update interval in server ticks. Defaults to 1.
     */
    default int getTickInterval() {
        return 1;
    }
}

