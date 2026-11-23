package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Aim;
import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Env;
import com.zlorbie788.zeus.core.Hits;
import com.zlorbie788.zeus.core.Inv;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class MaceMods {
    private MaceMods() {}

    private static boolean ready(Minecraft mc) {
        return mc.player != null && mc.level != null && mc.gameMode != null && mc.getConnection() != null && mc.screen == null;
    }

    private static boolean mayFall(Player p) {
        return !p.isFallFlying() && !p.getAbilities().flying && !p.isPassenger() && !p.isInWater() && !p.isInLava() && !p.onClimbable();
    }

    public static final class MaceDamage extends Module {
        private static volatile boolean on;
        private static volatile boolean sending;
        private static double height = 22;
        private static boolean reachOnly = true;
        private static boolean fullSwing = true;
        private static int lastBoost;
        private static int lastBoostTick = -1;
        private static final double MAX_LIFT = 9.5;

        private final Setting.Num fall = add(new Setting.Num("Fall height", "How many blocks of fall the hit carries. 9.5 is the most one tick can give - a server refuses a move packet over ten blocks and puts you back - so anything above that still gives 9.5. Asking for more is only useful if you were falling anyway.", 9.5, 2, 50, 0.5, " blocks"));
        private final Setting.Bool reach = add(new Setting.Bool("Only when the hit can land", "Only boost a hit when the target is inside your reach. A boosted hit that misses would leave the fall on you.", true));
        private final Setting.Bool charged = add(new Setting.Bool("Wait for full swing", "Do not spend a boost on a half-charged swing. Off by default: a boost that waits for a bar the server never quite shows is a boost you never get.", false));

        public MaceDamage() {
            super("macedamage", "Mace Damage", "Makes every mace hit count as a fall from a great height. Hold a mace and hit.", Category.COMBAT);
            alias("macedmg", "smash", "mace boost");
            fall.onChange(() -> height = fall.get());
            reach.onChange(() -> reachOnly = reach.on());
            charged.onChange(() -> fullSwing = charged.on());
        }

        public static boolean isOn() {
            return on;
        }

        public static boolean sending() {
            return sending;
        }

        @Override
        protected void onEnable() {
            on = true;
            height = fall.get();
            reachOnly = reach.on();
            fullSwing = charged.on();
        }

        @Override
        protected void onDisable() {
            on = false;
        }

        @Override
        public String info() {
            return lastBoost > 0 ? lastBoost + " blocks" : null;
        }

        private static double clearance(Minecraft mc, Player p, double wanted) {
            double free = 0;
            AABB box = p.getBoundingBox();
            for (double h = 0.5; h <= wanted + 0.001; h += 0.5) {
                if (!mc.level.noCollision(p, box.move(0, h, 0))) break;
                free = h;
            }
            return free;
        }

        public static void beforeAttack(Player p, Entity target) {
            if (!on || sending) return;
            Minecraft mc = Minecraft.getInstance();
            if (p != mc.player) return;
            if (mc.getConnection() == null || mc.level == null) return;
            if (!p.getMainHandItem().is(Items.MACE) || !(target instanceof LivingEntity le) || le.isDeadOrDying() || !mayFall(p)) return;
            if (reachOnly && !p.isWithinEntityInteractionRange(target, 0.0)) return;
            if (com.zlorbie788.zeus.core.Setback.recent()) return;
            if (p.getDeltaMovement().y > 0.02) return;
            if (fullSwing && p.getAttackStrengthScale(0.5f) < 0.9f) return;
            double need = height - p.fallDistance;
            if (need < 1.0) return;
            double free = clearance(mc, p, Math.min(need + 0.5, MAX_LIFT));
            if (free < 1.0) {
                lastBoost = 0;
                return;
            }
            int tick = p.tickCount;
            if (tick == lastBoostTick || tick - lastBoostTick < 2) return;
            double step = Math.min(need, Math.min(MAX_LIFT, free));
            if (step < 1.0) return;
            double x = p.getX();
            double y = p.getY();
            double z = p.getZ();
            sending = true;
            try {
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(x, y + step, z, false, false));
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(x, y, z, false, false));
                lastBoost = (int) Math.round(step + p.fallDistance);
                lastBoostTick = tick;
            } finally {
                sending = false;
            }
        }
    }

    public static final class MaceAura extends Module {
        private final Targets.Pick who = Targets.pick(this::add, true, true, false);
        private final Setting.Num detect = add(new Setting.Num("Notice from", "How close a target has to be before the mace is taken out.", 6.0, 3.0, 12.0, 0.5, " blocks"));
        private final Setting.Num reach = add(new Setting.Num("Hit from", "How far away the hit is made. The game's own reach is 3 blocks.", 3.0, 2.5, 4.5, 0.1, " blocks"));
        private final Setting.Choice priority = add(new Setting.Choice("Pick the target by", "Closest, or the one with the least health.", 0, "Closest", "Lowest health"));
        private final Setting.Bool full = add(new Setting.Bool("Wait for full swing", "Only hit with a full attack bar. Off by default, so the smash lands on its own timing instead of waiting for a bar that reads full too rarely in the air.", false));
        private final Setting.Bool humanAim = add(new Setting.Bool("Humanised aim", "Turn the server's copy of your view toward the target at a human speed instead of snapping to it, and hit only once it is on target.", true));
        private final Setting.Num turn = add(new Setting.Num("Turn speed", "The fastest the server-side view turns, in degrees per second.", 420, 180, 1080, 20, "\u00b0/s")).showIf(humanAim::on);
        private final Setting.Bool silent = add(new Setting.Bool("Silent rotation", "Send a look packet at the target right before the hit, without turning your camera.", true)).showIf(() -> !humanAim.on());
        private final Setting.Bool los = add(new Setting.Bool("Line of sight only", "Skip targets behind walls.", true));
        private final Setting.Bool back = add(new Setting.Bool("Switch back", "Go back to your own slot after landing.", true));
        private final Setting.Bool assist = add(new Setting.Bool("Mace assistant", "Line yourself up for the hit: closes the gap to the target and holds you just above its head, so the smash comes down on it instead of sailing past. Only your own movement is touched, and only while this is on.", false));
        private final Setting.Num hover = add(new Setting.Num("Hover above", "How far above the target's head the assistant holds you before it lets you drop.", 0.9, 0.4, 3.0, 0.1, " blocks").showIf(() -> assist.on()));
        private final Setting.Num glide = add(new Setting.Num("Correction speed", "How far it pulls you into line each tick.", 0.45, 0.15, 1.2, 0.05, " blocks/tick").showIf(() -> assist.on()));
        private final Setting.Num hitDelay = add(new Setting.Num("Hit delay", "Ticks between hits on the same target, so the server's answers arrive instead of being drowned out. With Mace Damage on it is best left short.", 2, 0, 8, 1, " ticks").showIf(() -> false));
        private final Setting.Bool confirmHits = add(new Setting.Bool("Stop on misses", "When recent swings at a target are not landing, stop hitting it and pick another.", true));
        private int previous = -1;
        private int lost;
        private String shown;
        private String why = "";
        private boolean warned;
        private final Map<Integer, Long> lastHit = new java.util.HashMap<>();
        private long lookedAt;
        private boolean looked;
        private float aimYaw = Float.NaN;
        private float aimPitch;
        private static volatile MaceAura live;
        private static int swungTick = -1;
        private static volatile long hitAt;

        public MaceAura() {
            super("maceaura", "Mace Aura", "In the air near a target: takes out the mace, smashes it, and keeps going.", Category.COMBAT);
            alias("mace bot", "macebot", "mace killaura");
            add(new Setting.Action("Check now", "Says in chat what the module sees right now: who, how far, the mace slot, whether you are falling, and the last reason it stopped.", this::check));
            live = this;
        }

        private void check() {
            Minecraft mc = Minecraft.getInstance();
            var me = mc.player;
            if (me == null || mc.level == null) {
                com.zlorbie788.zeus.core.Zeus.toast("Mace Aura: not in a world", false);
                return;
            }
            int slot = Inv.hotbar(s -> s.is(Items.MACE));
            String mace;
            if (slot >= 0) {
                mace = "slot " + (slot + 1);
            } else {
                int packed = Inv.main(s -> s.is(Items.MACE));
                mace = packed < 0 ? "none" : "in your inventory, pulled up when it is needed";
            }
            LivingEntity t = pick(mc);
            if (t == null) {
                com.zlorbie788.zeus.core.Zeus.toast("Mace Aura: no target within " + String.format(java.util.Locale.ROOT, "%.1f", detect.get()) + ", mace " + mace + ", last " + why, false);
                return;
            }
            double dist = Math.sqrt(t.getBoundingBox().distanceToSqr(me.getEyePosition()));
            com.zlorbie788.zeus.core.Zeus.toast("Mace Aura: " + t.getName().getString()
                    + " " + String.format(java.util.Locale.ROOT, "%.1f", dist)
                    + ", mace " + mace
                    + ", falling " + (!me.onGround() && me.getDeltaMovement().y <= 0.05 ? "yes " + String.format(java.util.Locale.ROOT, "%.2f", me.fallDistance) : "no")
                    + ", boost " + (MaceDamage.isOn() ? "on" : "off")
                    + ", last " + why, true);
        }

        public static boolean armed() {
            MaceAura m = live;
            return m != null && m.enabled() && System.currentTimeMillis() - hitAt < 500L;
        }

        public static boolean isOn() {
            MaceAura m = live;
            return m != null && m.enabled();
        }

        public static boolean busy() {
            MaceAura m = live;
            var mc = Minecraft.getInstance();
            if (mc.player == null) return false;
            return m != null && m.enabled() && swungTick == mc.player.tickCount;
        }

        @Override
        protected void onTick(Minecraft mc) {
            shown = null;
            if (!ready(mc)) return;
            var me = mc.player;
            boolean boosted = MaceDamage.isOn();

            LivingEntity best = pick(mc);
            if (best == null) {
                why = "no target";
                settle(me);
                return;
            }
            if (assist.on() && !me.onGround() && mayFall(me)) lineUp(me, best);

            boolean airborne = !me.onGround() && mayFall(me) && me.getDeltaMovement().y <= 0.05;
            if (!(airborne || boosted && mayFall(me))) {
                why = me.onGround() ? "on the ground" : "still rising";
                settle(me);
                return;
            }

            if (best instanceof Player blocking && Clutch.AutoStun.holdsMace(blocking)) {
                why = "shield first";
                return;
            }

            int mace = Inv.hotbar(s -> s.is(Items.MACE));
            if (mace < 0) {
                int packed = Inv.main(s -> s.is(Items.MACE));
                if (packed >= 0) {
                    why = "pulling the mace up";
                    warned = false;
                    if (!com.zlorbie788.zeus.core.Stealth.busyThisTick(mc)) Inv.toHotbar(packed, Inv.room());
                    return;
                }
                why = "no mace";
                if (!warned) {
                    warned = true;
                    com.zlorbie788.zeus.core.Zeus.toast("Mace Aura: no mace in your hotbar or inventory", false);
                }
                return;
            }
            warned = false;
            shown = best.getName().getString();
            if (!me.getMainHandItem().is(Items.MACE)) {
                if (previous < 0) previous = Inv.selected();
                Inv.select(mace);
            }
            lost = 0;

            long now = System.currentTimeMillis();
            long since = now - lastHit.getOrDefault(best.getId(), 0L);
            int delay = Math.max(0, hitDelay.i() - 1);
            if (delay > 0 && since < delay * 50L) {
                why = "waiting on this target";
                return;
            }

            AABB box = best.getBoundingBox();
            double eyeDist = box.distanceToSqr(me.getEyePosition());
            double hit = Math.min(Math.max(reach.get(), Combat.Reach.entityRange() - 0.02), com.zlorbie788.zeus.core.Stealth.ENTITY_REACH);
            if (eyeDist > hit * hit) {
                why = "out of reach";
                return;
            }
            if (full.on() && me.getAttackStrengthScale(0f) < 0.97f) {
                why = "waiting for a full swing";
                return;
            }
            if (!Pvp18.spam() && best.invulnerableTime > 10) {
                why = "i-frames";
                return;
            }

            Vec3 aimAt = best.position().add(0, best.getBbHeight() * 0.5, 0);
            if (humanAim.on()) {
                if (!humanAim(me, aimAt)) {
                    why = "aiming";
                    return;
                }
            } else if (silent.on()) {
                if (!looked) {
                    Aim.look(aimAt);
                    lookedAt = now;
                    looked = true;
                    return;
                }
                if (now - lookedAt < 30L) return;
                looked = false;
            } else {
                looked = false;
            }

            if (!com.zlorbie788.zeus.core.Stealth.allowAttack(mc)) {
                why = "another hit took the tick";
                return;
            }
            Hits.attempt(best);
            mc.gameMode.attack(me, best);
            me.swing(InteractionHand.MAIN_HAND);
            swungTick = me.tickCount;
            hitAt = System.currentTimeMillis();
            why = "hit " + best.getName().getString();
            lastHit.put(best.getId(), now);
        }

        private boolean humanAim(Player me, Vec3 point) {
            if (Float.isNaN(aimYaw)) {
                aimYaw = me.getYRot();
                aimPitch = me.getXRot();
            }
            Vec3 eye = me.getEyePosition();
            float wantYaw = Aim.yawTo(eye, point);
            float wantPitch = Math.max(-90f, Math.min(90f, Aim.pitchTo(eye, point)));
            float dy = Combat.AimAssist.wrap(wantYaw - aimYaw);
            float dp = wantPitch - aimPitch;
            float off = Math.max(Math.abs(dy), Math.abs(dp));
            if (off > 0.05f) {
                float step = (float) Math.min(off, turn.get() / 20.0 * ThreadLocalRandom.current().nextDouble(0.88, 1.12));
                aimYaw = Combat.AimAssist.wrap(aimYaw + dy / off * step);
                aimPitch = Math.max(-90f, Math.min(90f, aimPitch + dp / off * step));
                Aim.sendRotation(aimYaw, aimPitch);
            }
            return off <= 4.0f;
        }

        private void lineUp(Player me, LivingEntity t) {
            double dx = t.getX() - me.getX();
            double dz = t.getZ() - me.getZ();
            double flat = Math.sqrt(dx * dx + dz * dz);
            double step = glide.get();
            double mx = 0;
            double mz = 0;
            if (flat > 0.10) {
                double pull = Math.min(Math.min(step, flat), flat * 0.55 + 0.03);
                mx = dx / flat * pull;
                mz = dz / flat * pull;
            }
            double wantY = t.getY() + t.getBbHeight() + hover.get();
            double vy = me.getDeltaMovement().y;
            if (me.getY() < wantY - 0.05) {
                if (vy > -0.02) vy = Math.min(0.40, (wantY - me.getY()) * 0.45);
            } else if (me.getY() > wantY + 0.15) {
                vy = Math.min(vy, -0.22);
            } else if (vy < -0.42) {
                vy = -0.42;
            }
            me.setDeltaMovement(mx, vy, mz);
        }

        private LivingEntity pick(Minecraft mc) {
            var me = mc.player;
            LivingEntity best = null;
            LivingEntity hurt = null;
            double bestD = Double.MAX_VALUE;
            double hurtD = Double.MAX_VALUE;
            double d2 = detect.get() * detect.get();
            Vec3 eye = me.getEyePosition();
            for (Entity e : Env.entities(mc, detect.get() + 1.0)) {
                if (!Targets.accepts(mc, who, e)) continue;
                LivingEntity le = (LivingEntity) e;
                double dist = le.getBoundingBox().distanceToSqr(eye);
                if (dist > d2) continue;
                double rank = priority.index() == 1 ? le.getHealth() + le.getAbsorptionAmount() + dist * 0.001 : dist;
                if (le.invulnerableTime > 10 || le.hurtTime > 0) {
                    if (rank < hurtD && (!los.on() || me.hasLineOfSight(e))) {
                        hurt = le;
                        hurtD = rank;
                    }
                    continue;
                }
                if (rank >= bestD) continue;
                if (los.on() && !me.hasLineOfSight(e)) continue;
                best = le;
                bestD = rank;
            }
            return best != null ? best : hurt;
        }

        private void settle(Player me) {
            if (previous < 0) return;
            if (!me.onGround()) return;
            if (++lost < 6) return;
            if (back.on()) Inv.select(previous);
            previous = -1;
            lost = 0;
        }

        @Override
        protected void onDisable() {
            previous = -1;
            lost = 0;
            warned = false;
            aimYaw = Float.NaN;
        }

        @Override
        protected void cleanSlate() {
            aimYaw = Float.NaN;
        }

        @Override
        public String info() {
            return why;
        }
    }
}
