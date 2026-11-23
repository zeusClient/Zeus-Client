package com.zlorbie788.zeus.core;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

public final class Armor {
    private Armor() {}

    public static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    public static float left(ItemStack s) {
        if (s == null || s.isEmpty()) return -1f;
        int max = s.getMaxDamage();
        if (max <= 0) return 1f;
        int dmg = s.getDamageValue();
        return Math.max(0f, Math.min(1f, 1f - (float) dmg / max));
    }

    public static int remaining(ItemStack s) {
        if (s == null || s.isEmpty()) return -1;
        int max = s.getMaxDamage();
        if (max <= 0) return Integer.MAX_VALUE;
        return Math.max(0, max - s.getDamageValue());
    }

    public static String letter(EquipmentSlot s) {
        return switch (s) {
            case HEAD -> "H";
            case CHEST -> "C";
            case LEGS -> "L";
            default -> "B";
        };
    }

    public static String piece(EquipmentSlot s) {
        return switch (s) {
            case HEAD -> "Helmet";
            case CHEST -> "Chestplate";
            case LEGS -> "Leggings";
            default -> "Boots";
        };
    }

    public static EquipmentSlot weakest(LivingEntity e) {
        EquipmentSlot worst = null;
        float f = 2f;
        for (EquipmentSlot s : SLOTS) {
            float v = left(e.getItemBySlot(s));
            if (v >= 0 && v < f) {
                f = v;
                worst = s;
            }
        }
        return worst;
    }

    public static int softestSlot() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return -1;
        int best = -1;
        double bestDmg = Double.MAX_VALUE;
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            double dmg = 1.0; // an empty hand hits for one
            if (!s.isEmpty()) {
                double[] bonus = {0};
            s.forEachModifier(EquipmentSlot.MAINHAND, (attr, mod) -> {
                    if (attr.value() == Attributes.ATTACK_DAMAGE.value()) bonus[0] += mod.amount();
                });
                dmg = 1.0 + bonus[0];
            }
            if (dmg < bestDmg) {
                bestDmg = dmg;
                best = i;
            }
        }
        return best;
    }
}
