package com.zlorbie788.zeus.core;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

public final class Track {
    private Track() {}

    private static final EquipmentSlot[] SLOTS = Armor.SLOTS;
    private static final int MAX_TRACKED = 512;

    private static final class Rec {
        float health = -1f;
        float lastMeta = Float.NaN;
        boolean exact = true;
        final ItemStack[] stack = new ItemStack[4];
        final int[] base = new int[4];
        final int[] extra = new int[4];
        final int[] max = new int[4];
        final float[] left = {-1f, -1f, -1f, -1f};
        final boolean[] est = {false, false, false, false};
        int lastHurt;
    }

    private static final Map<Integer, Rec> MAP = new HashMap<>();

    public static float health(LivingEntity le) {
        return peek(le).health;
    }

    public static boolean estimated(LivingEntity le) {
        return !peek(le).exact;
    }

    public static float left(LivingEntity le, EquipmentSlot slot) {
        int i = index(slot);
        return i < 0 ? -1f : peek(le).left[i];
    }

    public static boolean leftEstimated(LivingEntity le, EquipmentSlot slot) {
        int i = index(slot);
        return i >= 0 && peek(le).est[i];
    }

    public static int remaining(LivingEntity le, EquipmentSlot slot) {
        int i = index(slot);
        if (i < 0) return -1;
        Rec r = peek(le);
        if (r.max[i] <= 0) return r.left[i] < 0 ? -1 : Integer.MAX_VALUE;
        return Math.max(0, r.max[i] - r.base[i] - r.extra[i]);
    }

    private static int index(EquipmentSlot slot) {
        for (int i = 0; i < SLOTS.length; i++) if (SLOTS[i] == slot) return i;
        return -1;
    }

    public static void touch(LivingEntity le) {
        rec(le);
    }

    private static Rec peek(LivingEntity le) {
        Rec r = MAP.get(le.getId());
        return r != null ? r : rec(le);
    }

    private static Rec rec(LivingEntity le) {
        Rec r = MAP.get(le.getId());
        if (r == null) {
            if (MAP.size() > MAX_TRACKED) MAP.clear();   // bounded, and ids are reused across respawns anyway
            r = new Rec();
            MAP.put(le.getId(), r);
        }
        observe(le, r);
        return r;
    }

    private static void observe(LivingEntity le, Rec r) {
        if (!le.isAlive()) {
            r.health = 0f;
            r.exact = true;
            for (int i = 0; i < SLOTS.length; i++) {   // next life starts on new gear
                r.extra[i] = 0;
                r.est[i] = false;
            }
        } else {
            float meta = le.getHealth();
            if (Float.isNaN(r.lastMeta) || Math.abs(meta - r.lastMeta) > 0.01f) {
                r.health = meta;
                r.exact = true;
            }
            r.lastMeta = meta;
            if (r.health < 0f) r.health = Math.max(0f, meta);
            if (r.health <= 0.01f && meta > 0.01f) {
                r.health = meta;
                r.exact = true;
            }

            if (le.hurtTime > r.lastHurt) {
                if (ours(le)) {
                    float dmg = hitDamage(le);
                    r.health = Math.max(0f, r.health - dmg);
                    r.exact = false;
                    wear(r, dmg);
                } else {
                    r.exact = false;
                }
            }
            r.lastHurt = le.hurtTime;
        }
        observeArmor(le, r);
    }

    private static boolean ours(LivingEntity le) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return false;
        if (le != mc.crosshairPickEntity && le != mc.player.getLastHurtMob()) return false;
        return mc.player.getAttackStrengthScale(0f) < 0.75f;   // the charge reset, so the swing was ours
    }

    private static float hitDamage(LivingEntity target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 1f;
        double dmg;
        try {
            dmg = mc.player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        } catch (Throwable t) {
            dmg = 1.0;
        }
        float strength = mc.player.getAttackStrengthScale(0.5f);
        dmg *= 0.2 + strength * strength * 0.8;                       // vanilla 1.9 charge scaling
        boolean crit = mc.player.fallDistance > 0f && !mc.player.onGround()
                && mc.player.getDeltaMovement().y < 0 && !mc.player.isSprinting();
        if (crit) dmg *= 1.5;
        int points = target.getArmorValue();
        double tough = 0;
        try {
            tough = Math.max(0, target.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        } catch (Throwable ignored) {
            // no attribute on this entity: plain armour points
        }
        double red = Math.min(20.0, Math.max(points / 5.0, points * 4.0 / (tough + 8.0))) / 25.0;
        return (float) Math.max(0.0, dmg * (1.0 - Math.min(1.0, red)));
    }

    private static void wear(Rec r, float dmg) {
        int lose = (int) Math.max(1.0f, dmg / 4.0f);                  // vanilla doHurtEquipment, per worn piece
        for (int i = 0; i < SLOTS.length; i++) {
            if (r.max[i] <= 0) continue;
            r.extra[i] = Math.min(r.max[i], r.extra[i] + lose);
            r.est[i] = true;
            r.left[i] = Math.max(0f, 1f - (float) (r.base[i] + r.extra[i]) / r.max[i]);
        }
    }

    private static void observeArmor(LivingEntity le, Rec r) {
        for (int i = 0; i < SLOTS.length; i++) {
            ItemStack s = le.getItemBySlot(SLOTS[i]);
            if (s.isEmpty()) {
                r.stack[i] = null;
                r.base[i] = 0;
                r.extra[i] = 0;
                r.max[i] = 0;
                r.left[i] = -1f;
                r.est[i] = false;
                continue;
            }
            int md = s.getMaxDamage();
            if (s != r.stack[i] || s.getDamageValue() != r.base[i]) {
                r.stack[i] = s;
                r.base[i] = s.getDamageValue();
                r.extra[i] = 0;
                r.max[i] = md;
                r.est[i] = false;
            }
            if (md <= 0) {
                r.left[i] = 1f;
                r.est[i] = false;
                continue;
            }
            r.left[i] = Math.max(0f, Math.min(1f, 1f - (float) (r.base[i] + r.extra[i]) / md));
        }
    }
}
