package com.zlorbie788.zeus.core;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class Aim {
    private Aim() {}

    private static float lastYaw = Float.NaN;
    private static float lastPitch = Float.NaN;

    private static int sentTick = -1;
    private static int sentCount;
    private static float sentYaw;
    private static float sentPitch;

    public static float yawTo(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
    }

    public static float pitchTo(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        return (float) -Math.toDegrees(Math.atan2(dy, flat));
    }

    public static final int P_AURA = 40;
    public static final int P_PLACE = 80;
    public static final int P_CLUTCH = 100;

    private static int sentPriority = Integer.MIN_VALUE;

    public static boolean look(Vec3 point) {
        return request(point, P_AURA);
    }

    public static boolean look(Vec3 point, int priority) {
        return request(point, priority);
    }

    public static boolean request(Vec3 point, int priority) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null || point == null) return false;
        Vec3 eye = mc.player.getEyePosition();
        float yaw = yawTo(eye, point);
        float pitch = Math.max(-90f, Math.min(90f, pitchTo(eye, point)));
        return send(yaw, pitch, priority);
    }

    private static boolean sentOnce;

    private static int silentUntil;
    private static float silentYaw;
    private static float silentPitch;
    private static int fadeLeft;
    private static float fadeTargetYaw;
    private static float fadeTargetPitch;

    public static boolean sendRotation(float yaw, float pitch) {
        return send(yaw, pitch, P_AURA);
    }

    private static boolean send(float yaw, float pitch, int priority) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return false;
        float[] shaped = Hand.shape(yaw, Math.max(-90f, Math.min(90f, pitch)));
        int tick = mc.player.tickCount;
        if (tick != sentTick) {
            sentTick = tick;
            sentCount = 0;
            sentPriority = Integer.MIN_VALUE;
        }
        if (sentCount >= 2) return false;
        if (sentCount >= 1 && priority <= sentPriority) return false;
        float prevYaw = sentYaw;
        float prevPitch = sentPitch;
        boolean had = sentOnce;
        float[] tuned = Stealth.shape(mc, shaped[0], shaped[1], prevYaw, prevPitch, had);
        float y = tuned[0];
        float p = tuned[1];
        float[] allowed = com.zlorbie788.zeus.mods.Oracle.limitRotation(mc.player.getYRot(), mc.player.getXRot(), y, p);
        y = allowed[0];
        p = allowed[1];
        if (!Float.isFinite(y) || !Float.isFinite(p)) return false;
        if (y == sentYaw && p == sentPitch) return true;
        sentOnce = true;
        sentCount++;
        sentPriority = priority;
        sentYaw = y;
        sentPitch = p;
        lastYaw = y;
        lastPitch = p;
        float viewYaw = mc.player.getYRot();
        float viewPitch = mc.player.getXRot();
        double grid = Stealth.grid(mc);
        boolean away = Math.abs(Mth.wrapDegrees(y - viewYaw)) > Math.max(grid * 2.0, 0.35)
                || Math.abs(p - viewPitch) > Math.max(grid * 2.0, 0.35);
        if (away) {
            // Remember the flick: it is held, then walked home in steps instead of snapping back.
            silentYaw = y;
            silentPitch = p;
            silentUntil = tick + Stealth.HOLD_TICKS;
            fadeLeft = 0;
        } else {
            silentUntil = 0;
        }
        Stealth.sent(y, p, viewYaw, viewPitch, prevYaw, prevPitch, had);
        com.zlorbie788.zeus.mods.Oracle.rotation(viewYaw, viewPitch, y, p);
        mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(y, p, mc.player.onGround(), mc.player.horizontalCollision));
        return true;
    }

    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.getConnection() == null || silentUntil == 0) return;
        int tick = mc.player.tickCount;
        if (sentTick == tick) return;                 // a module aimed this tick, it owns the look
        if (tick <= silentUntil) {
            sendRaw(mc, silentYaw, silentPitch, tick);
            return;
        }
        if (fadeLeft == 0) {
            fadeLeft = Stealth.FADE_TICKS;
            fadeTargetYaw = mc.player.getYRot();
            fadeTargetPitch = mc.player.getXRot();
        }
        double grid = Stealth.grid(mc);
        silentYaw = Stealth.fadeStep(silentYaw, fadeTargetYaw, fadeLeft, grid, false);
        silentPitch = Stealth.fadeStep(silentPitch, fadeTargetPitch, fadeLeft, grid, true);
        sendRaw(mc, silentYaw, silentPitch, tick);
        if (--fadeLeft <= 0) {
            silentUntil = 0;
            silentYaw = fadeTargetYaw;
            silentPitch = fadeTargetPitch;
        }
    }

    private static void sendRaw(Minecraft mc, float yaw, float pitch, int tick) {
        float p = Math.max(-90f, Math.min(90f, pitch));
        if (yaw == sentYaw && p == sentPitch) return;
        float prevYaw = sentYaw;
        float prevPitch = sentPitch;
        boolean had = sentOnce;
        sentOnce = true;
        sentTick = tick;
        sentCount = 1;
        sentPriority = Integer.MIN_VALUE;
        sentYaw = yaw;
        sentPitch = p;
        lastYaw = yaw;
        lastPitch = p;
        Stealth.sent(yaw, p, mc.player.getYRot(), mc.player.getXRot(), prevYaw, prevPitch, had);
        mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(yaw, p, mc.player.onGround(), mc.player.horizontalCollision));
    }

    public static double reachTo(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || e == null) return Double.MAX_VALUE;
        Vec3 eye = mc.player.getEyePosition();
        AABB box = e.getBoundingBox();
        double dx = Math.max(box.minX - eye.x, Math.max(0.0, eye.x - box.maxX));
        double dy = Math.max(box.minY - eye.y, Math.max(0.0, eye.y - box.maxY));
        double dz = Math.max(box.minZ - eye.z, Math.max(0.0, eye.z - box.maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static boolean isLookingAt(Vec3 point, double degrees) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || point == null) return true;
        Vec3 eye = mc.player.getEyePosition();
        float yaw = yawTo(eye, point);
        float pitch = pitchTo(eye, point);
        float baseYaw = sentOnce ? sentYaw : mc.player.getYRot();
        float basePitch = sentOnce ? sentPitch : mc.player.getXRot();
        return Math.abs(Mth.wrapDegrees(yaw - baseYaw)) <= degrees
                && Math.abs(pitch - basePitch) <= degrees;
    }

    public static void forget() {
        lastYaw = Float.NaN;
        lastPitch = Float.NaN;
        sentTick = -1;
        sentCount = 0;
    }
}
