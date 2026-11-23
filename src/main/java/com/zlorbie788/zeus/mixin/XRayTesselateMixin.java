package com.zlorbie788.zeus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zlorbie788.zeus.mods.XRay;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ModelBlockRenderer.class)
public abstract class XRayTesselateMixin {
    @Inject(method = "tesselateBlock", at = @At("HEAD"), require = 0)
    private void zeus$xrayPush(BlockAndTintGetter level, List<?> quads, BlockState state, BlockPos pos,
                               PoseStack pose, VertexConsumer buffer, boolean checkSides, int seed, CallbackInfo ci) {
        XRay.pushBlock(state);
    }

    @Inject(method = "tesselateBlock", at = @At("RETURN"), require = 0)
    private void zeus$xrayPop(BlockAndTintGetter level, List<?> quads, BlockState state, BlockPos pos,
                              PoseStack pose, VertexConsumer buffer, boolean checkSides, int seed, CallbackInfo ci) {
        XRay.popBlock();
    }
}
