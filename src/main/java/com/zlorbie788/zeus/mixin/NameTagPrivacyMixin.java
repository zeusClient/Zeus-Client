package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.StreamerPrivacy;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class NameTagPrivacyMixin {
    @Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true, require = 0)
    private void zeus$nameTag(Entity entity, CallbackInfoReturnable<Component> cir) {
        cir.setReturnValue(StreamerPrivacy.scrub(cir.getReturnValue()));
    }
}
