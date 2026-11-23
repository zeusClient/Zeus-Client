package com.zlorbie788.zeus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zlorbie788.zeus.mods.XRay;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(BlockRenderDispatcher.class)
public abstract class XRayBatchedMixin {
    @Inject(method = "renderBatched", at = @At("HEAD"), require = 0)
    private void zeus$xrayPush(BlockState state, BlockPos pos, BlockAndTintGetter level, PoseStack pose,
                               VertexConsumer buffer, boolean checkSides, List<?> quads, CallbackInfo ci) {
        XRay.pushBlock(state);
    }

    @Inject(method = "renderBatched", at = @At("RETURN"), require = 0)
    private void zeus$xrayPop(BlockState state, BlockPos pos, BlockAndTintGetter level, PoseStack pose,
                              VertexConsumer buffer, boolean checkSides, List<?> quads, CallbackInfo ci) {
        XRay.popBlock();
    }
}
