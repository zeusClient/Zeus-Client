package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Build;
import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Inv;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Names;
import com.zlorbie788.zeus.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Clutch {
    private Clutch() {}

    private static boolean ready(Minecraft mc) {
        return mc.player != null && mc.level != null && mc.gameMode != null && mc.screen == null;
    }

    private static boolean falling(Player p) {
        return !p.onGround() && !p.isInWater() && !p.isInLava() && !p.isFallFlying() && !p.getAbilities().flying;
    }

    private static BlockPos ground(Minecraft mc, int max) {
        BlockPos base = mc.player.blockPosition();
        for (int i = 1; i <= max; i++) {
            BlockPos pos = base.below(i);
            if (!mc.level.getBlockState(pos).getFluidState().isEmpty()) return null;
            if (Build.solid(pos)) return pos;
        }
        return null;
    }

    private static boolean has(Minecraft mc, net.minecraft.world.item.Item item) {
        return Inv.hotbar(Inv.is(item)) >= 0 || Inv.offhand(Inv.is(item));
    }

    private static Player nearestPlayer(Minecraft mc, double range) {
        Player best = null;
        double bestD = range * range;
        for (Player other : mc.level.players()) {
            if (other == mc.player || !other.isAlive() || other.isSpectator() || Friends.protects(other)) continue;
            double d = mc.player.distanceToSqr(other);
            if (d < bestD) {
                bestD = d;
                best = other;
            }
        }
        return best;
    }

    private static boolean sword(ItemStack s) {
        return Names.item(s).endsWith("_sword");
    }

    private static boolean axe(ItemStack s) {
        return Names.item(s).endsWith("_axe");
    }

    // ------------------------------------------------------------------ Auto MLG

    public static final class AutoMlg extends Module {
        private final Setting.Choice method = add(new Setting.Choice("What to use",
                "Water is the most reliable. Cobweb works in the Nether and anywhere water cannot go.",
                2, "Water bucket", "Cobweb", "Water, then cobweb"));
        private final Setting.Num fall = add(new Setting.Num("Fall distance",
                "How far you have to fall before it acts. It still waits until the ground is within reach.", 3, 2, 30, 1, " blocks"));
        private final Setting.Bool human = add(new Setting.Bool("Human timing",
                "Waits a human reaction instead of placing on the exact tick the fall starts.", true));

        private long armed;
        private int cooldown;
        private String note = "watching";
        private int saved;

        public AutoMlg() {
            super("automlg", "Auto MLG",
                    "Catches a bad fall with a water bucket or a cobweb, so one missed jump does not cost you the fight.",
                    Category.PLAYER);
            alias("mlg", "clutch", "waterbucket");
        }

        @Override
        protected void onDisable() {
            armed = 0;
            cooldown = 0;
            note = "off";
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (!ready(mc)) {
                armed = 0;
                return;
            }
            if (cooldown > 0) {
                cooldown--;
                return;
            }
            Player p = mc.player;
            if (!falling(p)) {
                armed = 0;
                note = "watching";
                return;
            }
            double distance = p.fallDistance;
            if (distance < fall.get()) {
                armed = 0;
                return;
            }
            BlockPos landing = ground(mc, 4);
            if (landing == null) {
                note = "looking for the ground";
                return;
            }
            if (Build.distTo(landing) > 4.5) {
                note = "waiting for reach";
                return;
            }
            if (armed == 0L) {
                armed = System.currentTimeMillis() + (human.on() ? Phantom.reaction() : 0L);
                note = "falling " + Math.round(distance);
                return;
            }
            boolean urgent = Build.distTo(landing) < 3.2;
            if (System.currentTimeMillis() < armed && !urgent) return;
            boolean water = method.index() != 1;
            boolean cobweb = method.index() != 0;
            BlockPos spot = landing.above();
            if (water && has(mc, Items.WATER_BUCKET)) {
                BlockHitResult hit = Build.against(spot, Build.REACH);
                if (hit == null) hit = Build.top(landing);
                Phantom.aim(p, Vec3.atCenterOf(spot));
                int wslot = Inv.hotbar(Inv.is(Items.WATER_BUCKET));
                boolean woff = wslot < 0;
                Build.useOn(hit, Inv.is(Items.WATER_BUCKET), true);
                boolean emptied = woff ? !p.getOffhandItem().is(Items.WATER_BUCKET)
                        : !Inv.get(wslot).is(Items.WATER_BUCKET);
                boolean wet = !mc.level.getBlockState(spot).getFluidState().isEmpty();
                if (emptied || wet) {
                    cooldown = 30;
                    saved++;
                    note = "water at " + spot.getX() + " " + spot.getY() + " " + spot.getZ();
                    return;
                }
            }
            if (cobweb && has(mc, Items.COBWEB)) {
                Phantom.aim(p, Vec3.atCenterOf(spot));
                if (Build.place(spot, Inv.is(Items.COBWEB), true, 4.4)) {
                    cooldown = 30;
                    saved++;
                    note = "cobweb at " + spot.getX() + " " + spot.getY() + " " + spot.getZ();
                    return;
                }
            }
            note = method.index() == 0 ? "no water bucket" : method.index() == 1 ? "no cobweb" : "no water or cobweb";
        }

        @Override
        public String info() {
            return note + (saved > 0 ? " · saved " + saved : "");
        }
    }

    // ------------------------------------------------------------------ Pearl Catch

    public static final class PearlCatch extends Module {
        private final Setting.Num fall = add(new Setting.Num("Fall distance",
                "How far you have to fall before a pearl is thrown. Earlier is better: a pearl falls at 1.5 blocks a tick, so a late throw trails behind you and lands after you do.", 3, 2, 60, 1, " blocks"));
        private final Setting.Bool withoutWater = add(new Setting.Bool("Only without a water bucket",
                "Leaves the fall to Auto MLG when you still have a bucket to place.", true));
        private final Setting.Num cooldown = add(new Setting.Num("Wait after a throw",
                "Ticks before another pearl may be thrown. Short enough that a pearl which lands on a ledge can be followed by a second one.", 10, 5, 100, 5, " ticks"));

        private int wait;
        private String note = "watching";

        public PearlCatch() {
            super("pearlcatch", "Pearl Catch",
                    "Throws an ender pearl straight down when a fall is going to hurt, teleporting you onto the ground instead.",
                    Category.PLAYER);
            alias("pearlclutch", "catch");
        }

        @Override
        protected void onDisable() {
            note = "off";
            wait = 0;
        }

        private static int pearlSlot() {
            for (int i = 0; i < 9; i++) if (Inv.get(i).isEmpty()) return i;
            for (int i = 0; i < 9; i++) {
                String n = Names.item(Inv.get(i));
                boolean weapon = n.endsWith("_sword") || n.endsWith("_axe") || n.endsWith("_mace")
                        || n.endsWith("_pickaxe") || n.endsWith("_shovel") || n.endsWith("_hoe")
                        || n.equals("bow") || n.equals("crossbow") || n.equals("trident") || n.equals("shield");
                if (!weapon) return i;
            }
            return Inv.selected();
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (!ready(mc)) return;
            if (wait > 0) {
                wait--;
                return;
            }
            Player p = mc.player;
            if (!falling(p)) {
                note = "watching";
                return;
            }
            int slot = Inv.hotbar(Inv.is(Items.ENDER_PEARL));
            boolean offhand = Inv.offhand(Inv.is(Items.ENDER_PEARL));
            if (slot < 0 && !offhand) {
                int pack = Inv.main(Inv.is(Items.ENDER_PEARL));
                if (pack < 0) {
                    note = "no pearl";
                    return;
                }
                Inv.toHotbar(pack, pearlSlot());
                slot = Inv.hotbar(Inv.is(Items.ENDER_PEARL));
                offhand = Inv.offhand(Inv.is(Items.ENDER_PEARL));
                if (slot < 0 && !offhand) {
                    note = "pulling a pearl up";
                    return;
                }
            }
            if (p.fallDistance < fall.get()) {
                note = "watching";
                return;
            }
            if (withoutWater.on() && has(mc, Items.WATER_BUCKET)) {
                note = "leaving it to the bucket";
                return;
            }
            if (ground(mc, 40) == null) {
                note = "nothing to land on";
                return;
            }
            Phantom.aimDown(p);
            if (offhand) {
                mc.gameMode.useItem(p, InteractionHand.OFF_HAND);
                p.swing(InteractionHand.OFF_HAND);
            } else {
                Inv.with(slot, () -> {
                    mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
                    p.swing(InteractionHand.MAIN_HAND);
                });
            }
            wait = cooldown.i();
            note = "pearl away at " + Math.round(p.fallDistance) + " blocks";
        }

        @Override
        public String info() {
            return note;
        }
    }

    // ------------------------------------------------------------------ Auto Wind Charge

    public static final class AutoWindCharge extends Module {
        private final Setting.Choice when = add(new Setting.Choice("When",
                "Catch a fall launches you back up mid-air. Boost a jump fires while you hold jump.",
                0, "Catch a fall", "Boost a jump"));
        private final Setting.Num fall = add(new Setting.Num("Fall distance",
                "Falling this far arms a wind charge.", 3, 1, 30, 1, " blocks")).showIf(() -> when.index() == 0);
        private final Setting.Num cooldown = add(new Setting.Num("Cooldown",
                "Ticks between two wind charges.", 25, 10, 100, 5, " ticks"));

        private int wait;
        private String note = "ready";
        private int used;

        public AutoWindCharge() {
            super("autowindcharge", "Auto Wind Charge",
                    "Fires a wind charge at your feet to catch a fall or boost a jump, using the knockback as movement.",
                    Category.MOVEMENT);
            alias("windcharge", "windjump");
        }

        @Override
        protected void onDisable() {
            wait = 0;
            note = "off";
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (!ready(mc)) return;
            if (wait > 0) {
                wait--;
                return;
            }
            Player p = mc.player;
            if (when.index() == 0) {
                // Only worth a charge on the way down: fired while rising it just cancels the jump.
                if (!falling(p) || p.fallDistance < fall.get() || p.getDeltaMovement().y > -0.05) {
                    note = "ready";
                    return;
                }
            } else {
                if (p.onGround() || !mc.options.keyJump.isDown() || p.getDeltaMovement().y < -0.05) {
                    note = "ready";
                    return;
                }
            }
            // A mace hit in flight is worth more than a boost, so the charge waits its turn.
            if (MaceMods.MaceAura.armed()) {
                note = "letting the mace finish";
                return;
            }
            int slot = Inv.hotbar(Inv.is(Items.WIND_CHARGE));
            boolean offhand = Inv.offhand(Inv.is(Items.WIND_CHARGE));
            if (slot < 0 && !offhand) {
                note = "no wind charge";
                return;
            }
            if (Phantom.miss()) return;
            fire(mc, p, slot, offhand);
            wait = cooldown.i();
            used++;
            note = "launched " + used;
        }

        private void fire(Minecraft mc, Player p, int slot, boolean offhand) {
            float yaw = p.getYRot();
            float pitch = p.getXRot();
            float down = Math.min(90f, 89f + Phantom.wobble() * 0.2f);
            p.setXRot(down);
            com.zlorbie788.zeus.core.Aim.sendRotation(yaw, down);
            if (offhand) {
                mc.gameMode.useItem(p, InteractionHand.OFF_HAND);
            } else {
                Inv.with(slot, () -> mc.gameMode.useItem(p, InteractionHand.MAIN_HAND));
            }
            p.swing(InteractionHand.MAIN_HAND);
            p.setXRot(pitch);
            com.zlorbie788.zeus.core.Aim.sendRotation(yaw, pitch);
        }

        @Override
        public String info() {
            return note;
        }
    }

    // ------------------------------------------------------------------ Right Click Shield

    public static final class RightClickShield extends Module {
        private final Setting.Bool nearby = add(new Setting.Bool("When players are near",
                "Raises the shield while a player is inside the range below.", true));
        private final Setting.Bool arrows = add(new Setting.Bool("When arrows are coming",
                "Raises the shield at arrows or tridents heading at you.", true));
        private final Setting.Num range = add(new Setting.Num("Range",
                "How close a player has to be for the shield to go up.", 6, 3, 24, 1, " blocks")).showIf(() -> nearby.on());

        private int previous = -1;
        private boolean raised;
        private String note = "down";

        public RightClickShield() {
            super("rightclickshield", "Right Click Shield",
                    "Keeps a shield up by itself when someone is close or an arrow is on the way, so you never get caught with it down.",
                    Category.COMBAT);
            alias("rightclip", "autoshield", "shieldup");
        }

        @Override
        protected void onDisable() {
            lower();
            note = "off";
        }

        private void lower() {
            Minecraft mc = Minecraft.getInstance();
            if (raised) {
                mc.options.keyUse.setDown(false);
                raised = false;
            }
            if (previous >= 0 && previous < 9 && mc.player != null) {
                mc.player.getInventory().setSelectedSlot(previous);
                previous = -1;
            }
        }

        private boolean danger(Minecraft mc) {
            Player p = mc.player;
            if (nearby.on()) {
                for (Player other : mc.level.players()) {
                    if (other == p || !other.isAlive() || other.isSpectator()) continue;
                    if (p.distanceToSqr(other) <= range.get() * range.get()) return true;
                }
            }
            if (arrows.on()) {
                for (Entity e : mc.level.getEntities(p, p.getBoundingBox().inflate(10.0))) {
                    if (!(e instanceof Projectile arrow)) continue;
                    Vec3 v = arrow.getDeltaMovement();
                    if (v.lengthSqr() < 0.05) continue;
                    Vec3 to = p.position().add(0, p.getBbHeight() * 0.5, 0).subtract(arrow.position());
                    if (to.lengthSqr() < 1.0e-4) return true;
                    if (v.normalize().dot(to.normalize()) > 0.55) return true;
                }
            }
            return false;
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (!ready(mc)) {
                lower();
                return;
            }
            Player p = mc.player;
            if (p.isUsingItem() && !p.isBlocking()) {
                return;
            }
            if (!danger(mc)) {
                if (raised) {
                    lower();
                    note = "down";
                }
                return;
            }
            if (Inv.offhand(Inv.is(Items.SHIELD))) {
                mc.options.keyUse.setDown(true);
                raised = true;
                note = "up";
                return;
            }
            int slot = Inv.hotbar(Inv.is(Items.SHIELD));
            if (slot < 0) {
                note = "no shield";
                return;
            }
            if (Inv.selected() != slot) {
                if (previous < 0) previous = Inv.selected();
                Inv.select(slot);
            }
            mc.options.keyUse.setDown(true);
            raised = true;
            note = "up";
        }

        @Override
        public String info() {
            return note;
        }
    }

    // ------------------------------------------------------------------ Auto Stun

    public static final class AutoStun extends Module {
        private final Setting.Num reach = add(new Setting.Num("Reach",
                "How close the target has to be to swing the axe.", 3.2, 2.5, 6, 0.1, " blocks"));
        private final Setting.Bool blockingOnly = add(new Setting.Bool("Only while they block",
                "Only swap when the target actually has a shield up.", true));
        private final Setting.Bool swapBack = add(new Setting.Bool("Swap back",
                "Returns to the sword, or the slot you came from, straight after the axe hit.", true));
        private final Setting.Num wait = add(new Setting.Num("Wait between hits",
                "Ticks between two stun attempts.", 4, 2, 20, 1, " ticks"));

        private static volatile AutoStun live;
        private static final Map<UUID, Long> broken = new HashMap<>();

        private int cooldown;
        private int from = -1;
        private String note = "waiting";

        public AutoStun() {
            super("autostun", "Shield Breaker",
                    "Hits a raised shield with an axe to disable it, then puts the sword back in your hand for the follow-up.",
                    Category.COMBAT);
            alias("shield breaker", "shieldbreaker", "auto stun", "autostun", "shield disable");
            alias("stun", "axestun");
            live = this;
            add(new Setting.Action("Check now", "Says in chat what the module sees right now: who, how far, which axe, and the last reason it stopped.", this::check));
        }

        private void check() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) {
                com.zlorbie788.zeus.core.Zeus.toast("Shield Breaker: not in a world", false);
                return;
            }
            int axeSlot = Inv.hotbar(Clutch::axe);
            String axe;
            if (axeSlot >= 0) {
                axe = "slot " + (axeSlot + 1);
            } else {
                int packed = Inv.main(Clutch::axe);
                axe = packed < 0 ? "none" : "in your inventory, pulled up when it is needed";
            }
            Player t = nearestPlayer(mc, reach.get());
            if (t == null) {
                com.zlorbie788.zeus.core.Zeus.toast("Shield Breaker: nobody within " + one(reach.get()) + " blocks, axe " + axe + ", holding " + Inv.selected(), false);
                return;
            }
            double reachCap = Math.min(reach.get(), com.zlorbie788.zeus.core.Stealth.ENTITY_REACH);
            com.zlorbie788.zeus.core.Zeus.toast("Shield Breaker: " + t.getName().getString()
                    + " " + one(com.zlorbie788.zeus.core.Aim.reachTo(t)) + "/" + one(reachCap)
                    + ", blocking " + (t.isBlocking() ? "yes" : "no")
                    + ", axe " + axe
                    + ", swing " + one(mc.player.getAttackStrengthScale(0.5f))
                    + ", last " + note, true);
        }

        private static String one(double v) {
            return String.format(java.util.Locale.ROOT, "%.1f", v);
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (!ready(mc)) return;
            if (cooldown > 0) {
                cooldown--;
                return;
            }
            Player p = mc.player;
            Player target = nearestPlayer(mc, reach.get());
            if (target == null) {
                if (from >= 0 && !Inv.holding(Clutch::sword)) Inv.select(from);
                from = -1;
                note = "waiting";
                return;
            }
            if (blockingOnly.on() && !target.isBlocking()) {
                note = "they are not blocking";
                return;
            }
            if (p.getAttackStrengthScale(0.5f) < 0.45f) {
                note = "waiting for the swing";
                return;
            }
            if (com.zlorbie788.zeus.core.Aim.reachTo(target) > Math.min(reach.get(), com.zlorbie788.zeus.core.Stealth.ENTITY_REACH)) {
                note = "out of reach";
                return;
            }
            int axeSlot = Inv.hotbar(Clutch::axe);
            if (axeSlot < 0) {
                int packed = Inv.main(Clutch::axe);
                if (packed >= 0) {
                    note = "pulling an axe up";
                    if (!com.zlorbie788.zeus.core.Stealth.busyThisTick(mc)) Inv.toHotbar(packed, Inv.room());
                    return;
                }
                note = "no axe";
                return;
            }
            if (!Inv.holding(Clutch::axe)) {
                if (from < 0) from = Inv.selected();
                Inv.select(axeSlot);
            }
            if (!com.zlorbie788.zeus.core.Stealth.allowAttack(mc)) {
                note = "another hit took the tick";
                return;
            }
            com.zlorbie788.zeus.core.Aim.look(target.position().add(0, target.getBbHeight() * 0.5, 0));
            com.zlorbie788.zeus.core.Hits.attempt(target);
            mc.gameMode.attack(p, target);
            p.swing(InteractionHand.MAIN_HAND);
            com.zlorbie788.zeus.core.Stealth.attackBudget(12);
            if (target.isBlocking()) broken.put(target.getUUID(), System.currentTimeMillis() + 5000);
            if (swapBack.on() && !MaceMods.MaceAura.isOn()) {
                int swordSlot = Inv.hotbar(Clutch::sword);
                if (swordSlot >= 0) Inv.select(swordSlot);
                else if (from >= 0) Inv.select(from);
            }
            from = -1;
            cooldown = Math.max(1, wait.i() + java.util.concurrent.ThreadLocalRandom.current().nextInt(3) - 1);
            note = "stunned " + target.getName().getString();
        }

        /**
         * True while this module is the one that should take the target: it has a shield up and an axe
         * is at hand. Mace Aura stands down for it, so the shield goes first and the mace follows.
         */
        public static boolean holdsMace(Player p) {
            AutoStun a = live;
            if (a == null || !a.enabled() || p == null) return false;
            if (!p.isBlocking()) return false;
            Long until = broken.get(p.getUUID());
            if (until != null && until > System.currentTimeMillis()) return false;
            double cap = Math.min(a.reach.get(), com.zlorbie788.zeus.core.Stealth.ENTITY_REACH);
            if (com.zlorbie788.zeus.core.Aim.reachTo(p) > cap) return false;
            return Inv.hotbar(Clutch::axe) >= 0 || Inv.main(Clutch::axe) >= 0;
        }

        @Override
        protected void onDisable() {
            broken.clear();
            from = -1;
            cooldown = 0;
        }

        @Override
        public String info() {
            return note;
        }
    }

    // ------------------------------------------------------------------ Auto Drain

    public static final class AutoDrain extends Module {
        private final Setting.Num radius = add(new Setting.Num("Radius",
                "How far around you it looks for water to scoop up.", 4, 2, 8, 1, " blocks"));
        private final Setting.Num vertical = add(new Setting.Num("Height",
                "How far above and below you it looks.", 2, 1, 4, 1, " blocks"));
        private final Setting.Bool lava = add(new Setting.Bool("Lava too",
                "Also scoop lava. Only do this where you can survive the bucket going wrong.", false));
        private final Setting.Bool holes = add(new Setting.Bool("Only water in holes",
                "Only drains water that is boxed in, which is the water that blocks a crystal or an anchor.", true));
        private final Setting.Num delay = add(new Setting.Num("Between scoops",
                "Ticks between two buckets.", 6, 2, 30, 1, " ticks"));

        private int cooldown;
        private int drained;
        private String note = "nothing to drain";

        public AutoDrain() {
            super("autodrain", "Auto Drain",
                    "Scoops water out of the holes around you so crystals and anchors can be placed, and stops you fighting in a pool.",
                    Category.WORLD);
            alias("drain", "waterdrain");
        }

        @Override
        protected void onDisable() {
            note = "off";
            cooldown = 0;
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (!ready(mc)) return;
            if (cooldown > 0) {
                cooldown--;
                return;
            }
            Player p = mc.player;
            int bucket = Inv.hotbar(Inv.is(Items.BUCKET));
            if (bucket < 0) {
                note = "no empty bucket";
                return;
            }
            BlockPos found = find(mc);
            if (found == null) {
                note = "nothing to drain";
                return;
            }
            Phantom.aim(p, Vec3.atCenterOf(found));
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(found), Direction.UP, found, false);
            Inv.with(bucket, () -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit));
            drained++;
            cooldown = delay.i();
            note = "scooped " + drained;
        }

        private BlockPos find(Minecraft mc) {
            Player p = mc.player;
            BlockPos base = p.blockPosition();
            int r = radius.i();
            int v = vertical.i();
            BlockPos best = null;
            double bestD = Double.MAX_VALUE;
            for (BlockPos pos : BlockPos.betweenClosed(base.offset(-r, -v, -r), base.offset(r, v, r))) {
                if (pos.equals(base)) continue;
                BlockState state = mc.level.getBlockState(pos);
                boolean water = state.is(Blocks.WATER);
                boolean hot = lava.on() && state.is(Blocks.LAVA);
                if (!water && !hot) continue;
                if (!state.getFluidState().isSource()) continue;
                if (holes.on() && !boxed(mc, pos)) continue;
                double d = Build.distTo(pos);
                if (d > 4.3 || d >= bestD) continue;
                bestD = d;
                best = pos.immutable();
            }
            return best;
        }

        private boolean boxed(Minecraft mc, BlockPos pos) {
            if (!Build.solid(pos.below())) return false;
            int walls = 0;
            for (Direction d : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                if (Build.solid(pos.relative(d))) walls++;
            }
            return walls >= 3;
        }

        @Override
        public String info() {
            return note;
        }
    }
}
