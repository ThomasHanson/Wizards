package dev.thomashanson.wizards.util.npc.data;

public enum Ping {

    NO_CONNECTION(-1),
    ONE_BAR(1000),
    TWO_BARS(999),
    THREE_BARS(599),
    FOUR_BARS(299),
    FIVE_BARS(149);

    private final int milliseconds;

    Ping(int milliseconds) {
        this.milliseconds = milliseconds;
    }

    public int getMilliseconds() {
        return milliseconds;
    }

    public static Ping fromMilliseconds(int time) {

        for (Ping ping : values())
            if (ping.milliseconds <= time)
                return ping;

        return Ping.NO_CONNECTION;
    }
}