package com.zlorbie788.zeus.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zlorbie788.zeus.mods.XRay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(VertexConsumer.class)
public abstract class XRayVertexMixin {
    @ModifyArg(method = "putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;[FFFFF[II)V",
            at = @At("HEAD"), index = 6, require = 0)
    private float zeus$xrayAlphaLit(float alpha) {
        return XRay.alphaFor(alpha);
    }

    @ModifyArg(method = "putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;FFFFII)V",
            at = @At("HEAD"), index = 5, require = 0)
    private float zeus$xrayAlphaPlain(float alpha) {
        return XRay.alphaFor(alpha);
    }
}
