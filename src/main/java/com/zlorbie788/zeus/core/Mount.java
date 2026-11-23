package com.zlorbie788.zeus.core;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class Mount {
    private Mount() {}

    private static Entity claimed;
    private static int claimedId = -1;
    private static boolean hadClient;
    private static boolean originalNoPhysics;
    private static boolean originalNoGravity;

    private static volatile boolean serverKnown;
    private static volatile boolean serverNoPhysics;
    private static volatile boolean serverNoGravity;

    public static void claim(Minecraft mc, Entity boat) {
        if (boat == null || boat == claimed) return;
        release(mc);
        claimed = boat;
        claimedId = boat.getId();
        hadClient = true;
        originalNoPhysics = boat.noPhysics;
        originalNoGravity = boat.isNoGravity();
        serverKnown = false;
        serverNoPhysics = false;
        serverNoGravity = false;
        var server = mc.getSingleplayerServer();
        if (server == null) return;
        final int id = claimedId;
        server.execute(() -> {
            Entity se = find(server, id);
            if (se == null) return;
            serverNoPhysics = se.noPhysics;
            serverNoGravity = se.isNoGravity();
            serverKnown = true;
        });
    }

    public static void push(Minecraft mc, Entity boat, Vec3 step, boolean noGravity) {
        if (mc.level == null || boat == null) return;
        Vec3 to = boat.position().add(step);
        boat.setPos(to.x, to.y, to.z);
        boat.setDeltaMovement(Vec3.ZERO);
        boat.setNoGravity(noGravity);
        boat.noPhysics = true;
        boat.hurtMarked = false;
        mirror(mc, boat, to, noGravity);
    }

    public static void release(Minecraft mc) {
        if (claimed == null) return;
        Entity boat = claimed;
        int id = claimedId;
        claimed = null;
        claimedId = -1;
        if (hadClient && boat.isAlive()) {
            boat.noPhysics = originalNoPhysics;
            boat.setNoGravity(originalNoGravity);
            boat.setDeltaMovement(Vec3.ZERO);
        }
        hadClient = false;
        var server = mc.getSingleplayerServer();
        if (server == null) return;
        final boolean nophysics = serverKnown ? serverNoPhysics : false;
        final boolean nogravity = serverKnown ? serverNoGravity : false;
        server.execute(() -> {
            Entity se = find(server, id);
            if (se == null) return;
            se.noPhysics = nophysics;
            se.setNoGravity(nogravity);
            se.setDeltaMovement(Vec3.ZERO);
        });
    }

    private static void mirror(Minecraft mc, Entity boat, Vec3 to, boolean noGravity) {
        var server = mc.getSingleplayerServer();
        if (server == null) return;
        final int id = boat.getId();
        final float yaw = boat.getYRot();
        server.execute(() -> {
            Entity se = find(server, id);
            if (se == null) return;
            se.setPos(to.x, to.y, to.z);
            se.setYRot(yaw);
            se.setDeltaMovement(Vec3.ZERO);
            se.setNoGravity(noGravity);
            se.noPhysics = true;
        });
    }

    private static Entity find(net.minecraft.server.MinecraftServer server, int id) {
        for (var level : server.getAllLevels()) {
            Entity e = level.getEntity(id);
            if (e != null) return e;
        }
        return null;
    }
}
