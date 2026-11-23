package com.zlorbie788.zeus.core;

public final class Clock {
    private Clock() {
    }

    public static volatile long manual = -1L;
    private static final long OFFSET_MS = System.currentTimeMillis() - System.nanoTime() / 1_000_000L;

    public static long nanoTime() {
        long m = manual;
        return m >= 0 ? m : System.nanoTime();
    }

    public static long millis() {
        long m = manual;
        return m >= 0 ? m / 1_000_000L + OFFSET_MS : System.currentTimeMillis();
    }
}
