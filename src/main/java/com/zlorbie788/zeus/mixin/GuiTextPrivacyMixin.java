package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.StreamerPrivacy;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Gui.class)
public abstract class GuiTextPrivacyMixin {
    @ModifyVariable(method = "setOverlayMessage", at = @At("HEAD"), argsOnly = true, require = 0)
    private Component zeus$actionBar(Component message) {
        return StreamerPrivacy.scrub(message);
    }

    @ModifyVariable(method = "setTitle", at = @At("HEAD"), argsOnly = true, require = 0)
    private Component zeus$title(Component title) {
        return StreamerPrivacy.scrub(title);
    }

    @ModifyVariable(method = "setSubtitle", at = @At("HEAD"), argsOnly = true, require = 0)
    private Component zeus$subtitle(Component subtitle) {
        return StreamerPrivacy.scrub(subtitle);
    }
}
