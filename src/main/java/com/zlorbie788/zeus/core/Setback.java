package com.zlorbie788.zeus.core;

public final class Setback {
    private Setback() {}

    private static volatile long last;
    private static volatile int count;

    public static void record() {
        long now = System.currentTimeMillis();
        if (now - last > 5000L) count = 0;
        count++;
        last = now;
    }

    public static boolean recent() {
        return System.currentTimeMillis() - last < 3000L;
    }

    public static int corrections() {
        return System.currentTimeMillis() - last < 5000L ? count : 0;
    }

    public static void clear() {
        last = 0L;
        count = 0;
    }
}
