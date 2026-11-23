package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.StreamerPrivacy;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BossEvent.class)
public abstract class BossBarPrivacyMixin {
    @Inject(method = "getName", at = @At("RETURN"), cancellable = true, require = 0)
    private void zeus$bossName(CallbackInfoReturnable<Component> cir) {
        cir.setReturnValue(StreamerPrivacy.scrub(cir.getReturnValue()));
    }
}
