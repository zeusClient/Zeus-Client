package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.StreamerPrivacy;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.network.chat.MutableComponent;

@Mixin(PlayerTeam.class)
public abstract class TeamPrivacyMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true, require = 0)
    private void zeus$teamDisplay(CallbackInfoReturnable<Component> cir) {
        cir.setReturnValue(StreamerPrivacy.scrub(cir.getReturnValue()));
    }

    @Inject(method = "formatNameForTeam", at = @At("RETURN"), cancellable = true, require = 0)
    private static void zeus$teamName(Team team, Component name, CallbackInfoReturnable<MutableComponent> cir) {
        Component out = StreamerPrivacy.scrub(cir.getReturnValue());
        if (out instanceof MutableComponent mutable) cir.setReturnValue(mutable);
    }
}
