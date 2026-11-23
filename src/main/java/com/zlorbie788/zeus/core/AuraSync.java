package com.zlorbie788.zeus.core;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class AuraSync {
    private AuraSync() {}

    private static final long PATIENCE_MS = 1200L;

    private static final long TARGET_MS = 6000L;

    private static final long HOLD_MS = 3000L;

    private static String owner;
    private static boolean holding;
    private static boolean free;
    private static long since;
    private static long heldAt;
    private static int targetId = -1;
    private static long targetAt;

    private static long clock = -1L;
    private static int forced = -1;

    private static long now() {
        return clock >= 0 ? clock : System.currentTimeMillis();
    }

    public static boolean shared() {
        if (forced >= 0) return forced == 1;
        Module crystal = Zeus.get("crystalaura");
        Module anchor = Zeus.get("anchoraura");
        return crystal != null && crystal.enabled() && anchor != null && anchor.enabled();
    }

    public static String owner() {
        return shared() ? owner : null;
    }

    public static boolean allow(String who) {
        if (!shared()) {
            owner = null;
            holding = false;
            free = false;
            return true;
        }
        long at = now();
        if (holding && at - heldAt > HOLD_MS) {
            // The sequence never finished. Whatever the reason, it cannot be allowed to block the other.
            holding = false;
            free = true;
        }
        if (owner == null) {
            owner = who;
            since = at;
            free = false;
        } else if (!owner.equals(who) && !holding && (free || at - since >= PATIENCE_MS)) {
            owner = who;
            since = at;
            free = false;
        }
        return who.equals(owner);
    }

    public static void hold(String who) {
        if (!who.equals(owner)) return;
        holding = true;
        free = false;
        heldAt = now();
        since = heldAt;
    }

    public static void progress(String who) {
        if (!who.equals(owner)) return;
        free = false;
        since = now();
    }

    public static void idle(String who) {
        if (!who.equals(owner)) return;
        holding = false;
        free = true;
        since = now();
    }

    public static void release(String who) {
        idle(who);
    }

    public static void finished(String who) {
        if (!who.equals(owner)) return;
        holding = false;
        free = false;
        since = now();
        owner = who.equals("crystal") ? "anchor" : "crystal";
    }

    public static void target(LivingEntity e) {
        targetId = e == null ? -1 : e.getId();
        targetAt = e == null ? 0L : now();
    }

    public static LivingEntity lastTarget(Minecraft mc) {
        if (targetId < 0 || mc.level == null) return null;
        if (now() - targetAt > TARGET_MS) return null;
        Entity e = mc.level.getEntity(targetId);
        return e instanceof LivingEntity le && le.isAlive() ? le : null;
    }

    public static void clear() {
        owner = null;
        holding = false;
        free = false;
        since = 0L;
        heldAt = 0L;
        targetId = -1;
        targetAt = 0L;
    }

    // ---- headless hooks, used by SelfCheck and by nothing else ----

    static void selfCheck(boolean both, long at) {
        forced = both ? 1 : 0;
        clock = at;
        clear();
    }

    static void clock(long at) {
        clock = at;
    }

    static void selfCheckEnd() {
        forced = -1;
        clock = -1L;
        clear();
    }
}
