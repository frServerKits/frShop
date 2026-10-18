package dev.limitshop.util;

import java.util.concurrent.TimeUnit;

public final class TimeUtil {

    private TimeUtil() {
    }

    public static String duration(long millis) {
        if (millis <= 0) {
            return "now";
        }
        long seconds = (millis + 999L) / 1000L;
        long days = seconds / 86_400L;
        seconds %= 86_400L;
        long hours = seconds / 3_600L;
        seconds %= 3_600L;
        long minutes = seconds / 60L;
        seconds %= 60L;

        StringBuilder builder = new StringBuilder();
        if (days > 0) {
            builder.append(days).append("d ");
        }
        if (hours > 0) {
            builder.append(hours).append("h ");
        }
        if (minutes > 0) {
            builder.append(minutes).append("m ");
        }
        if (seconds > 0 || builder.isEmpty()) {
            builder.append(seconds).append("s");
        }
        return builder.toString().trim();
    }

    public static String durationSeconds(int seconds) {
        return duration(TimeUnit.SECONDS.toMillis(Math.max(0, seconds)));
    }
}
