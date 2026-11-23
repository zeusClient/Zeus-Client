package com.zlorbie788.zeus.render;

import com.zlorbie788.zeus.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** A small arrow at the border of the screen for a player who is off screen or behind you. */
public final class EdgeArrow {
    private EdgeArrow() {}

    private static final float[] POINT = new float[2];

    /** Call after {@link Projector#prepare}. Does nothing while the target is in view. */
    public static void draw(GuiGraphics g, Minecraft mc, int w, int h, double x, double y, double z, int color, float alpha) {
        if (mc.player == null || alpha <= 0.01f) return;
        if (Projector.screenPoint(x, y, z, 26f, POINT) != 1) return;
        float cx = w / 2f;
        float cy = h / 2f;
        float vx = POINT[0] - cx;
        float vy = POINT[1] - cy;
        float len = (float) Math.max(1e-3, Math.sqrt(vx * vx + vy * vy));
        vx /= len;
        vy /= len;
        float nx = -vy;
        float ny = vx;
        int c = Draw.rgba(color, Math.min(1f, 0.92f * alpha));
        float tipX = POINT[0] + vx * 7f;
        float tipY = POINT[1] + vy * 7f;
        float backX = POINT[0] - vx * 9f;
        float backY = POINT[1] - vy * 9f;
        Draw.line(g, tipX, tipY, backX + nx * 7f, backY + ny * 7f, 2f, c);
        Draw.line(g, tipX, tipY, backX - nx * 7f, backY - ny * 7f, 2f, c);
        Draw.line(g, backX + nx * 7f, backY + ny * 7f, backX - nx * 7f, backY - ny * 7f, 2f, c);
        String text = Math.round(Math.sqrt(mc.player.distanceToSqr(x, y, z))) + "m";
        int tw = Draw.width(text);
        int lx = Math.max(3, Math.min(w - tw - 3, Math.round(POINT[0] - vx * 27f - tw / 2f)));
        int ly = Math.max(3, Math.min(h - 13, Math.round(POINT[1] - vy * 27f - 5f)));
        Draw.rrect(g, lx - 3, ly - 3, tw + 6, 13, 4, Draw.rgba(0x000000, 0.55f * alpha));
        Draw.text(g, text, lx, ly, c);
    }
}
