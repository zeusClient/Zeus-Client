package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Aim;
import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Stealth;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class BedAura extends Module {
    private final Setting.Bool looking = add(new Setting.Bool("Only while looking", "Dig only the bed under your crosshair. Off: any bed within range, which can include your own.", true));
    private final Setting.Num range = add(new Setting.Num("Range", "How far a bed can be dug from.", 4.5, 2.0, 6.0, 0.1, " blocks"));
    private final Setting.Bool sneak = add(new Setting.Bool("Only while sneaking", "Extra guard for your own base: it does nothing unless you are holding sneak.", false));
    private final Setting.Bool rotate = add(new Setting.Bool("Rotate to it", "Looks at the bed it is digging, the same shape every other placement uses.", true));
    private BlockPos current;

    public BedAura() {
        super("bedaura", "Bed Aura", "Breaks the bed you are looking at by itself, start to finish, without holding attack.", Category.WORLD);
        alias("bed breaker", "bedbreaker", "beds");
    }

    @Override
    protected void onTick(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.gameMode == null || mc.screen != null) return;
        if (sneak.on() && !mc.player.isShiftKeyDown()) {
            current = null;
            return;
        }
        double r = range.get();
        BlockPos pos = null;
        net.minecraft.core.Direction face = null;
        if (looking.on()) {
            HitResult hit = mc.hitResult;
            if (hit instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK
                    && mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(bhr.getBlockPos())) <= r * r
                    && isBed(mc.level.getBlockState(bhr.getBlockPos()))) {
                pos = bhr.getBlockPos();
                face = bhr.getDirection();
            }
        } else {
            int span = (int) Math.ceil(r);
            BlockPos eye = mc.player.blockPosition();
            double best = Double.MAX_VALUE;
            for (BlockPos p : BlockPos.betweenClosed(eye.offset(-span, -span, -span), eye.offset(span, span, span))) {
                if (!isBed(mc.level.getBlockState(p))) continue;
                double d = mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(p));
                if (d > r * r || d >= best) continue;
                BlockHitResult see = mc.level.clip(new net.minecraft.world.level.ClipContext(
                        mc.player.getEyePosition(), Vec3.atCenterOf(p),
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
                if (see.getType() != HitResult.Type.BLOCK || !see.getBlockPos().equals(p)) continue;
                best = d;
                pos = p.immutable();
                face = net.minecraft.core.Direction.UP;
            }
        }
        if (pos == null || face == null) {
            current = null;
            return;
        }
        if (!Stealth.allowBreak(mc)) return;
        if (rotate.on()) Aim.look(Vec3.atCenterOf(pos), Aim.P_PLACE);
        if (pos.equals(current)) mc.gameMode.continueDestroyBlock(pos, face);
        else {
            mc.gameMode.startDestroyBlock(pos, face);
            current = pos;
        }
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private static boolean isBed(BlockState state) {
        return state != null && !state.isAir() && state.is(BlockTags.BEDS);
    }

    @Override
    public String info() {
        return looking.on() ? "crosshair bed" : Math.round(range.get() * 10) / 10.0 + " blocks";
    }
}
