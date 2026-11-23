package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.StreamerPrivacy;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(PlayerTabOverlay.class)
public abstract class TabListHeaderMixin {
    @ModifyVariable(method = "setHeader", at = @At("HEAD"), argsOnly = true, require = 0)
    private Component zeus$header(Component header) {
        return StreamerPrivacy.scrub(header);
    }

    @ModifyVariable(method = "setFooter", at = @At("HEAD"), argsOnly = true, require = 0)
    private Component zeus$footer(Component footer) {
        return StreamerPrivacy.scrub(footer);
    }
}
