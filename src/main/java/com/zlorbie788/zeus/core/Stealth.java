package com.zlorbie788.zeus.core;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

public final class Stealth {
    private Stealth() {}

    public static final int HOLD_TICKS = 2;
    public static final int FADE_TICKS = 4;
    private static final double HAND_STEPS = 1.0;

    private static int attackTick = -1;
    private static int useTick = -1;
    private static int breakTick = -1;
    private static int blockedAttacks;
    private static int blockedUses;
    private static int blockedBreaks;

    private static final int ATTACKS_PER_SECOND = 8;
    private static final int USES_PER_SECOND = 6;
    private static final int BREAKS_PER_SECOND = 6;
    private static volatile int attackBudget = ATTACKS_PER_SECOND;
    private static final java.util.ArrayDeque<Long> ATTACK_WINDOW = new java.util.ArrayDeque<>();
    private static final java.util.ArrayDeque<Long> USE_WINDOW = new java.util.ArrayDeque<>();
    private static final java.util.ArrayDeque<Long> BREAK_WINDOW = new java.util.ArrayDeque<>();

    private static boolean withinSecond(java.util.ArrayDeque<Long> window, int perSecond) {
        long now = System.currentTimeMillis();
        while (!window.isEmpty() && now - window.peekFirst() > 1000L) window.pollFirst();
        if (window.size() >= perSecond) return false;
        window.addLast(now);
        return true;
    }

    private static int samples;
    private static int offGrid;
    private static int repeated;
    private static double maxDelta;

    public static final double ENTITY_REACH = 3.35;

    public static boolean busyThisTick(Minecraft mc) {
        if (mc.player == null) return false;
        int tick = mc.player.tickCount;
        return tick == attackTick || tick == useTick;
    }

    private static int claimedTick = -1;

    /** A module takes the tick for its own swing. The swing itself passes through {@link #allowSwing}. */
    public static boolean allowAttack(Minecraft mc) {
        if (mc.player == null) return false;
        int tick = mc.player.tickCount;
        if (tick == attackTick || !withinSecond(ATTACK_WINDOW, attackBudget)) {
            blockedAttacks++;
            return false;
        }
        attackTick = tick;
        claimedTick = tick;
        return true;
    }

    /**
     * The swing on its way through the game mode. A module that already took the tick keeps it; a click
     * from the mouse takes the tick here. One swing a tick either way.
     */
    public static boolean allowSwing(Minecraft mc) {
        if (mc.player == null) return false;
        int tick = mc.player.tickCount;
        if (tick == claimedTick) {
            claimedTick = -1;
            return true;
        }
        if (tick == attackTick || !withinSecond(ATTACK_WINDOW, attackBudget)) {
            blockedAttacks++;
            return false;
        }
        attackTick = tick;
        return true;
    }

    private static int attackPacketTick = -1;
    private static int usePacketTick = -1;

    public static boolean allowAttackPacket(Minecraft mc) {
        if (mc.player == null) return true;
        int tick = mc.player.tickCount;
        if (tick == attackPacketTick) return false;
        attackPacketTick = tick;
        return true;
    }

    public static boolean allowUsePacket(Minecraft mc) {
        if (mc.player == null) return true;
        int tick = mc.player.tickCount;
        if (tick == usePacketTick) return false;
        usePacketTick = tick;
        return true;
    }

    public static void attackBudget(int perSecond) {
        attackBudget = Math.max(1, Math.min(20, perSecond));
    }

    public static boolean allowUse(Minecraft mc) {
        if (mc.player == null) return false;
        int tick = mc.player.tickCount;
        if (tick == useTick || !withinSecond(USE_WINDOW, USES_PER_SECOND)) {
            blockedUses++;
            return false;
        }
        useTick = tick;
        return true;
    }

    public static boolean allowBreak(Minecraft mc) {
        if (mc.player == null) return false;
        int tick = mc.player.tickCount;
        if (tick == breakTick || !withinSecond(BREAK_WINDOW, BREAKS_PER_SECOND)) {
            blockedBreaks++;
            return false;
        }
        breakTick = tick;
        return true;
    }

    public static double grid(Minecraft mc) {
        try {
            return gridFor(mc.options.sensitivity().get());
        } catch (RuntimeException e) {
            return 0.15;
        }
    }

    public static double gridFor(double sensitivity) {
        double f = sensitivity * 0.6 + 0.2;
        return Math.max(1e-5, f * f * f * 1.2);
    }

    public static float stepTo(float last, float target, double grid, boolean pitch) {
        double delta = pitch ? target - last : Mth.wrapDegrees(target - last);
        long steps = Math.round(delta / grid);
        if (steps == 0 && Math.abs(delta) > grid * 0.35) steps = delta > 0 ? 1 : -1;
        return last + (float) (steps * grid);
    }

    public static float fadeStep(float last, float target, int remaining, double grid, boolean pitch) {
        double delta = pitch ? target - last : Mth.wrapDegrees(target - last);
        long whole = Math.round(Math.abs(delta) / grid);
        if (whole == 0) return last;
        long steps = Math.max(1, Math.round(Math.abs(delta) / grid / Math.max(1, remaining)));
        steps = Math.min(steps, whole);
        return last + (float) (Math.signum(delta) * steps * grid);
    }

    public static float[] shape(Minecraft mc, float yaw, float pitch, float lastYaw, float lastPitch,
                                boolean sentBefore) {
        double grid = grid(mc);
        float fromYaw = sentBefore ? lastYaw : mc.player.getYRot();
        float fromPitch = sentBefore ? lastPitch : mc.player.getXRot();
        float y = stepTo(fromYaw, yaw, grid, false);
        float p = stepTo(fromPitch, Math.max(-90f, Math.min(90f, pitch)), grid, true);
        int jy = (int) Math.round((Math.random() * 2 - 1) * HAND_STEPS);
        int jp = (int) Math.round((Math.random() * 2 - 1) * HAND_STEPS);
        if (jy == 0 && jp == 0) jy = Math.random() < 0.5 ? -1 : 1;
        y += (float) (jy * grid);
        p += (float) (jp * grid * 0.7);
        return new float[]{y, Math.max(-90f, Math.min(90f, p))};
    }

    public static void sent(float yaw, float pitch, float viewYaw, float viewPitch, float lastYaw,
                            float lastPitch, boolean sentBefore) {
        if (!sentBefore) return;
        Minecraft mc = Minecraft.getInstance();
        double grid = grid(mc);
        double dy = Math.abs(Mth.wrapDegrees(yaw - lastYaw));
        double dp = Math.abs(pitch - lastPitch);
        samples++;
        if (grid > 1e-4 && dy > grid) {
            double k = dy / grid;
            if (Math.abs(k - Math.round(k)) > 0.06) offGrid++;
        }
        if (dy == 0 && dp == 0) repeated++;
        maxDelta = Math.max(maxDelta, Math.max(dy, dp));
    }

    public static int samples() {
        return samples;
    }

    public static String summary() {
        String rotations = samples == 0 ? "no rotations sent yet"
                : String.format(java.util.Locale.ROOT, "%.1f%% of %d deltas on the mouse grid, %d repeated, largest %.1f\u00B0",
                100.0 - 100.0 * offGrid / samples, samples, repeated, maxDelta);
        return rotations + String.format(java.util.Locale.ROOT, " \u00B7 held back: %d attacks, %d uses, %d breaks",
                blockedAttacks, blockedUses, blockedBreaks);
    }

    public static boolean clean() {
        return samples < 20 || offGrid * 100 <= samples * 2;
    }

    public static void reset() {
        samples = 0;
        offGrid = 0;
        repeated = 0;
        maxDelta = 0;
        blockedAttacks = 0;
        blockedUses = 0;
        blockedBreaks = 0;
        attackPacketTick = -1;
        usePacketTick = -1;
        claimedTick = -1;
        ATTACK_WINDOW.clear();
        USE_WINDOW.clear();
        BREAK_WINDOW.clear();
    }
}
