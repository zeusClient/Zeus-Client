package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Aim;
import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Env;
import com.zlorbie788.zeus.core.Hits;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Stealth;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

public final class FireballAura extends Module {
    private final Setting.Num range = add(new Setting.Num("Range", "How far away a fireball is hit out of the air.", 3.6, 2.0, 6.0, 0.1, " blocks"));
    private final Setting.Bool incoming = add(new Setting.Bool("Only incoming", "Leaves fireballs that are already flying away alone.", true));
    private final Setting.Num delay = add(new Setting.Num("Delay", "Least time between two deflects.", 60, 20, 400, 10, " ms"));
    private long next;

    public FireballAura() {
        super("fireballaura", "Fireball Aura", "Hits incoming fireballs out of the air and sends them back where they came from.", Category.COMBAT);
        alias("fireball", "deflect", "fireballdeflect");
    }

    @Override
    protected void onTick(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.gameMode == null || mc.screen != null) return;
        long now = System.currentTimeMillis();
        if (now < next) return;
        double r = range.get();
        Entity best = null;
        double bestD = Double.MAX_VALUE;
        for (Entity e : Env.entities(mc, r)) {
            if (e == mc.player || !e.isAlive()) continue;
            String kind = e.getClass().getSimpleName();
            if (!kind.contains("Fireball") && !kind.contains("WitherSkull")) continue;
            double d = mc.player.distanceToSqr(e);
            if (d > r * r) continue;
            if (incoming.on()) {
                Vec3 motion = e.getDeltaMovement();
                Vec3 toMe = mc.player.getEyePosition().subtract(e.position());
                if (motion.dot(toMe) <= 0) continue;
            }
            if (!mc.player.hasLineOfSight(e)) continue;
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        if (best == null) return;
        if (!Stealth.allowAttack(mc)) return;
        next = now + Math.round(delay.get() * (0.8 + ThreadLocalRandom.current().nextDouble(0.5)));
        Aim.look(best.getBoundingBox().getCenter(), Aim.P_AURA);
        Hits.attempt(best);
        mc.gameMode.attack(mc.player, best);
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    @Override
    public String info() {
        return Math.round(range.get() * 10) / 10.0 + " blocks";
    }
}
