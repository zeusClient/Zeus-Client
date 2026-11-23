package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.AuraSync;
import com.zlorbie788.zeus.core.Aim;
import com.zlorbie788.zeus.core.Build;
import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Dmg;
import com.zlorbie788.zeus.core.Hits;
import com.zlorbie788.zeus.core.Inv;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

public final class CrystalAnchor {
    private CrystalAnchor() {}

    private static final class ActionCoordinator {
        private static final long GAP = 30_000_000L;

        private static long crystalAt;
        private static long anchorAt;

        private static synchronized boolean claim(String owner) {
            long now = System.nanoTime();
            if (owner.equals("crystal")) {
                if (now - crystalAt < GAP) return false;
                crystalAt = now;
                return true;
            }
            if (now - anchorAt < GAP) return false;
            anchorAt = now;
            return true;
        }
    }

    private static final Predicate<ItemStack> CRYSTAL = Inv.is(Items.END_CRYSTAL);
    private static final Predicate<ItemStack> OBSIDIAN = Inv.is(Items.OBSIDIAN);

    private static boolean base(BlockState s) {
        return s.is(Blocks.OBSIDIAN) || s.is(Blocks.BEDROCK);
    }

    static double crystalReach(BlockPos base) {
        Vec3 eye = Build.eye();
        Vec3 c = new Vec3(base.getX() + 0.5, base.getY() + 1.5, base.getZ() + 0.5);
        double dx = Math.max(0.0, Math.abs(eye.x - c.x) - 1.0);
        double dy = Math.max(0.0, Math.max(c.y - 1.0 - eye.y, eye.y - (c.y + 1.0)));
        double dz = Math.max(0.0, Math.abs(eye.z - c.z) - 1.0);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static int jitterTicks(int ticks) {
        return ticks <= 0 ? ticks : Math.max(1, ticks + ThreadLocalRandom.current().nextInt(3) - 1);
    }

    public static final class CrystalAura extends Module {
        private final Targets.Pick who = Targets.pick(this::add, true, true, false);
        private final Setting.Num targetRange = add(new Setting.Num("Target range", "How far away a target is picked.", 10, 4, 20, 1, " blocks"));
        private final Setting.Num placeRange = add(new Setting.Num("Place range", "How far you place crystals and obsidian.", 4.5, 2, 6, 0.1, " blocks"));
        private final Setting.Num breakRange = add(new Setting.Num("Break range", "How far you hit crystals. A crystal is an entity, so this is the ordinary attack range, not the block range: hits past it are refused and then count as misses.", 3.3, 2, 6, 0.1, " blocks"));
        private final Setting.Num placeDelay = add(new Setting.Num("Place delay", "Ticks between two crystals. 20 ticks are one second.", 2, 0, 20, 1, " ticks"));
        private final Setting.Num breakDelay = add(new Setting.Num("Break delay", "Ticks between two hits on crystals.", 1, 0, 20, 1, " ticks"));
        private final Setting.Num minDamage = add(new Setting.Num("Minimum damage", "A crystal is only used when it would hurt the target at least this much (2 = one heart).", 6, 1, 36, 1, ""));
        private final Setting.Num maxSelf = add(new Setting.Num("Maximum self damage", "A crystal is never used when it would hurt you more than this. It has to allow the damage a crystal at fighting distance really does, or the aura refuses every single spot next to the target.", 12, 0, 36, 1, ""));
        private final Setting.Bool antiSuicide = add(new Setting.Bool("Anti suicide", "Never use a crystal that could kill you.", true));
        private final Setting.Bool obsidian = add(new Setting.Bool("Place obsidian", "Lays obsidian for the crystal when there is nothing to place it on.", true));
        private final Setting.Num obsidianDelay = add(new Setting.Num("Obsidian delay", "Ticks between two obsidian blocks.", 4, 0, 20, 1, " ticks")).showIf(() -> obsidian.on());
        private final Setting.Bool onlyUseful = add(new Setting.Bool("Break only useful crystals", "Only hit crystals that hurt the target. Off: hit every crystal in reach.", true));
        private final Setting.Bool pauseEat = add(new Setting.Bool("Pause while eating", "Do nothing while you use an item.", true));
        private final Setting.Bool los = add(new Setting.Bool("Line of sight only", "Only work on crystals and targets you can actually see.", true));
        private final Setting.Bool rotate = add(new Setting.Bool("Look at it", "Tells the server you look at the crystal or block before you hit or place it (your own view does not move). Some servers refuse hits at things you do not face.", true));
        private final Setting.Bool swing = add(new Setting.Bool("Swing arm", "Show the arm swinging.", true));
        private final Setting.Bool totem = add(new Setting.Bool("Totem in your offhand", "Keep a totem there while this is on - crystal PvP is standing in your own blasts. Turns Auto Totem on together with this aura when it is installed, and with no totem left it only ever takes a small bite out of your own health instead of a fatal one.", true));

        private int placeCd;
        private int totemCd;
        private int breakCd;
        private int obsCd;
        private long warned;
        private String info;
        private BlockPos placedBase;
        private BlockPos enemyBase;
        private BlockPos selfBase;

        public CrystalAura() {
            super("crystalaura", "Crystal Aura", "Keeps two obsidian bases for the fight - one under or beside the target, one in front of you - and sets crystals off on both, so the explosions keep hurting and launching them even while they are in the air.", Category.COMBAT);
        }

        @Override
        protected void onEnable() {
            if (!totem.on()) return;
            Module auto = Zeus.get("autototem");
            if (auto == null || auto.enabled()) return;
            auto.toggle();
            Zeus.toast("Auto Totem on with Crystal Aura: the blast comes with you", true);
        }

        @Override
        protected void onDisable() {
            info = null;
            placedBase = null;
            enemyBase = null;
            selfBase = null;
        }

        @Override
        public String info() {
            return info;
        }

        private float selfDamage(Minecraft mc, Vec3 c) {
            return Dmg.explosion(mc, c, mc.player, 6f);
        }

        private boolean selfSafe(Minecraft mc, float self) {
            if (self > maxSelf.get()) return false;
            if (antiSuicide.on() && self >= mc.player.getHealth() + mc.player.getAbsorptionAmount() - 1f) return false;
            if (totem.on() && !Gear.AutoTotem.available(mc)) {
                return self <= Math.max(2.0f, (mc.player.getHealth() + mc.player.getAbsorptionAmount()) * 0.30f);
            }
            return true;
        }

        @Override
        protected void onTick(Minecraft mc) {
            info = null;
            if (mc.player == null || mc.level == null || mc.gameMode == null || mc.screen != null) return;
            if (pauseEat.on() && mc.player.isUsingItem()) return;
            if (totemCd > 0) totemCd--;
            if (totem.on() && totemCd == 0 && !Gear.AutoTotem.held(mc) && Gear.AutoTotem.available(mc)) {
                Gear.AutoTotem.equip(mc);
                totemCd = 4;        // a swap is two clicks; asking again every tick would flood them
            }
            if (placeCd > 0) placeCd--;
            if (breakCd > 0) breakCd--;
            if (obsCd > 0) obsCd--;
            // Turns are only taken when both auras are on; alone, this one always has the field.
            if (!AuraSync.allow("crystal")) return;
            LivingEntity target = AuraSync.lastTarget(mc);
            if (target == null || !Targets.accepts(mc, who, target)
                    || mc.player.distanceToSqr(target) > (double) targetRange.get() * targetRange.get()) {
                target = Targets.nearest(mc, who, targetRange.get());
            }
            AuraSync.target(target);
            if (target != null && los.on() && !mc.player.hasLineOfSight(target)) target = null;
            if (target != null) info = target.getName().getString();
            if (target == null && placedBase == null) {
                AuraSync.idle("crystal");
                return;
            }

            double breakReach = breakReach();
            if (breakCd == 0) {
                Entity bestCrystal = null;
                float bestScore = -1e9f;
                for (Entity e : com.zlorbie788.zeus.core.Env.entities(mc, breakReach + 1.0)) {
                    if (e.getType() != EntityType.END_CRYSTAL || !e.isAlive()) continue;
                    if (Hits.cold(e.getId())) continue;
                    boolean mine = nearCrystal(e, placedBase) || nearCrystal(e, enemyBase) || nearCrystal(e, selfBase);
                    if (target == null && !mine) continue;
                    if (com.zlorbie788.zeus.core.Aim.reachTo(e) > breakReach) continue;
                    if (los.on() && !mc.player.hasLineOfSight(e)) continue;
                    float dt = target == null ? 0f : Dmg.explosion(mc, e.position(), target, 6f);
                    float ds = selfDamage(mc, e.position());
                    if (!selfSafe(mc, ds)) continue;
                    if (onlyUseful.on() && dt < minDamage.get() && !mine) continue;
                    float score = dt - ds;
                    if (score > bestScore) {
                        bestScore = score;
                        bestCrystal = e;
                    }
                }
                if (bestCrystal != null) {
                    if (!ActionCoordinator.claim("crystal")) { AuraSync.progress("crystal"); return; }
                    if (rotate.on()) Aim.look(bestCrystal.position().add(0, 0.5, 0), Aim.P_PLACE);
                    if (!com.zlorbie788.zeus.core.Stealth.allowAttack(mc)) { AuraSync.progress("crystal"); return; }
                    Hits.attempt(bestCrystal);
                    mc.gameMode.attack(mc.player, bestCrystal);
                    if (swing.on()) mc.player.swing(InteractionHand.MAIN_HAND);
                    breakCd = jitterTicks(breakDelay.i());
                    placedBase = null;
                    placeCd = 0;
                    // A detonation is a finished sequence: the anchor gets the next one.
                    if (target != null) AuraSync.finished("crystal");
                    return;
                }
            }

            if (target == null) {
                AuraSync.idle("crystal");
                return;
            }
            BlockPos tp = target.blockPosition();
            if (placeCd != 0) return;
            if (baseGone(enemyBase)) enemyBase = null;
            if (baseGone(selfBase)) selfBase = null;
            boolean airborne = !target.onGround();

            for (BlockPos b : new BlockPos[]{enemyBase, selfBase}) {
                if (b == null || !crystalFree(b, target)) continue;
                Vec3 c = crystalCenter(b);
                float dt = Dmg.explosion(mc, c, target, 6f);
                if (dt < minDamage.get()) continue;
                float ds = selfDamage(mc, c);
                if (!selfSafe(mc, ds)) continue;
                if (!ActionCoordinator.claim("crystal")) { AuraSync.progress("crystal"); return; }
                if (rotate.on()) Aim.look(c, Aim.P_PLACE);
                if (Build.useOn(Build.top(b), CRYSTAL, swing.on())) {
                    placeCd = jitterTicks(placeDelay.i());
                    placedBase = b;
                    AuraSync.hold("crystal");
                    info = target.getName().getString() + " " + Math.round(dt - ds) + (b == selfBase ? " at mine" : "");
                } else if (Build.rateLimited()) {
                    AuraSync.progress("crystal");       // the tick's use was spent elsewhere, not a dry run
                } else if (System.currentTimeMillis() - warned > 5000) {
                    warned = System.currentTimeMillis();
                    Zeus.toast("Crystal Aura: no end crystals in your hotbar or offhand", false);
                    AuraSync.idle("crystal");
                }
                return;
            }

            if (!obsidian.on() || obsCd != 0) return;
            BlockPos wanted = null;
            if (enemyBase == null) wanted = pickEnemyBase(mc, target, airborne);
            if (wanted == null && selfBase == null) wanted = pickSelfBase(mc, target);
            if (wanted == null) {
                AuraSync.idle("crystal");
                return;
            }
            if (base(Build.state(wanted))) {
                // Someone left an obsidian block here already - it is ours now, and it costs nothing.
                if (enemyBase == null) enemyBase = wanted;
                else selfBase = wanted;
                return;
            }
            if (Inv.hotbar(OBSIDIAN) < 0 && !Inv.offhand(OBSIDIAN)) {
                if (System.currentTimeMillis() - warned > 5000) {
                    warned = System.currentTimeMillis();
                    Zeus.toast("Crystal Aura: no obsidian in your hotbar or offhand", false);
                }
                AuraSync.idle("crystal");
                return;
            }
            if (!ActionCoordinator.claim("crystal")) { AuraSync.progress("crystal"); return; }
            if (rotate.on()) Aim.look(Vec3.atCenterOf(wanted), Aim.P_PLACE);
            if (Build.place(wanted, OBSIDIAN, swing.on(), Build.REACH)) {
                if (enemyBase == null) enemyBase = wanted;
                else selfBase = wanted;
                obsCd = jitterTicks(obsidianDelay.i());
                info = target.getName().getString() + " base";
            }
        }

        private static boolean baseGone(BlockPos b) {
            return b != null && !base(Build.state(b));
        }

        private static Vec3 crystalCenter(BlockPos base) {
            return new Vec3(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5);
        }

        private static boolean nearCrystal(Entity e, BlockPos b) {
            return b != null && e.position().distanceToSqr(b.getX() + 0.5, b.getY() + 1.0, b.getZ() + 0.5) < 2.25;
        }

        private double breakReach() {
            return Math.min(Math.max(breakRange.get(), Combat.Reach.entityRange() - 0.02),
                    com.zlorbie788.zeus.core.Stealth.ENTITY_REACH);
        }

        private boolean crystalFree(BlockPos base, LivingEntity target) {
            BlockPos up = base.above();
            if (!Build.state(up).isAir()) return false;
            if (Build.distTo(base) > placeRange.get()) return false;
            if (crystalReach(base) > breakReach()) return false;
            if (!Build.clearLine(crystalCenter(base))) return false;
            return !Build.blockedExcept(up, target);
        }

        private BlockPos pickEnemyBase(Minecraft mc, LivingEntity target, boolean airborne) {
            BlockPos tp = target.blockPosition();
            Vec3 v = target.getDeltaMovement();
            BlockPos ahead = BlockPos.containing(target.getX() + v.x * 2.5, target.getY(), target.getZ() + v.z * 2.5);
            BlockPos best = null;
            double bestScore = -1e9;
            BlockPos below = tp.below();
            if (baseBuildable(below) && Build.state(tp).isAir()) {
                float dt = Dmg.explosion(mc, crystalCenter(below), target, 6f);
                if (dt >= minDamage.get() && selfSafe(mc, selfDamage(mc, crystalCenter(below)))) {
                    best = below;
                    bestScore = airborne ? 12.0 : 9.0;
                }
            }
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    for (int dy = -1; dy <= 0; dy++) {
                        BlockPos b = ahead.offset(dx, dy, dz);
                        if (!baseBuildable(b)) continue;
                        BlockPos up = b.above();
                        if (!Build.state(up).isAir() || Build.blockedExcept(up, target)) continue;
                        Vec3 c = crystalCenter(b);
                        if (Dmg.explosion(mc, c, target, 6f) < minDamage.get()) continue;
                        if (!selfSafe(mc, selfDamage(mc, c))) continue;
                        double score = 5.0 - Math.abs(dx) * 0.6 - Math.abs(dz) * 0.6 + (dy < 0 ? 1.2 : 0);
                        if (score > bestScore) {
                            bestScore = score;
                            best = b;
                        }
                    }
                }
            }
            return best;
        }

        private BlockPos pickSelfBase(Minecraft mc, LivingEntity target) {
            BlockPos me = mc.player.blockPosition();
            Vec3 look = mc.player.getLookAngle();
            BlockPos best = null;
            double bestScore = -1e9;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dy = -1; dy <= 0; dy++) {
                        BlockPos b = me.offset(dx, dy, dz);
                        if (b.equals(me) || b.equals(me.above())) continue;
                        if (!baseBuildable(b)) continue;
                        BlockPos up = b.above();
                        // Never in yourself: a crystal that spawns in your own body is a suicide.
                        if (!Build.state(up).isAir() || Build.blocked(up)) continue;
                        Vec3 c = crystalCenter(b);
                        if (mc.player.getEyePosition().distanceTo(c) > breakReach()) continue;
                        if (Dmg.explosion(mc, c, target, 6f) < minDamage.get()) continue;
                        if (!selfSafe(mc, selfDamage(mc, c))) continue;
                        double score = 5.0 - Build.distTo(b) * 0.6;
                        Vec3 to = c.subtract(mc.player.getEyePosition());
                        if (to.lengthSqr() > 1e-6 && to.normalize().dot(look) > 0.3) score += 1.5;
                        if (score > bestScore) {
                            bestScore = score;
                            best = b;
                        }
                    }
                }
            }
            return best;
        }

        private boolean baseBuildable(BlockPos b) {
            if (base(Build.state(b))) return true;    // already obsidian or bedrock
            if (!Build.canPlace(b, placeRange.get())) return false;
            return Build.distTo(b) <= placeRange.get();
        }
    }

    public static final class AnchorAura extends Module {
        private final Targets.Pick who = Targets.pick(this::add, true, true, false);
        private final Setting.Num targetRange = add(new Setting.Num("Target range", "How far away a target is picked.", 8, 3, 16, 1, " blocks"));
        private final Setting.Num placeRange = add(new Setting.Num("Place range", "How far you place the anchor.", 4.5, 2, 6, 0.1, " blocks"));
        private final Setting.Num charges = add(new Setting.Num("Charges", "How many times the anchor is charged. Every extra charge uses a glowstone; one charge is already a full explosion.", 1, 1, 4, 1, ""));
        private final Setting.Num delay = add(new Setting.Num("Delay", "Ticks between steps (place, charge, explode).", 2, 0, 20, 1, " ticks"));
        private final Setting.Num minDamage = add(new Setting.Num("Minimum damage", "Only blow up an anchor when it hurts the target at least this much.", 6, 1, 36, 1, ""));
        private final Setting.Num maxSelf = add(new Setting.Num("Maximum self damage", "Never use an anchor that would hurt you more than this.", 12, 0, 36, 1, ""));
        private final Setting.Bool antiSuicide = add(new Setting.Bool("Anti suicide", "Never use an anchor that could kill you.", true));
        private final Setting.Bool rotate = add(new Setting.Bool("Look at it", "Tells the server you look at the anchor before you place, charge or set it off (your own view does not move).", true));
        private final Setting.Bool swing = add(new Setting.Bool("Swing arm", "Show the arm swinging.", true));
        private final Setting.Bool los = add(new Setting.Bool("Line of sight only", "Only work on targets and anchors you can actually see.", true));
        private final Setting.Bool pauseUse = add(new Setting.Bool("Pause while eating", "Do nothing while you use an item.", true));

        private BlockPos anchor;
        private long ticks;
        private long placedAt;
        private long chargedAt;
        private int charged;
        private int cd;
        private long warned;
        private String info;

        public AnchorAura() {
            super("anchoraura", "Anchor Aura", "Places, charges and blows up respawn anchors next to a target.", Category.COMBAT);
        }

        @Override
        protected void onEnable() {
            anchor = null;
            charged = 0;
            ticks = 0;
        }

        static int chargeOf(BlockState st) {
            try {
                for (net.minecraft.world.level.block.state.properties.Property<?> p : st.getProperties()) {
                    if (p.getName().equals("charges") && p instanceof net.minecraft.world.level.block.state.properties.IntegerProperty ip) return st.getValue(ip);
                }
            } catch (RuntimeException ignored) {
            }
            return -1;
        }

        @Override
        public String info() {
            return info;
        }

        private void warn(String text) {
            if (System.currentTimeMillis() - warned > 5000) {
                warned = System.currentTimeMillis();
                Zeus.toast(text, false);
            }
        }

        private boolean safe(Minecraft mc, Vec3 c) {
            float self = Dmg.explosion(mc, c, mc.player, 5f);
            if (self > maxSelf.get()) return false;
            return !antiSuicide.on() || self < mc.player.getHealth() + mc.player.getAbsorptionAmount() - 1f;
        }

        private boolean isAnchor(BlockPos p) {
            return Build.state(p).is(Blocks.RESPAWN_ANCHOR);
        }

        @Override
        protected void onTick(Minecraft mc) {
            info = null;
            if (mc.player == null || mc.level == null || mc.gameMode == null || mc.screen != null) return;
            if (pauseUse.on() && mc.player.isUsingItem()) return;
            ticks++;
            if (cd > 0) {
                cd--;
                return;
            }
            if (mc.level.dimension().equals(Level.NETHER)) {
                warn("Anchor Aura: anchors do not explode in the Nether");
                return;
            }
            if (!AuraSync.allow("anchor")) return;
            LivingEntity target = AuraSync.lastTarget(mc);
            if (target == null || !Targets.accepts(mc, who, target)
                    || mc.player.distanceToSqr(target) > (double) targetRange.get() * targetRange.get()) {
                target = Targets.nearest(mc, who, targetRange.get());
            }
            AuraSync.target(target);
            if (anchor == null && (target == null || (los.on() && !mc.player.hasLineOfSight(target)))) {
                AuraSync.idle("anchor");
                return;
            }
            if (target != null) info = target.getName().getString();
            if (anchor != null && !isAnchor(anchor)) {
                if (ticks - placedAt < 12) return;
                anchor = null;
                charged = 0;
                AuraSync.release("anchor");
            }
            if (anchor != null && charged > 0) {
                int real = chargeOf(Build.state(anchor));
                if (real >= 0 && ticks - chargedAt > 8) charged = real;
            }

            if (anchor == null) {
                BlockPos tp = target.blockPosition();
                BlockPos best = null;
                float bestScore = -1e9f;
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        for (int dy = 0; dy <= 2; dy++) {
                            BlockPos p = tp.offset(dx, dy, dz);
                            if (p.equals(mc.player.blockPosition()) || p.equals(mc.player.blockPosition().above())) continue;
                            if (!Build.canPlace(p, Build.REACH) || Build.distTo(p) > placeRange.get()) continue;
                            if (crystalReach(p) > com.zlorbie788.zeus.core.Stealth.ENTITY_REACH) continue;
                            Vec3 c = Vec3.atCenterOf(p);
                            float dt = Dmg.explosion(mc, c, target, 5f);
                            if (dt < minDamage.get() || !safe(mc, c)) continue;
                            float score = dt - Dmg.explosion(mc, c, mc.player, 5f);
                            if (score > bestScore) {
                                bestScore = score;
                                best = p;
                            }
                        }
                    }
                }
                if (best == null) {
                    AuraSync.idle("anchor");
                    return;
                }
                if (!ActionCoordinator.claim("anchor")) { AuraSync.progress("anchor"); return; }
                if (rotate.on()) Aim.look(Vec3.atCenterOf(best), Aim.P_PLACE);
                if (Build.place(best, Inv.is(Items.RESPAWN_ANCHOR), swing.on(), Build.REACH)) {
                    anchor = best;
                    placedAt = ticks;
                    charged = 0;
                    AuraSync.hold("anchor");
                    cd = jitterTicks(delay.i());
                } else {
                    AuraSync.idle("anchor");
                    warn("Anchor Aura: no respawn anchor in your hotbar");
                }
                return;
            }

            if (target == null && !safe(mc, Vec3.atCenterOf(anchor))) {
                anchor = null;
                charged = 0;
                AuraSync.release("anchor");
                return;
            }
            BlockHitResult click = new BlockHitResult(Vec3.atCenterOf(anchor).add(0, 0.5, 0), net.minecraft.core.Direction.UP, anchor, false);
            if (Build.distTo(anchor) > Build.REACH) {
                anchor = null;
                return;
            }
            if (rotate.on()) Aim.look(Vec3.atCenterOf(anchor), Aim.P_PLACE);
            if (charged < charges.i()) {
                if (!ActionCoordinator.claim("anchor")) { AuraSync.progress("anchor"); return; }
                if (Build.useOn(click, Inv.is(Items.GLOWSTONE), swing.on())) {
                    charged++;
                    chargedAt = ticks;
                    AuraSync.hold("anchor");
                    cd = Math.max(jitterTicks(delay.i()), 1);
                } else {
                    AuraSync.idle("anchor");
                    warn("Anchor Aura: no glowstone in your hotbar");
                }
                return;
            }
            int slot = Inv.hotbar(s -> !s.is(Items.GLOWSTONE) && !s.is(Items.RESPAWN_ANCHOR));
            if (slot < 0) {
                for (int i = 0; i < 9; i++) {
                    if (!Inv.get(i).is(Items.GLOWSTONE) && !Inv.get(i).is(Items.RESPAWN_ANCHOR)) slot = i;
                }
            }
            if (slot < 0) {
                AuraSync.idle("anchor");
                warn("Anchor Aura: free a hotbar slot that is not glowstone");
                return;
            }
            if (!ActionCoordinator.claim("anchor")) { AuraSync.progress("anchor"); return; }
            if (!com.zlorbie788.zeus.core.Stealth.allowUse(mc)) { AuraSync.progress("anchor"); return; }
            Inv.with(slot, () -> {
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, click);
                if (swing.on()) mc.player.swing(InteractionHand.MAIN_HAND);
            });
            charged = 0;
            anchor = null;
            cd = jitterTicks(delay.i());
            // One anchor is spent: the crystal aura gets the next move.
            AuraSync.finished("anchor");
        }
    }
}
