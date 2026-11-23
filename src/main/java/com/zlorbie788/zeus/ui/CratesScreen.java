package com.zlorbie788.zeus.ui;

import com.zlorbie788.zeus.core.Crates;
import com.zlorbie788.zeus.core.Crates.Crate;
import com.zlorbie788.zeus.core.Crates.Item;
import com.zlorbie788.zeus.core.Crates.Rarity;
import com.zlorbie788.zeus.mods.KnifeSwing;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public final class CratesScreen extends Screen {
    private static final String[] TABS = {"Crates", "Collection", "History", "Redeem", "Daily"};
    private static final int T_CRATES = 0, T_COLL = 1, T_HIST = 2, T_REDEEM = 3, T_DAILY = 4;

    // palette
    private static final int GOLD = 0xFFF5C04A;
    private static final int GOLD_DARK = 0xFFB07A18;
    private static final int TEXT = 0xFFEEF2FF;
    private static final int SUB = 0xFF98A6C8;
    private static final int FAINT = 0xFF5F6D90;
    private static final int GREEN = 0xFF5EE6A0;
    private static final int RED = 0xFFFF7A7A;
    private static final int PITY = 0xFFD86BF0;
    private static final int PANEL_TOP = 0xFF0C1122;
    private static final int PANEL_BOT = 0xFF070A15;
    private static final int SURFACE = 0xFF0E1426;
    private static final int SURFACE2 = 0xFF141C33;
    private static final int RAISED = 0xFF1A2440;
    private static final int BORDER = 0xFF1C2641;
    private static final int BORDER_HI = 0xFF2E3D66;

    // text sizes, in units
    private static final float F_TITLE = 13.5f, F_H = 10f, F_BODY = 8f, F_SMALL = 7f, F_TINY = 6.2f;

    private final Screen parent;
    private final long born = com.zlorbie788.zeus.core.Clock.nanoTime();
    private int tab;
    private int prevTab;
    private float tabAt = -10f;
    private int sel;
    private float selAt = -10f;
    private float openedAt;
    private float lastT;
    private EditBox codeBox;
    private String codeMsg = "";
    private boolean codeOk;
    private long codeAt;
    private String flash = "";
    private long flashAt;
    private Opening opening;

    // canvas: real pixels per unit, GUI scale, canvas size in units, the panel and the mouse in units
    private float s = 2f;
    private int gs = 2;
    private float W, H;
    private float px, py, pw, ph;
    private float mxu = -1e5f, myu = -1e5f;

    // smoothed UI state
    private float underX = -1, underW;
    private float selBarY = -1;
    private final Map<Integer, Float> hoverAnim = new HashMap<>();
    private float lastCrateCx, lastCrateCy, lastCrateSize = 110;
    private float shownTokens = -1, shownKeys = -1;
    private int lastTokens, lastKeys;
    private float collScroll, collScrollShown;
    private float histScroll, histScrollShown;
    private Item inspect;
    private float inspectAt;
    private float inspectYaw = -0.35f, inspectSpin;
    private boolean dragging;
    private double dragLastX;
    private final List<Object[]> floats = new ArrayList<>();
    private String tip;
    private Item tipItem;

    // hit boxes rebuilt every frame, in units
    private final List<float[]> hits = new ArrayList<>();
    private static final int H_TAB = 1, H_CRATE = 2, H_OPEN = 3, H_BUY = 4, H_REDEEM = 5, H_EQUIP = 6, H_AGAIN = 7, H_DONE = 8,
            H_COLL = 9, H_CLOSE = 10, H_FIELD = 11, H_QUICK = 12, H_INSPECT = 13, H_HIST = 14, H_MULTI = 15, H_AUTO = 16, H_STOP = 17;

    public CratesScreen(Screen parent) {
        super(Component.literal("Zeus Crates"));
        this.parent = parent;
        Crates.load();
        for (int i = 0; i < Crates.CRATES.size(); i++) {
            if (Crates.held(Crates.CRATES.get(i).id) > 0) {
                sel = i;
                break;
            }
        }
    }

    private float now() {
        return (com.zlorbie788.zeus.core.Clock.nanoTime() - born) / 1e9f;
    }

    // ------------------------------------------------------------------ easing
    private static float clamp01(float x) {
        return x < 0 ? 0 : x > 1 ? 1 : x;
    }

    private static float outCubic(float x) {
        x = clamp01(x);
        return 1 - (1 - x) * (1 - x) * (1 - x);
    }

    private static float inOutCubic(float x) {
        x = clamp01(x);
        return x < 0.5f ? 4 * x * x * x : 1 - (float) Math.pow(-2 * x + 2, 3) / 2;
    }

    private static float outBack(float x) {
        x = clamp01(x);
        float c1 = 1.70158f, c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(x - 1, 3) + c1 * (float) Math.pow(x - 1, 2);
    }

    private static float approach(float cur, float target, float rate, float dt) {
        return cur + (target - cur) * (1f - (float) Math.exp(-rate * dt));
    }

    private float hover(int key, boolean on, float dt) {
        float v = hoverAnim.getOrDefault(key, 0f);
        v = approach(v, on ? 1f : 0f, 16f, dt);
        hoverAnim.put(key, v);
        return v;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void onClose() {
        Crates.save(true);
        minecraft.setScreen(parent);
    }

    // ------------------------------------------------------------------ canvas
    private void measure() {
        gs = Math.max(1, Tex.guiScale());
        float rw = width * gs, rh = height * gs;
        float k = Math.min(rw / 960f, rh / 540f);
        if (k < 1.1f) k = Math.min(1.1f, Math.min(rw / 680f, rh / 380f));
        s = Math.max(0.5f, k);
        W = rw / s;
        H = rh / s;
        float m = H < 440 ? 10 : 20;
        pw = Math.min(W - 2 * m, 1000);
        ph = Math.min(H - 2 * m, 560);
        px = (W - pw) / 2f;
        py = (H - ph) / 2f;
    }

    private int P(float u) {
        return Math.round(u * s);
    }

    private void rect(GuiGraphics g, float x, float y, float w, float h, float r, int c) {
        rect(g, x, y, w, h, r, c, c);
    }

    private void rect(GuiGraphics g, float x, float y, float w, float h, float r, int top, int bottom) {
        int x0 = P(x), y0 = P(y);
        Draw.rrect(g, x0, y0, P(x + w) - x0, P(y + h) - y0, P(r), top, bottom);
    }

    private void stroke(GuiGraphics g, float x, float y, float w, float h, float r, int c) {
        int x0 = P(x), y0 = P(y);
        Draw.rrectLine(g, x0, y0, P(x + w) - x0, P(y + h) - y0, P(r), c);
    }

    private void fill(GuiGraphics g, float x, float y, float w, float h, int c) {
        g.fill(P(x), P(y), Math.max(P(x) + 1, P(x + w)), Math.max(P(y) + 1, P(y + h)), c);
    }

    private void glow(GuiGraphics g, float x, float y, float w, float h, float r, int c, float spread, float peak) {
        if (peak <= 0f) return;
        int x0 = P(x), y0 = P(y), sp = Math.max(1, P(spread));
        Draw.glow(g, x0, y0, Math.max(1, P(x + w) - x0), Math.max(1, P(y + h) - y0), P(r), c, sp, Math.min(1f, peak) * 3f / sp);
    }

    private void sheen(GuiGraphics g, float x, float y, float w, float h, int c, float phase) {
        int x0 = P(x), y0 = P(y);
        Draw.sheen(g, x0, y0, P(x + w) - x0, P(y + h) - y0, c, phase);
    }

    private void line(GuiGraphics g, float x1, float y1, float x2, float y2, float thick, int c) {
        Draw.line(g, x1 * s, y1 * s, x2 * s, y2 * s, Math.max(1f, thick * s), c);
    }

    private void scissor(GuiGraphics g, float x, float y, float w, float h) {
        g.enableScissor(P(x), P(y), P(x + w), P(y + h));
    }

    // text, centred on cy
    private void text(GuiGraphics g, String t, float x, float cy, float size, int weight, int c) {
        Ty.text(g, t, P(x), P(cy), size * s, weight, c);
    }

    private void textC(GuiGraphics g, String t, float cx, float cy, float size, int weight, int c) {
        Ty.textC(g, t, P(cx), P(cy), size * s, weight, c);
    }

    private void textR(GuiGraphics g, String t, float xr, float cy, float size, int weight, int c) {
        Ty.textR(g, t, P(xr), P(cy), size * s, weight, c);
    }

    private float tw(String t, float size, int weight) {
        return Ty.w(t, size * s, weight) / s;
    }

    private String fit(String t, float max, float size, int weight) {
        return Ty.fit(t, Math.max(1, Math.round(max * s)), size * s, weight);
    }

    private void knife(GuiGraphics g, int kind, float x, float y, float w, float h, boolean live, float time) {
        if (kind < 0) return;
        int bw = Math.round(w * s / gs), bh = Math.round(h * s / gs);
        if (bw < 3 || bh < 3) return;
        g.pose().pushMatrix();
        g.pose().translate(x * s, y * s);
        g.pose().scale(gs, gs);
        if (live) KnifeArt.showcase(g, kind, 0, 0, 0, bw, bh, time, false);
        else KnifeArt.still(g, kind, 0, 0, bw, bh);
        g.pose().popMatrix();
    }

    private void knifeSpin(GuiGraphics g, int hd, float x, float y, float w, float h, float yaw, float tilt) {
        int bw = Math.round(w * s / gs), bh = Math.round(h * s / gs);
        if (hd < 0 || bw < 3 || bh < 3) return;
        g.pose().pushMatrix();
        g.pose().translate(x * s, y * s);
        g.pose().scale(gs, gs);
        KnifeHDGui.spin(g, hd, 0, 0, bw, bh, yaw, tilt);
        g.pose().popMatrix();
    }

    private void crate(GuiGraphics g, Crate c, float cx, float cy, float size, float t, float lid, float shake, float glowBoost) {
        int sz = Math.max(8, Math.round(size * s / gs));
        g.pose().pushMatrix();
        g.pose().translate(cx * s, cy * s);
        g.pose().scale(gs, gs);
        drawCrate(g, c, 0f, 0f, sz, t, lid, shake * s / gs, glowBoost);
        g.pose().popMatrix();
    }

    @Override
    protected void init() {
        measure();
        String keep = codeBox == null ? "" : codeBox.getValue();
        codeBox = new EditBox(font, 0, 0, 200, 14, Component.literal("Promo code"));
        codeBox.setMaxLength(24);
        codeBox.setBordered(false);
        codeBox.setTextColor(0xFFFFFFFF);
        codeBox.setHint(Component.literal("ZEUS-XXXX-XXXX"));
        codeBox.setValue(keep);
        // the box only takes the typing; its text, hint and caret are drawn by redeemTab in the panel's font
        addWidget(codeBox);
        hideCodeBox();
        preload();
    }

    private void preload() {
        for (Crate c : Crates.CRATES) {
            for (String part : new String[] {"body", "lid"}) {
                for (int w : new int[] {512, 256, 128}) Tex.prefetch("textures/crates/" + c.id + "_" + part + "_" + w + ".png");
            }
        }
        List<Crate> order = new ArrayList<>(Crates.CRATES);
        order.remove(Crates.CRATES.get(sel));
        order.add(0, Crates.CRATES.get(sel));
        for (Crate c : order) for (Item it : c.items) KnifeHDGui.prefetch(Crates.hdOf(it.id()), false);
        KnifeHDGui.prefetch(Crates.hdOf(Crates.STARTER), false);
    }

    private void placeCodeBox(float x, float cy, float w) {
        if (codeBox == null) return;
        codeBox.setX(Math.round(x * s / gs));
        codeBox.setY(Math.round(cy * s / gs) - 4);
        codeBox.setWidth(Math.max(20, Math.round(w * s / gs)));
        codeBox.visible = tab == T_REDEEM && opening == null && inspect == null;
        codeBox.active = codeBox.visible;
    }

    private void hideCodeBox() {
        if (codeBox != null) {
            codeBox.visible = false;
            codeBox.active = false;
        }
    }

    // ------------------------------------------------------------------ sound
    private static void snd(Object e, float pitch, float vol) {
        try {
            var sm = net.minecraft.client.Minecraft.getInstance().getSoundManager();
            if (e instanceof Holder<?> h) {
                @SuppressWarnings("unchecked") Holder<SoundEvent> hs = (Holder<SoundEvent>) h;
                sm.play(SimpleSoundInstance.forUI(hs.value(), pitch, vol));
            } else if (e instanceof SoundEvent se) {
                sm.play(SimpleSoundInstance.forUI(se, pitch, vol));
            }
        } catch (RuntimeException ignored) {
        }
    }

    // ------------------------------------------------------------------ small parts
    private void hit(int kind, int idx, float x, float y, float w, float h) {
        hits.add(new float[] {kind, idx, x, y, w, h});
    }

    private static boolean in(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private boolean hot(float x, float y, float w, float h) {
        return in(mxu, myu, x, y, w, h);
    }

    private void button(GuiGraphics g, int kind, float x, float y, float w, float h, String label, String right, int colour,
                        boolean enabled, boolean primary, float t, float dt) {
        boolean on = enabled && hot(x, y, w, h);
        float hv = hover(1000 + kind, on, dt);
        float r = Math.min(h / 2f, 8f);
        float fs = h >= 28 ? 9f : F_BODY;
        float lw = tw(label, fs, Ty.BOLD), rw2 = right == null ? 0 : tw(right, F_SMALL, Ty.SEMI) + 8;
        float lx = x + w / 2f - (lw + rw2) / 2f;
        if (primary && enabled) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(t * 3.2f);
            glow(g, x, y, w, h, r, colour, 9, 0.10f + 0.05f * pulse + 0.14f * hv);
            rect(g, x, y, w, h, r, Draw.lighten(colour, 0.10f + 0.14f * hv), Draw.darken(colour, 0.22f));
            fill(g, x + r, y + 0.6f, w - 2 * r, 0.6f, Draw.rgba(0xFFFFFFFF, 0.35f));
            sheen(g, x, y, w, h, 0x50FFFFFF, (t * 0.45f) % 1.6f);
            int ink = 0xFF160F04;
            text(g, label, lx, y + h / 2f, fs, Ty.BOLD, ink);
            if (right != null) text(g, right, lx + lw + 8, y + h / 2f, F_SMALL, Ty.SEMI, Draw.rgba(ink, 0.62f));
        } else {
            rect(g, x, y, w, h, r, !enabled ? 0xFF0D1220 : Draw.mix(SURFACE2, RAISED, hv));
            stroke(g, x, y, w, h, r, !enabled ? 0xFF161E33 : Draw.mix(Draw.mix(BORDER_HI, colour, 0.25f), colour, hv));
            text(g, label, lx, y + h / 2f, fs, Ty.SEMI, enabled ? TEXT : FAINT);
            if (right != null) text(g, right, lx + lw + 8, y + h / 2f, F_SMALL, Ty.MED, enabled ? Draw.mix(SUB, colour, 0.5f) : FAINT);
        }
        if (enabled) hit(kind, 0, x, y, w, h);
    }

    private float chip(GuiGraphics g, float x, float cy, String label, int colour, boolean filled) {
        float w = tw(label, F_TINY, Ty.BOLD) + 10, h = 12;
        if (filled) rect(g, x, cy - h / 2f, w, h, h / 2f, colour);
        else {
            rect(g, x, cy - h / 2f, w, h, h / 2f, Draw.rgba(colour, 0.14f));
            stroke(g, x, cy - h / 2f, w, h, h / 2f, Draw.rgba(colour, 0.45f));
        }
        textC(g, label, x + w / 2f, cy, F_TINY, Ty.BOLD, filled ? 0xFF0A0A12 : colour);
        return w;
    }

    private void bar(GuiGraphics g, float x, float y, float w, float h, float f, int colour) {
        rect(g, x, y, w, h, h / 2f, 0xFF161F38);
        if (f > 0f) rect(g, x, y, Math.max(h, w * Math.min(1f, f)), h, h / 2f, Draw.lighten(colour, 0.12f), colour);
    }

    private void dot(GuiGraphics g, float cx, float cy, float r, int colour) {
        rect(g, cx - r, cy - r, 2 * r, 2 * r, r, colour);
    }

    private void tick(GuiGraphics g, float cx, float cy, float size, int colour) {
        float th = Math.max(1.1f, size * 0.2f);
        line(g, cx - size * 0.45f, cy, cx - size * 0.12f, cy + size * 0.32f, th, colour);
        line(g, cx - size * 0.12f, cy + size * 0.32f, cx + size * 0.48f, cy - size * 0.36f, th, colour);
    }

    private void cross(GuiGraphics g, float cx, float cy, float size, int colour) {
        float th = Math.max(1.1f, size * 0.17f);
        line(g, cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f, th, colour);
        line(g, cx - size / 2f, cy + size / 2f, cx + size / 2f, cy - size / 2f, th, colour);
    }

    private void lock(GuiGraphics g, float cx, float cy, float size, int colour) {
        float bw = size, bh = size * 0.72f;
        float sw = size * 0.6f, sh = size * 0.9f;
        stroke(g, cx - sw / 2f, cy - size * 0.78f, sw, sh, sw / 2f, colour);
        stroke(g, cx - sw / 2f + 1f / s, cy - size * 0.78f + 1f / s, sw - 2f / s, sh, sw / 2f, colour);
        rect(g, cx - bw / 2f, cy - bh * 0.2f, bw, bh, size * 0.16f, colour);
        dot(g, cx, cy + bh * 0.22f, size * 0.09f, 0xFF0A0E1A);
    }

    private void coin(GuiGraphics g, float cx, float cy, float r) {
        rect(g, cx - r, cy - r, 2 * r, 2 * r, r, Draw.lighten(GOLD, 0.15f), GOLD_DARK);
        textC(g, "Z", cx, cy, r * 1.15f, Ty.XBOLD, 0xFF3A2A08);
    }

    private void keyIcon(GuiGraphics g, float x, float cy, float size, int c) {
        float r = size * 0.3f;
        stroke(g, x, cy - r, 2 * r, 2 * r, r, c);
        stroke(g, x + 1f / s, cy - r + 1f / s, 2 * r - 2f / s, 2 * r - 2f / s, r, c);
        fill(g, x + 2 * r - 0.5f, cy - 0.7f, size * 0.6f, 1.4f, c);
        fill(g, x + 2 * r + size * 0.38f, cy, 1.4f, size * 0.24f, c);
        fill(g, x + 2 * r + size * 0.52f, cy, 1.4f, size * 0.18f, c);
    }

    // ------------------------------------------------------------------ crate art
    private void drawCrate(GuiGraphics g, Crate c, float cx, float cy, int size, float t, float lid, float shake, float glowBoost) {
        if (drawCase(g, c, cx, cy, size, t, lid, shake, glowBoost)) return;
        int w = size, h = Math.round(size * 0.72f);
        float sx = shake * (float) Math.sin(t * 61) + shake * 0.5f * (float) Math.sin(t * 97);
        float sy = shake * 0.6f * (float) Math.cos(t * 53);
        float rot = shake * 0.006f * (float) Math.sin(t * 43);
        g.pose().pushMatrix();
        g.pose().translate(cx + sx, cy + sy);
        g.pose().rotate(rot);
        int x = -w / 2, y = -h / 2;
        int r = Math.max(2, size / 14);
        Draw.shadow(g, x, y + h / 6, w, h * 5 / 6, r, Math.max(3, size / 10), 0.55f);
        float breath = 0.5f + 0.5f * (float) Math.sin(t * 2.0f);
        Draw.glow(g, x, y, w, h, r, c.colour, Math.max(4, size / 6), 0.05f + 0.05f * breath + glowBoost);
        int bodyY = y + h / 5;
        Draw.rrect(g, x, bodyY, w, h - h / 5, r, Draw.mix(c.dark, 0xFF1A2030, 0.35f), Draw.darken(c.dark, 0.45f));
        Draw.rrectLine(g, x, bodyY, w, h - h / 5, r, Draw.darken(c.colour, 0.1f));
        int sw = Math.max(3, w / 9);
        for (int s = 0; s < 2; s++) {
            int stx = s == 0 ? x + w / 5 - sw / 2 : x + w * 4 / 5 - sw / 2;
            g.fill(stx, bodyY + 1, stx + sw, y + h - 1, Draw.darken(c.colour, 0.55f));
            g.fill(stx, bodyY + 1, stx + 1, y + h - 1, Draw.rgba(c.colour, 0.6f));
            g.fill(stx + sw - 1, bodyY + 1, stx + sw, y + h - 1, Draw.rgba(c.colour, 0.6f));
        }
        int er = Math.max(4, size / 7);
        int ecy = bodyY + (h - h / 5) / 2 + 2;
        float pulse = Math.min(1f, breath + glowBoost * 3f);
        Draw.glow(g, -er, ecy - er, er * 2, er * 2, er, c.colour, Math.max(3, er), 0.10f + 0.12f * pulse);
        Draw.rrect(g, -er, ecy - er, er * 2, er * 2, er, Draw.darken(c.colour, 0.55f));
        Draw.rrectLine(g, -er, ecy - er, er * 2, er * 2, er, Draw.lighten(c.colour, 0.2f * pulse));
        float sc = Math.max(0.6f, er / 6f);
        Draw.boldCenterS(g, "Z", 0, ecy - Math.round(4 * sc), Draw.lighten(c.colour, 0.3f + 0.3f * Math.min(1f, glowBoost)), sc);
        // light leaking from the seam as the lid is about to go
        int lh = Math.max(4, h / 4);
        if (glowBoost > 0.02f || lid > 0f) {
            float k = Math.min(1f, glowBoost * 2f + lid * 3f);
            Draw.glow(g, x + 2, bodyY - 1, w - 4, 3, 1, Draw.lighten(c.colour, 0.5f), Math.max(4, size / 5), 0.30f * k);
            g.fill(x + 3, bodyY - 1, x + w - 3, bodyY + 1, Draw.rgba(0xFFFFF6D8, k));
        }
        float la = 1f - clamp01((lid - 0.55f) / 0.45f);
        if (la > 0f) {
            g.pose().pushMatrix();
            float lift = lid * size * 0.9f;
            g.pose().translate(lid * size * 0.25f, y - lift);
            g.pose().rotate(-lid * 0.9f);
            int lw = w + 2 * Math.max(2, size / 30);
            Draw.rrect(g, -lw / 2, 0, lw, lh, r, Draw.rgba(Draw.mix(c.dark, 0xFF2A3044, 0.5f), la), Draw.rgba(Draw.darken(c.dark, 0.2f), la));
            Draw.rrectLine(g, -lw / 2, 0, lw, lh, r, Draw.rgba(c.colour, la));
            g.fill(-Math.max(3, w / 12), lh - 2, Math.max(3, w / 12), lh + Math.max(3, h / 9), Draw.rgba(c.colour, la));
            g.pose().popMatrix();
        }
        g.pose().popMatrix();
    }

    static float previewLid;

    private static final float CASE_SEAM = 0.2695f;

    private static Tex.Img caseImg(Crate c, String part, int size) {
        float pxs = size * (float) Math.max(1, minecraftScale());
        int w = pxs > 300 ? 512 : pxs > 150 ? 256 : 128;
        String base = "textures/crates/" + c.id + "_" + part + "_";
        Tex.Img img = Tex.spriteIfReady(base + w + ".png");
        if (img != null) return img;
        // still loading in the background: another size that is ready will do for a frame
        if (Tex.pending(base + w + ".png")) {
            for (int o : new int[] {512, 256, 128}) {
                Tex.Img other = Tex.spriteIfReady(base + o + ".png");
                if (other != null) return other;
            }
        }
        img = Tex.sprite(base + w + ".png");
        return img != null ? img : Tex.sprite(base + "256.png");
    }

    private static double minecraftScale() {
        return net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
    }

    private boolean drawCase(GuiGraphics g, Crate c, float cx, float cy, int size, float t, float lid, float shake, float glowBoost) {
        Tex.Img body = caseImg(c, "body", size), top = caseImg(c, "lid", size);
        if (body == null || top == null) return false;
        int w = size, h = Math.round(size * 0.75f);
        int lh = Math.round(h * CASE_SEAM);
        float sx = shake * (float) Math.sin(t * 61) + shake * 0.5f * (float) Math.sin(t * 97);
        float sy = shake * 0.6f * (float) Math.cos(t * 53);
        float rot = shake * 0.006f * (float) Math.sin(t * 43);
        g.pose().pushMatrix();
        g.pose().translate(cx + sx, cy + sy);
        g.pose().rotate(rot);
        int x = -w / 2, y = -h / 2;
        int r = Math.max(2, size / 14);
        Draw.shadow(g, x, y + h / 6, w, h * 5 / 6, r, Math.max(3, size / 10), 0.55f);
        float breath = 0.5f + 0.5f * (float) Math.sin(t * 2.0f);
        int halo = Math.max(4, size / 6);
        // the glow's peak grows with its spread: cap it, so a big case about to open does not turn into a block of light
        Draw.glow(g, x, y, w, h, r, c.colour, halo, Math.min(0.04f + 0.04f * breath + glowBoost, 0.75f * 3f / halo));
        body.draw(g, x, y + lh, w, h - lh, 0xFFFFFFFF);
        // the medallion breathes, and flares as the case is about to open
        int er = Math.max(3, Math.round(size * 0.14f));
        int ecy = y + Math.round(h * 0.52f);
        float pulse = Math.min(1f, breath + glowBoost * 3f);
        int spread = Math.max(3, Math.round(er * 1.5f));
        Draw.glow(g, -1, ecy - 1, 2, 2, 1, c.colour, spread, (0.10f + 0.22f * pulse) * 3f / spread);
        // light leaking from the seam as the lid is about to go
        if (glowBoost > 0.02f || lid > 0f) {
            float k = Math.min(1f, glowBoost * 2f + lid * 3f);
            int leak = Math.max(4, size / 5);
            Draw.glow(g, x + 2, y + lh - 1, w - 4, 3, 1, Draw.lighten(c.colour, 0.5f), leak, 0.45f * k * 3f / leak);
            g.fill(x + 3, y + lh - 1, x + w - 3, y + lh + 1, Draw.rgba(0xFFFFF6D8, k));
        }
        if (lid <= 0f) {
            top.draw(g, -w / 2, y, w, lh, 0xFFFFFFFF);
        } else {
            // the latches let go: a click of light at each one
            float pop = 1f - clamp01(lid / 0.35f);
            if (pop > 0f) {
                int ls = Math.max(4, size / 9);
                for (int sgn = -1; sgn <= 1; sgn += 2) {
                    int lx = Math.round(sgn * w * 0.3145f);
                    Draw.glow(g, lx - 1, y + lh - 1, 2, 2, 1, Draw.lighten(c.colour, 0.6f), ls, 0.5f * pop * 3f / ls);
                }
            }
            // light pours out of the open case
            float th = Math.min(1f, lid / 0.8f) * 2.3f;           // swings up and back, well past upright
            float cs = (float) Math.cos(th), sn = (float) Math.sin(th);
            float open = Math.min(1f, sn * 1.4f);
            int beamC = Draw.lighten(c.colour, 0.45f);
            int seamSpread = Math.max(6, size / 4);
            Draw.glow(g, x + w / 6, y + lh - 1, w * 2 / 3, 2, 1, beamC, seamSpread, 0.55f * open * 3f / seamSpread);
            for (int i = 0; i < 7; i++) {
                float bx = x + w * (0.2f + 0.6f * i / 6f);
                float fan = (bx - x - w / 2f) * 0.35f;
                Draw.line(g, bx, y + lh, bx + fan, y + lh - size * (0.55f + 0.25f * ((i * 37) % 5) / 5f),
                        Math.max(1f, size / 40f), Draw.rgba(beamC, 0.22f * open * (i % 2 == 0 ? 1f : 0.6f)));
            }
            // the lid on its hinge at the top edge: the front face folds back, then its darker inside shows
            g.pose().pushMatrix();
            g.pose().translate(0, y - lh * 0.30f * sn);
            float fold = Math.max(0.03f, Math.abs(cs));
            if (cs >= 0f) {
                g.pose().scale(1f, fold);
                top.draw(g, -w / 2, 0, w, lh, 0xFFFFFFFF);
            } else {
                // upside down through the texture, not a mirrored pose: the GUI culls mirrored quads
                g.pose().translate(0f, -lh * fold);
                g.pose().scale(1f, fold);
                top.drawPart(g, -w / 2, 0, w, lh, 0, top.h, top.w, -top.h, 0xFF4A5266);
            }
            g.pose().popMatrix();
        }
        g.pose().popMatrix();
        return true;
    }

    private static String shortName(String full) {
        int dot = full.lastIndexOf('·');
        return dot >= 0 ? full.substring(dot + 1).trim() : full;
    }

    private static String typeName(String full) {
        int dot = full.indexOf('·');
        return dot >= 0 ? full.substring(0, dot).trim() : full;
    }

    private static final Map<String, String> NAMES = new HashMap<>();

    private static String nameOf(String id) {
        String n = NAMES.get(id);
        if (n != null) return n;
        int hd = Crates.hdOf(id);
        KnifeHD.Model m = hd >= 0 ? KnifeHD.model(hd) : null;
        n = m == null ? id : m.name;
        NAMES.put(id, n);
        return n;
    }

    private static int kindOf(String id) {
        int hd = Crates.hdOf(id);
        return hd < 0 ? -1 : KnifeArt.allKnives().length - KnifeArt.HD_COUNT + hd;
    }

    private static final class Card {
        Item it;
        float x, y, w, h;
        boolean owned, locked, big;
        float live;
        float sc = 1f;
        float veil;
        int veilColour;

        Card set(Item it, float x, float y, float w, float h, boolean owned, boolean locked, float live, boolean big) {
            this.it = it;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.owned = owned;
            this.locked = locked;
            this.live = live;
            this.big = big;
            this.veil = 0f;
            this.sc = 1f;
            return this;
        }

        float r() {
            return big ? 10 : 7;
        }

        float top() {
            return big ? 22 : 3;
        }

        float kh() {
            return h - (h >= 46 ? (big ? 30 : 15) : 3) - top() - 2;
        }
    }

    private final List<Card> cards = new ArrayList<>();
    private int cardsUsed;

    private void cardsBegin() {
        cardsUsed = 0;
    }

    private Card cardAdd() {
        if (cardsUsed == cards.size()) cards.add(new Card());
        return cards.get(cardsUsed++);
    }

    private void cardsDraw(GuiGraphics g, float t) {
        int n = cardsUsed;
        for (int i = 0; i < n; i++) {
            Card c = cards.get(i);
            int rc = c.it.rarity().tint();
            rect(g, c.x, c.y, c.w, c.h, c.r(), Draw.mix(0xFF0C1122, rc, 0.05f + 0.05f * c.live), Draw.mix(0xFF0B1020, rc, 0.20f + 0.10f * c.live));
        }
        for (int i = 0; i < n; i++) {
            Card c = cards.get(i);
            int rc = c.it.rarity().tint();
            stroke(g, c.x, c.y, c.w, c.h, c.r(), Draw.mix(Draw.mix(BORDER, rc, 0.40f), Draw.lighten(rc, 0.25f), c.live));
        }
        // a soft light in the rarity's colour behind each knife, so dark blades stand off the dark tile
        for (int i = 0; i < n; i++) {
            Card c = cards.get(i);
            float kh = c.kh(), sp = Math.max(6, Math.min(c.w, kh) * 0.42f);
            glow(g, c.x + c.w / 2f - 1, c.y + c.top() + kh / 2f - 1, 2, 2, 1, c.it.rarity().tint(), sp, 0.20f + 0.14f * c.live);
        }
        for (int i = 0; i < n; i++) {
            Card c = cards.get(i);
            if (c.sc == 1f) {
                knife(g, kindOf(c.it.id()), c.x + 3, c.y + c.top(), c.w - 6, c.kh(), c.live > 0.5f, t);
                continue;
            }
            // drawn at the unscaled size and scaled round the card's centre
            float bw = c.w / c.sc, bh = c.h / c.sc, cx = c.x + c.w / 2f, cy = c.y + c.h / 2f;
            float kh = bh - (bh >= 46 ? 15 : 3) - c.top() - 2;
            g.pose().pushMatrix();
            g.pose().translate(cx * s, cy * s);
            g.pose().scale(c.sc, c.sc);
            g.pose().translate(-cx * s, -cy * s);
            knife(g, kindOf(c.it.id()), cx - bw / 2f + 3, cy - bh / 2f + c.top(), bw - 6, kh, c.live > 0.5f, t);
            g.pose().popMatrix();
        }
        for (int i = 0; i < n; i++) {
            Card c = cards.get(i);
            if (c.it.rarity() == Rarity.CHROMA && !c.locked) sheen(g, c.x + 1, c.y + 1, c.w - 2, c.h - 2, 0x40FFFFFF, (now() * 0.45f) % 1.8f);
        }
        // the rarity line along the bottom edge
        for (int i = 0; i < n; i++) {
            Card c = cards.get(i);
            fill(g, c.x + c.r(), c.y + c.h - 2.4f, c.w - 2 * c.r(), 1.3f, Draw.rgba(c.it.rarity().tint(), c.locked ? 0.45f : 0.95f));
        }
        for (int i = 0; i < n; i++) {
            Card c = cards.get(i);
            if (c.locked) rect(g, c.x, c.y, c.w, c.h, c.r(), 0x9C060912);
        }
        for (int i = 0; i < n; i++) {
            Card c = cards.get(i);
            if (c.locked) lock(g, c.x + c.w / 2f, c.y + c.top() + c.kh() / 2f + 2, c.big ? 12 : 8, 0x90A9B6D6);
        }
        for (int i = 0; i < n; i++) {
            Card c = cards.get(i);
            if (c.h < 46) continue;
            int rc = c.it.rarity().tint();
            String full = nameOf(c.it.id());
            int nc = c.locked ? 0xFF55617F : TEXT;
            if (c.big) {
                textC(g, fit(typeName(full), c.w - 16, F_SMALL, Ty.MED), c.x + c.w / 2f, c.y + c.h - 24, F_SMALL, Ty.MED, c.locked ? FAINT : Draw.mix(SUB, rc, 0.35f));
                textC(g, fit(shortName(full), c.w - 16, F_H + 1, Ty.BOLD), c.x + c.w / 2f, c.y + c.h - 12, F_H + 1, Ty.BOLD, nc);
            } else {
                textC(g, fit(shortName(full), c.w - 10, F_SMALL, Ty.SEMI), c.x + c.w / 2f, c.y + c.h - 9, F_SMALL, Ty.SEMI, nc);
            }
        }
        for (int i = 0; i < n; i++) {
            Card c = cards.get(i);
            if (c.big) chip(g, c.x + 9, c.y + 13, c.it.rarity().label.toUpperCase(Locale.ROOT) + "  " + c.it.rarity().odds(), c.it.rarity().tint(), false);
        }
        for (int i = 0; i < n; i++) {
            Card c = cards.get(i);
            if (c.owned) dot(g, c.x + c.w - (c.big ? 7 : 5.5f) - 5, c.y + (c.big ? 7 : 5.5f) + 5, c.big ? 7 : 5.5f, 0xFF1F9A58);
        }
        for (int i = 0; i < n; i++) {
            Card c = cards.get(i);
            float cr = c.big ? 7 : 5.5f;
            if (c.owned) tick(g, c.x + c.w - cr - 5, c.y + cr + 5, cr * 1.1f, 0xFFFFFFFF);
        }
        for (int i = 0; i < n; i++) {
            Card c = cards.get(i);
            if (c.veil > 0.01f) rect(g, c.x, c.y, c.w, c.h, c.r(), Draw.rgba(c.veilColour, c.veil));
        }
        cardsUsed = 0;
    }

    private void card(GuiGraphics g, Item it, float x, float y, float w, float h, boolean owned, boolean locked, float live, float t,
                      boolean big) {
        cardsBegin();
        cardAdd().set(it, x, y, w, h, owned, locked, live, big);
        cardsDraw(g, t);
    }

    // ------------------------------------------------------------------ render
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        measure();
        float t = now();
        float dt = Math.max(0f, Math.min(0.1f, t - lastT));
        lastT = t;
        mxu = mouseX * gs / s;
        myu = mouseY * gs / s;
        tip = null;
        tipItem = null;
        hits.clear();
        Ty.begin();
        boolean covered = opening != null && opening.covers();
        g.pose().pushMatrix();
        g.pose().scale(1f / gs, 1f / gs);
        try {
            if (covered) {
                // the opening has faded in and covers everything: the panel under it is not drawn at all
                hideCodeBox();
                opening.render(g, t, dt);
            } else {
                drawPanel(g, t, dt);
            }
        } finally {
            g.pose().popMatrix();
        }
        if (!covered) {
            boolean over = opening != null || inspect != null;
            if (over) overlayLayer(g);
            else if (tipItem != null || tip != null) g.nextStratum();
            g.pose().pushMatrix();
            g.pose().scale(1f / gs, 1f / gs);
            try {
                if (opening != null) opening.render(g, t, dt);
                else if (inspect != null) renderInspect(g, t, dt);
                else if (tipItem != null) tooltip(g, tipItem);
                else if (tip != null) textTip(g, tip);
            } finally {
                g.pose().popMatrix();
            }
        }
        // a knife under the mouse may be clicked open next: read its large sheet ahead
        if (tipItem != null) KnifeHDGui.prefetch(Crates.hdOf(tipItem.id()), true);
        g.pose().pushMatrix();
        g.pose().scale(1f / gs, 1f / gs);
        floaters(g, t);
        toast(g);
        g.pose().popMatrix();
        Ty.flush();
    }

    private void drawPanel(GuiGraphics g, float t, float dt) {
        float intro = outCubic((t - openedAt) / 0.32f);
        int sc = Crates.CRATES.get(sel).colour;
        // the scene: a dark veil over the game, a little of the chosen crate's colour in the air
        fill(g, 0, 0, W, H, Draw.rgba(0xD8040710, intro));
        glow(g, W * 0.30f, H * 0.35f, W * 0.40f, H * 0.30f, 40, sc, Math.min(W, H) * 0.35f, 0.05f * intro);

        // the panel scales up a touch as it appears
        g.pose().pushMatrix();
        float ps = 0.95f + 0.05f * outBack((t - openedAt) / 0.38f);
        g.pose().translate(W * s / 2f, H * s / 2f);
        g.pose().scale(ps, ps);
        g.pose().translate(-W * s / 2f, -H * s / 2f);
        Draw.shadow(g, P(px), P(py), P(pw), P(ph), P(14), P(16), 0.7f);
        rect(g, px, py, pw, ph, 14, PANEL_TOP, PANEL_BOT);
        stroke(g, px, py, pw, ph, 14, BORDER_HI);
        // a faint light along the top edge
        fill(g, px + 24, py + 0.5f, pw - 48, 1f / s, 0x30FFFFFF);
        float headH = header(g, t, dt);
        float top = py + headH + 14, bottom = py + ph - 40 - 12;

        // the tab content slides in from the side it was picked from
        float tp = outCubic((t - tabAt) / 0.30f);
        float dir = tab >= prevTab ? 1f : -1f;
        g.pose().pushMatrix();
        g.pose().translate((1f - tp) * 16f * dir * s, 0f);
        float x0 = px + 20, w0 = pw - 40;
        if (tab == T_CRATES) cratesTab(g, x0, top, w0, bottom - top, t, dt);
        else if (tab == T_COLL) collectionTab(g, x0, top, w0, bottom - top, t, dt);
        else if (tab == T_HIST) historyTab(g, x0, top, w0, bottom - top, t, dt);
        else if (tab == T_DAILY) dailyTab(g, x0, top, w0, bottom - top, t);
        else redeemTab(g, x0, top, w0, bottom - top, t, dt);
        g.pose().popMatrix();
        if (tp < 1f) fill(g, px + 2, top - 4, pw - 4, bottom - top + 8, Draw.rgba(Draw.mix(PANEL_TOP, PANEL_BOT, 0.5f), (1f - tp) * 0.85f));
        if (tab != T_REDEEM) hideCodeBox();
        footer(g, t);
        g.pose().popMatrix();
    }

    private static void overlayLayer(GuiGraphics g) {
        g.nextStratum();
        try {
            g.blurBeforeThisStratum();
        } catch (IllegalStateException e) {
            // the game allows one blur a frame; the dark fill still covers
        }
    }

    // ------------------------------------------------------------------ header and footer
    private float header(GuiGraphics g, float t, float dt) {
        float hx = px + 20, rowH = 52;
        float cy = py + rowH / 2f + 2;
        // logo: a gold medallion with the Z
        float lr = 12;
        glow(g, hx, cy - lr, 2 * lr, 2 * lr, lr, GOLD, 8, 0.12f);
        rect(g, hx, cy - lr, 2 * lr, 2 * lr, lr, Draw.lighten(GOLD, 0.18f), GOLD_DARK);
        textC(g, "Z", hx + lr, cy + 0.5f, 12, Ty.XBOLD, 0xFF3A2A08);
        float tx = hx + 2 * lr + 10;
        String subtitle = "Unbox knives for your hand";
        text(g, "Zeus Crates", tx, cy - 5.5f, F_TITLE, Ty.XBOLD, TEXT);
        text(g, subtitle, tx, cy + 8, F_SMALL, Ty.MED, SUB);
        float titleEnd = tx + Math.max(tw("Zeus Crates", F_TITLE, Ty.XBOLD), tw(subtitle, F_SMALL, Ty.MED));

        // right side: close, keys, tokens
        float cbs = 22, cbx = px + pw - 20 - cbs, cby = cy - cbs / 2f;
        float chv = hover(9000, hot(cbx, cby, cbs, cbs), dt);
        rect(g, cbx, cby, cbs, cbs, cbs / 2f, Draw.mix(SURFACE2, 0xFF3A1E2A, chv));
        stroke(g, cbx, cby, cbs, cbs, cbs / 2f, Draw.mix(BORDER_HI, RED, chv));
        cross(g, cbx + cbs / 2f, cby + cbs / 2f, 6.5f, Draw.mix(SUB, TEXT, chv));
        hit(H_CLOSE, 0, cbx, cby, cbs, cbs);

        int realT = Crates.tokens(), realK = Crates.keys();
        if (shownTokens < 0) {
            shownTokens = realT;
            shownKeys = realK;
            lastTokens = realT;
            lastKeys = realK;
        }
        shownTokens = approach(shownTokens, realT, 9f, dt);
        shownKeys = approach(shownKeys, realK, 9f, dt);
        String ks = String.valueOf(Math.round(shownKeys)), tk = String.valueOf(Math.round(shownTokens));
        float chipH = 24, nf = F_BODY + 0.5f;
        float kw = 32 + tw(ks, nf, Ty.BOLD) + 4 + tw("keys", F_SMALL, Ty.MED) + 12;
        float kx = cbx - 10 - kw;
        rect(g, kx, cy - chipH / 2f, kw, chipH, chipH / 2f, SURFACE);
        stroke(g, kx, cy - chipH / 2f, kw, chipH, chipH / 2f, BORDER_HI);
        keyIcon(g, kx + 9, cy, 15, 0xFFC8D4F0);
        text(g, ks, kx + 32, cy, nf, Ty.BOLD, TEXT);
        text(g, "keys", kx + 32 + tw(ks, nf, Ty.BOLD) + 4, cy, F_SMALL, Ty.MED, SUB);
        float cw = 28 + tw(tk, nf, Ty.BOLD) + 4 + tw("tokens", F_SMALL, Ty.MED) + 12;
        float cx = kx - 8 - cw;
        rect(g, cx, cy - chipH / 2f, cw, chipH, chipH / 2f, SURFACE);
        stroke(g, cx, cy - chipH / 2f, cw, chipH, chipH / 2f, Draw.mix(BORDER_HI, GOLD, 0.35f));
        coin(g, cx + 13, cy, 7);
        text(g, tk, cx + 28, cy, nf, Ty.BOLD, GOLD);
        text(g, "tokens", cx + 28 + tw(tk, nf, Ty.BOLD) + 4, cy, F_SMALL, Ty.MED, SUB);
        if (realT != lastTokens) {
            floater((realT > lastTokens ? "+" : "") + (realT - lastTokens), realT > lastTokens ? GOLD : RED, cx + cw / 2f, cy + 18, t);
            lastTokens = realT;
        }
        if (realK != lastKeys) {
            floater((realK > lastKeys ? "+" : "") + (realK - lastKeys) + (Math.abs(realK - lastKeys) == 1 ? " key" : " keys"),
                    realK > lastKeys ? 0xFFC8D4F0 : RED, kx + kw / 2f, cy + 18, t);
            lastKeys = realK;
        }

        // the tab switcher: a track with a highlight that glides to the chosen tab
        float[] tws = new float[TABS.length];
        float total = 0;
        for (int i = 0; i < TABS.length; i++) {
            tws[i] = tw(TABS[i], F_BODY, Ty.SEMI) + 24;
            total += tws[i];
        }
        float trackH = 26, trackW = total + 6;
        float room = cx - 16 - (titleEnd + 16);
        boolean twoRows = trackW > room;
        float trackX = twoRows ? px + 20 : titleEnd + 16 + (room - trackW) / 2f;
        float trackY = twoRows ? py + rowH + 2 : cy - trackH / 2f;
        rect(g, trackX, trackY, trackW, trackH, trackH / 2f, 0xFF090D1A);
        stroke(g, trackX, trackY, trackW, trackH, trackH / 2f, BORDER);
        float x = trackX + 3, wantX = x, wantW = tws[0];
        for (int i = 0; i < TABS.length; i++) {
            if (i == tab) {
                wantX = x;
                wantW = tws[i];
            }
            x += tws[i];
        }
        if (underX < 0) {
            underX = wantX;
            underW = wantW;
        }
        underX = approach(underX, wantX, 18f, dt);
        underW = approach(underW, wantW, 18f, dt);
        rect(g, underX, trackY + 3, underW, trackH - 6, (trackH - 6) / 2f, 0xFF243260, 0xFF1A2546);
        stroke(g, underX, trackY + 3, underW, trackH - 6, (trackH - 6) / 2f, 0xFF3A4C80);
        x = trackX + 3;
        for (int i = 0; i < TABS.length; i++) {
            boolean on = i == tab, h = hot(x, trackY, tws[i], trackH);
            float hv = hover(2000 + i, h || on, dt);
            textC(g, TABS[i], x + tws[i] / 2f, trackY + trackH / 2f, F_BODY, Ty.SEMI, on ? TEXT : Draw.mix(FAINT, Draw.mix(SUB, TEXT, 0.5f), hv));
            hit(H_TAB, i, x, trackY, tws[i], trackH);
            x += tws[i];
        }
        float headH = twoRows ? rowH + trackH + 10 : rowH + 4;
        fill(g, px + 16, py + headH, pw - 32, 1f / s, BORDER);
        return headH;
    }

    private void footer(GuiGraphics g, float t) {
        float fy = py + ph - 40, cy = fy + 20;
        fill(g, px + 16, fy, pw - 32, 1f / s, BORDER);
        float x = px + 22;
        // next token: a clock that only runs while you play
        boolean active = Crates.activeNow();
        String lab = "Next token";
        text(g, lab, x, cy, F_SMALL, Ty.MED, SUB);
        x += tw(lab, F_SMALL, Ty.MED) + 8;
        float bw = Math.min(90, pw * 0.1f);
        bar(g, x, cy - 1.5f, bw, 3, Crates.tokenProgress(), GOLD);
        if (active) sheen(g, x, cy - 1.5f, bw * Crates.tokenProgress(), 3, 0x70FFFFFF, (t * 0.35f) % 1.6f);
        x += bw + 8;
        String clk = clock(Crates.nextTokenIn());
        text(g, clk, x, cy, F_SMALL, Ty.SEMI, TEXT);
        x += tw(clk, F_SMALL, Ty.SEMI) + 6;
        if (!active) x += chip(g, x, cy, "PAUSED", FAINT, false);
        // free crate drop, right
        String d = clock(Crates.nextDropIn());
        float dxr = px + pw - 22;
        textR(g, d, dxr, cy, F_SMALL, Ty.SEMI, TEXT);
        String dl = "Free crate in";
        float dlx = dxr - tw(d, F_SMALL, Ty.SEMI) - 6;
        textR(g, dl, dlx, cy, F_SMALL, Ty.MED, SUB);
        float left = x + 16, right = dlx - tw(dl, F_SMALL, Ty.MED) - 16;
        // daily streak, in the middle when there is room
        String st;
        int stc;
        if (Crates.streakClaimedToday()) {
            st = "Day " + Crates.streak() + " streak claimed  ·  tomorrow +" + Crates.streakReward(Crates.streakNextDay() + 1) + " tokens";
            stc = GOLD;
        } else {
            int n = Crates.streakNextDay();
            st = "Play " + clock(Crates.streakPlayLeft()) + " more today for streak day " + n + "  ·  +" + Crates.streakReward(n) + " tokens";
            stc = SUB;
        }
        float sw = tw(st, F_SMALL, Ty.MED);
        if (sw + 12 <= right - left) {
            float sx = (left + right) / 2f - (sw + 12) / 2f;
            dot(g, sx + 3, cy, 2.6f, Crates.streakClaimedToday() ? GOLD : 0xFF3A4C80);
            text(g, st, sx + 12, cy, F_SMALL, Ty.MED, stc);
        }
    }

    private static String clock(long ms) {
        long sec0 = ms / 1000;
        long h = sec0 / 3600, m = (sec0 / 60) % 60, sec = sec0 % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec) : String.format(Locale.ROOT, "%d:%02d", m, sec);
    }

    // ------------------------------------------------------------------ crates tab
    private void cratesTab(GuiGraphics g, float x0, float y0, float w0, float h0, float t, float dt) {
        int n = Crates.CRATES.size();
        float gap = 14;
        float lw = Math.max(150, Math.min(220, w0 * 0.23f));
        float sw = Math.max(196, Math.min(290, w0 * 0.31f));
        float rx = x0 + lw + gap + sw + gap, rw = x0 + w0 - rx;
        crateList(g, x0, y0, lw, h0, n, t, dt);
        stage(g, x0 + lw + gap, y0, sw, h0, t, dt);
        contents(g, rx, y0, rw, h0, t, dt);
    }

    private void crateList(GuiGraphics g, float x0, float y0, float lw, float h0, int n, float t, float dt) {
        float rgap = 6;
        float rowH = Math.min(56, (h0 - (n - 1) * rgap) / n);
        float wantBar = y0 + sel * (rowH + rgap);
        if (selBarY < 0) selBarY = wantBar;
        selBarY = approach(selBarY, wantBar, 16f, dt);
        for (int i = 0; i < n; i++) {
            if (i == sel) continue;
            float y = y0 + i * (rowH + rgap);
            float hv = hover(2500 + i, hot(x0, y, lw, rowH), dt);
            rect(g, x0, y, lw, rowH, 10, Draw.mix(SURFACE, SURFACE2, hv));
            stroke(g, x0, y, lw, rowH, 10, Draw.mix(BORDER, BORDER_HI, hv));
        }
        // the selection glides between rows
        Crate cs0 = Crates.CRATES.get(sel);
        rect(g, x0, selBarY, lw, rowH, 10, Draw.mix(SURFACE2, cs0.colour, 0.18f), Draw.mix(SURFACE, cs0.colour, 0.07f));
        stroke(g, x0, selBarY, lw, rowH, 10, Draw.rgba(cs0.colour, 0.8f));
        rect(g, x0 - 0.5f, selBarY + rowH * 0.28f, 3, rowH * 0.44f, 1.5f, cs0.colour);
        for (int i = 0; i < n; i++) {
            Crate c = Crates.CRATES.get(i);
            float y = y0 + i * (rowH + rgap), cy = y + rowH / 2f;
            boolean on = i == sel;
            float hv = on ? 0f : hoverAnim.getOrDefault(2500 + i, 0f);
            float shift = hv * 2f;
            float ts = Math.min(44, rowH - 10);
            crate(g, c, x0 + 12 + ts / 2f + shift, cy + 1, ts, t + i, 0f, 0f, 0f);
            float tx = x0 + 22 + ts + shift;
            int times = Crates.openedOf(c.id);
            float badgeW = 0;
            if (times > 0 && rowH >= 40) {
                String b = "×" + times;
                badgeW = tw(b, F_TINY, Ty.BOLD) + 10;
                chip(g, x0 + lw - 10 - badgeW, cy, b, c.colour, false);
                if (hot(x0 + lw - 10 - badgeW, cy - 6, badgeW, 12)) tip = "Opened " + times + (times == 1 ? " time" : " times");
            }
            float avail = x0 + lw - 10 - badgeW - 8 - tx;
            String name = c.name.replace(" Crate", "");
            int tc = on ? TEXT : Draw.mix(0xFFC2CCE6, TEXT, hv);
            if (rowH >= 40) {
                text(g, fit(name, avail, F_BODY + 1, Ty.SEMI), tx, cy - 6, F_BODY + 1, Ty.SEMI, tc);
                int got = Crates.collected(c), all = c.items.size();
                String gl = got + "/" + all;
                float glw = tw(gl, F_TINY, Ty.SEMI);
                float bw = Math.max(16, Math.min(70, avail - glw - 6));
                bar(g, tx, cy + 5, bw, 2.5f, got / (float) all, got == all ? GOLD : c.colour);
                text(g, gl, tx + bw + 6, cy + 6.2f, F_TINY, Ty.SEMI, got == all ? GOLD : FAINT);
            } else {
                text(g, fit(name, avail, F_BODY, Ty.SEMI), tx, cy, F_BODY, Ty.SEMI, tc);
            }
            hit(H_CRATE, i, x0, y, lw, rowH);
        }
    }

    private void stage(GuiGraphics g, float sx, float y0, float sw, float h0, float t, float dt) {
        Crate c = Crates.CRATES.get(sel);
        rect(g, sx, y0, sw, h0, 14, Draw.mix(SURFACE, c.colour, 0.10f), Draw.mix(PANEL_BOT, c.colour, 0.03f));
        stroke(g, sx, y0, sw, h0, 14, Draw.mix(BORDER, c.colour, 0.28f));
        float pad = 16, iw = sw - 2 * pad;
        float btnH = 30, rowS = 22;
        float below = 8 + 30 + 10 + 14 + 12 + btnH + 8 + rowS + 8 + rowS + 12 + 14;
        float cs = Math.max(50, Math.min(Math.min(iw - 16, 190), (h0 - 2 * pad - below) / 0.86f));
        float stackH = cs * 0.86f + below;
        float top = y0 + Math.max(pad, (h0 - stackH) / 2f);
        float ccx = sx + sw / 2f, ccy = top + cs * 0.42f;
        float fresh = 1f - clamp01((t - selAt) / 0.6f);
        // spotlight from above and a shadow on the floor
        scissor(g, sx + 1, y0 + 1, sw - 2, h0 - 2);
        glow(g, ccx - cs * 0.30f, ccy - cs * 0.22f, cs * 0.60f, cs * 0.44f, 20, c.colour, cs * 0.55f, 0.15f + 0.12f * fresh);
        glow(g, ccx - cs * 0.36f, top + cs * 0.80f, cs * 0.72f, 1.5f, 1, 0xFF000000, cs * 0.09f, 0.5f);
        float pop = outBack((t - selAt) / 0.45f);
        float bob = (float) Math.sin(t * 1.6f) * Math.max(1.2f, cs / 55f);
        crate(g, c, ccx, ccy + bob, cs * (0.84f + 0.16f * pop), t, previewLid, 0f, 0.25f * (1f - clamp01((t - selAt) / 0.5f)));
        g.disableScissor();
        lastCrateCx = ccx;
        lastCrateCy = ccy + bob;
        lastCrateSize = cs;

        float y = top + cs * 0.86f + 8;
        float ta = outCubic((t - selAt - 0.08f) / 0.3f), lift = (1f - ta) * 5f;
        textC(g, fit(c.name, iw, F_TITLE - 0.5f, Ty.XBOLD), ccx, y + 7 + lift, F_TITLE - 0.5f, Ty.XBOLD, Draw.rgba(Draw.lighten(c.colour, 0.18f), ta));
        textC(g, fit(c.tagline, iw, F_SMALL, Ty.MED), ccx, y + 22 + lift, F_SMALL, Ty.MED, Draw.rgba(SUB, ta));
        y += 30 + 10;
        // keys, and how many crates they open
        int keys = Crates.keys(), opens = keys / Crates.KEYS_PER_OPEN;
        String a = String.valueOf(keys), b = String.valueOf(opens);
        float aw = tw("Keys", F_SMALL, Ty.MED) + 4 + tw(a, F_BODY, Ty.BOLD);
        float bw2 = tw("Opens", F_SMALL, Ty.MED) + 4 + tw(b, F_BODY, Ty.BOLD);
        float rowW = aw + 22 + bw2, ax = ccx - rowW / 2f, cy = y + 7;
        text(g, "Keys", ax, cy, F_SMALL, Ty.MED, SUB);
        text(g, a, ax + tw("Keys", F_SMALL, Ty.MED) + 4, cy, F_BODY, Ty.BOLD, opens > 0 ? TEXT : RED);
        dot(g, ax + aw + 11, cy, 1.4f, FAINT);
        float bx = ax + aw + 22;
        text(g, "Opens", bx, cy, F_SMALL, Ty.MED, SUB);
        text(g, b, bx + tw("Opens", F_SMALL, Ty.MED) + 4, cy, F_BODY, Ty.BOLD, opens > 0 ? TEXT : FAINT);
        y += 14 + 12;
        // open one, two or three at once
        int can = Crates.canOpenCount();
        int n = Math.max(1, Math.min(Crates.multi(), Math.max(1, can)));
        boolean canOpen = can >= 1;
        String ol = !canOpen ? "Needs " + Crates.KEYS_PER_OPEN + " keys" : n == 1 ? "Open crate" : "Open " + n + " crates";
        button(g, H_OPEN, sx + pad, y, iw, btnH, ol, canOpen ? n * Crates.KEYS_PER_OPEN + " keys" : null, c.colour, canOpen, true, t, dt);
        y += btnH + 8;
        // how many at once, auto open and quick open
        float segW = Math.max(66, iw * 0.36f), tgW = (iw - segW - 12) / 2f;
        float scy = y + rowS / 2f;
        rect(g, sx + pad, y, segW, rowS, 8, 0xFF090D1A);
        stroke(g, sx + pad, y, segW, rowS, 8, BORDER_HI);
        float cellW = (segW - 4) / Crates.MAX_MULTI;
        for (int i = 1; i <= Crates.MAX_MULTI; i++) {
            float cx2 = sx + pad + 2 + (i - 1) * cellW;
            boolean ok = i <= Math.max(1, can), on = i == n;
            boolean h = ok && hot(cx2, y, cellW, rowS);
            float hv = hover(6100 + i, h, dt);
            if (on) rect(g, cx2, y + 2, cellW, rowS - 4, 6, Draw.mix(c.colour, 0xFF0A0E1A, 0.55f));
            else if (hv > 0.01f) rect(g, cx2, y + 2, cellW, rowS - 4, 6, Draw.rgba(RAISED, hv));
            textC(g, "×" + i, cx2 + cellW / 2f, scy, F_SMALL, Ty.BOLD, on ? TEXT : ok ? Draw.mix(SUB, TEXT, hv) : 0xFF3A4766);
            if (ok) hit(H_MULTI, i, cx2, y, cellW, rowS);
            else if (hot(cx2, y, cellW, rowS)) tip = "Opening " + i + " at once takes " + i * Crates.KEYS_PER_OPEN + " keys";
        }
        float ax2 = sx + pad + segW + 6;
        toggle(g, H_AUTO, ax2, y, tgW, rowS, "Auto", Crates.auto(), 6002, dt);
        if (hot(ax2, y, tgW, rowS)) tip = Crates.auto() ? "Auto open is on: opens again after each reveal while keys last" : "Auto open: keep opening after each reveal while keys last";
        float qx = ax2 + tgW + 6;
        toggle(g, H_QUICK, qx, y, tgW, rowS, "Quick", Crates.quick(), 6000, dt);
        if (hot(qx, y, tgW, rowS)) tip = Crates.quick() ? "Quick open is on: a shorter opening" : "Quick open: a shorter opening animation";
        y += rowS + 8;
        // buy keys with tokens
        boolean canBuy = Crates.tokens() >= Crates.KEY_PRICE;
        String br = canBuy ? Crates.KEY_PRICE + " tokens" : (Crates.KEY_PRICE - Crates.tokens()) + " more tokens";
        button(g, H_BUY, sx + pad, y, iw, rowS, "Buy key", br, GOLD, canBuy, false, t, dt);
        if (canBuy && hot(sx + pad, y, iw, rowS) && Crates.tokens() >= Crates.KEY_PRICE * 2) {
            tip = "Shift-click to buy " + Math.min(Crates.KEYS_PER_OPEN, Crates.tokens() / Crates.KEY_PRICE) + " at once";
        }
        y += rowS + 12;
        // pity: a guaranteed Divine or better after a dry run
        int pi = Crates.pityIn();
        float pf = (Crates.PITY - pi) / (float) Crates.PITY;
        String pl = pi <= 1 ? "Next open: Divine or better guaranteed" : "Divine or better guaranteed in " + pi + " opens";
        text(g, fit(pl, iw, F_TINY + 0.3f, Ty.MED), sx + pad, y + 2, F_TINY + 0.3f, Ty.MED, pi <= 3 ? PITY : SUB);
        bar(g, sx + pad, y + 8, iw, 2.5f, pf, PITY);
    }

    private void toggle(GuiGraphics g, int kind, float x, float y, float w, float h, String label, boolean on, int key, float dt) {
        float cy = y + h / 2f;
        boolean hh = hot(x, y, w, h);
        float v = hover(key, on, dt), hv = hover(key + 50, hh, dt);
        rect(g, x, y, w, h, 8, Draw.mix(SURFACE2, RAISED, hv));
        stroke(g, x, y, w, h, 8, Draw.mix(BORDER_HI, GOLD, v * 0.55f + hv * 0.2f));
        float sw = 16, lw = tw(label, F_SMALL, Ty.SEMI), x0 = x + w / 2f - (sw + 5 + lw) / 2f;
        rect(g, x0, cy - 4, sw, 8, 4, Draw.mix(0xFF26314F, GOLD, v));
        dot(g, x0 + 4 + v * 8, cy, 3f, Draw.mix(0xFFB8C4E2, 0xFF1A1206, v));
        text(g, label, x0 + sw + 5, cy, F_SMALL, Ty.SEMI, on ? Draw.lighten(GOLD, 0.2f) : SUB);
        hit(kind, 0, x, y, w, h);
    }

    private void contents(GuiGraphics g, float rx, float y0, float rw, float h0, float t, float dt) {
        Crate c = Crates.CRATES.get(sel);
        List<Item> items = c.items;
        text(g, "Contents", rx, y0 + 6, F_H, Ty.BOLD, TEXT);
        int got = Crates.collected(c);
        textR(g, got + " / " + items.size() + " collected", rx + rw, y0 + 6, F_SMALL, Ty.MED, got == items.size() ? GOLD : SUB);
        bar(g, rx, y0 + 16, rw, 2.5f, got / (float) items.size(), c.colour);

        // the odds, as a legend along the bottom
        Rarity[] rs = Rarity.values();
        int lcols = rw >= 400 ? 6 : 3;
        int lrows = (rs.length + lcols - 1) / lcols;
        float legendH = lrows * 13;
        float ly = y0 + h0 - legendH;
        for (int i = rs.length - 1, k = 0; i >= 0; i--, k++) {
            float lx = rx + (k % lcols) * (rw / lcols), lcy = ly + (k / lcols) * 13 + 6;
            dot(g, lx + 3, lcy, 2.8f, rs[i].tint());
            text(g, rs[i].label, lx + 9, lcy, F_TINY + 0.3f, Ty.MED, 0xFFB4C0DC);
            text(g, rs[i].odds(), lx + 12 + tw(rs[i].label, F_TINY + 0.3f, Ty.MED), lcy, F_TINY + 0.3f, Ty.SEMI, FAINT);
        }

        float gy = y0 + 26, gh = ly - 8 - gy;
        int cols = 4;
        float gap = 6;
        boolean featured = items.size() >= 9 && items.get(0).rarity() == Rarity.CHROMA;
        int slots = featured ? items.size() - 1 + 4 : items.size();
        int rows = (slots + cols - 1) / cols;
        float tw0 = (rw - (cols - 1) * gap) / cols;
        float th = Math.min(tw0 * 1.05f, (gh - (rows - 1) * gap) / rows);
        // the free cells, row by row, skipping the featured block in the top left
        List<float[]> cells = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            for (int k = 0; k < cols; k++) {
                if (featured && r < 2 && k < 2) continue;
                cells.add(new float[] {rx + k * (tw0 + gap), gy + r * (th + gap), tw0, th});
            }
        }
        int ci = 0;
        cardsBegin();
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            boolean big = featured && i == 0;
            float[] b = big ? new float[] {rx, gy, 2 * tw0 + gap, 2 * th + gap} : cells.get(Math.min(ci++, cells.size() - 1));
            float e = outCubic((t - selAt - 0.04f - i * 0.028f) / 0.28f);
            if (e <= 0f) continue;
            boolean h = hot(b[0], b[1], b[2], b[3]);
            float hv = hover(3000 + i, h, dt);
            float yy = b[1] + (1f - e) * 8f - hv * 1.5f;
            Card cd = cardAdd().set(it, b[0], yy, b[2], b[3], Crates.owns(it.id()), false, hv, big);
            cd.veil = 1f - e;
            cd.veilColour = PANEL_BOT;
            if (h) tipItem = it;
            hit(H_INSPECT, i, b[0], b[1], b[2], b[3]);
        }
        cardsDraw(g, t * 0.8f);
    }

    private void tooltip(GuiGraphics g, Item it) {
        String n = nameOf(it.id()).replace(" · ", " ");
        Crate from = Crates.crateOf(it.id());
        String l2 = it.rarity().label + "  ·  " + it.rarity().odds() + (from == null ? "  ·  starter" : "  ·  " + from.name);
        boolean own = Crates.owns(it.id());
        String l3 = own ? "Owned  ·  click to inspect" : "Not owned yet  ·  click to inspect";
        float w = Math.max(tw(n, F_BODY + 0.5f, Ty.BOLD), Math.max(tw(l2, F_SMALL, Ty.MED), tw(l3, F_SMALL, Ty.MED))) + 20, h = 44;
        float x = Math.min(mxu + 12, W - w - 6), y = Math.min(myu + 12, H - h - 6);
        Draw.shadow(g, P(x), P(y), P(w), P(h), P(8), P(8), 0.6f);
        rect(g, x, y, w, h, 8, 0xF8121A30, 0xF80B1020);
        stroke(g, x, y, w, h, 8, Draw.rgba(it.rarity().tint(), 0.8f));
        fill(g, x + 10, y + 1, 18, 1.2f, it.rarity().tint());
        text(g, n, x + 10, y + 12, F_BODY + 0.5f, Ty.BOLD, TEXT);
        text(g, l2, x + 10, y + 24, F_SMALL, Ty.MED, it.rarity().tint());
        text(g, l3, x + 10, y + 35, F_SMALL, Ty.MED, own ? GREEN : FAINT);
    }

    private void textTip(GuiGraphics g, String s0) {
        float w = tw(s0, F_SMALL, Ty.MED) + 16, h = 18;
        float x = Math.min(mxu + 12, W - w - 6), y = Math.min(myu + 14, H - h - 6);
        rect(g, x, y, w, h, 6, 0xF8121A30);
        stroke(g, x, y, w, h, 6, BORDER_HI);
        text(g, s0, x + 8, y + h / 2f, F_SMALL, Ty.MED, SUB);
    }

    // ------------------------------------------------------------------ collection tab
    private static List<Item> ALL;

    private static List<Item> allItems() {
        if (ALL == null) {
            List<Item> l = new ArrayList<>();
            l.add(new Item(Crates.STARTER, Rarity.MORTAL));
            for (Crate c : Crates.CRATES) l.addAll(c.items);
            ALL = l;
        }
        return ALL;
    }

    private void collectionTab(GuiGraphics g, float x0, float y0, float w0, float h0, float t, float dt) {
        List<Item> all = allItems();
        int ownedN = Crates.ownedCount();
        text(g, "Your knives", x0, y0 + 6, F_H, Ty.BOLD, TEXT);
        float hw = tw("Your knives", F_H, Ty.BOLD);
        text(g, ownedN + " of " + all.size() + " collected", x0 + hw + 10, y0 + 6.5f, F_SMALL, Ty.MED, SUB);
        textR(g, "Click a knife you own to hold it", x0 + w0, y0 + 6.5f, F_SMALL, Ty.MED, FAINT);
        bar(g, x0, y0 + 16, w0, 2.5f, ownedN / (float) all.size(), GOLD);
        float gy = y0 + 26, gh = h0 - 26;
        float gap = 8;
        int cols = Math.max(4, (int) ((w0 + gap) / (122 + gap)));
        float tw0 = (w0 - 8 - (cols - 1) * gap) / cols, th = Math.round(tw0 * 0.78f);
        int rows = (all.size() + cols - 1) / cols;
        float maxScroll = Math.max(0, rows * (th + gap) - gap - gh);
        collScroll = Math.max(0, Math.min(maxScroll, collScroll));
        collScrollShown = approach(collScrollShown, collScroll, 18f, dt);
        scissor(g, x0 - 4, gy, w0 + 8, gh);
        int held = KnifeSwing.heldHd();
        boolean inView = myu >= gy && myu < gy + gh;
        cardsBegin();
        float heldX = 0, heldY = 0;
        boolean heldShown = false;
        for (int i = 0; i < all.size(); i++) {
            float x = x0 + (i % cols) * (tw0 + gap), y = gy + (i / cols) * (th + gap) - collScrollShown;
            if (y + th < gy || y > gy + gh) continue;
            Item it = all.get(i);
            boolean own = Crates.owns(it.id());
            boolean h = inView && hot(x, y, tw0, th);
            float hv = hover(4000 + i, h, dt) * (own ? 1f : 0.35f);
            float yy = y - hv * 1.5f;
            cardAdd().set(it, x, yy, tw0, th, false, !own, hv, false);
            if (own && Crates.hdOf(it.id()) == held) {
                heldShown = true;
                heldX = x;
                heldY = yy;
            }
            if (h) {
                tipItem = it;
                hit(H_COLL, i, x, y, tw0, th);
            }
        }
        cardsDraw(g, t * 0.8f);
        if (heldShown) {
            stroke(g, heldX - 1.5f, heldY - 1.5f, tw0 + 3, th + 3, 8.5f, GREEN);
            chip(g, heldX + 6, heldY + 10, "IN HAND", GREEN, true);
        }
        g.disableScissor();
        if (maxScroll > 0) {
            float bh = Math.max(20, gh * gh / (rows * (th + gap)));
            float by = gy + (gh - bh) * collScrollShown / maxScroll;
            rect(g, x0 + w0 - 2.5f, gy, 2.5f, gh, 1.25f, 0xFF121A30);
            rect(g, x0 + w0 - 2.5f, by, 2.5f, bh, 1.25f, BORDER_HI);
        }
    }

    // ------------------------------------------------------------------ history tab
    private static String ago(long at) {
        long sec = Math.max(0, (com.zlorbie788.zeus.core.Clock.millis() - at) / 1000);
        if (sec < 60) return "just now";
        if (sec < 3600) return (sec / 60) + " min ago";
        if (sec < 86400) return (sec / 3600) + " h ago";
        return (sec / 86400) + " d ago";
    }

    private void historyTab(GuiGraphics g, float x0, float y0, float w0, float h0, float t, float dt) {
        List<Crates.Drop> hist = Crates.history();
        // left: your luck, drop counts per rarity and the best drop
        float sw = Math.max(170, Math.min(230, w0 * 0.28f));
        rect(g, x0, y0, sw, h0, 12, SURFACE2, SURFACE);
        stroke(g, x0, y0, sw, h0, 12, BORDER);
        float ix = x0 + 14, iw = sw - 28, y = y0 + 16;
        text(g, "Your stats", ix, y, F_H, Ty.BOLD, TEXT);
        y += 14;
        // lifetime numbers, two to a row
        String fav = "-";
        int favN = 0;
        for (Crate c : Crates.CRATES) {
            int o = Crates.openedOf(c.id);
            if (o > favN) {
                favN = o;
                fav = c.name.replace(" Crate", "");
            }
        }
        String[][] tiles = {
                {String.valueOf(Crates.opened()), "Crates opened"},
                {Crates.uniqueFound() + "/" + (allItems().size() - 1), "Knives found"},
                {String.valueOf(Crates.duplicates()), "Duplicates"},
                {String.valueOf(Crates.earned()), "Tokens earned"},
                {String.valueOf(Crates.keysBought()), "Keys bought"},
                {String.valueOf(Crates.bestStreak()), "Best streak"},
                {fav, "Favourite crate"},
                {String.valueOf(Crates.refunded()), "Tokens refunded"}};
        float tgap = 6, tw2 = (iw - tgap) / 2f, th2 = 30;
        int tileRows = h0 < 330 ? 2 : h0 < 400 ? 3 : 4;
        for (int i = 0; i < tileRows * 2; i++) {
            float tx = ix + (i % 2) * (tw2 + tgap), ty = y + (i / 2) * (th2 + tgap);
            rect(g, tx, ty, tw2, th2, 7, 0xFF0B1122);
            stroke(g, tx, ty, tw2, th2, 7, BORDER);
            text(g, fit(tiles[i][0], tw2 - 14, F_BODY + 1.5f, Ty.XBOLD), tx + 8, ty + 11, F_BODY + 1.5f, Ty.XBOLD, i == 0 ? GOLD : TEXT);
            text(g, fit(tiles[i][1], tw2 - 14, F_TINY, Ty.MED), tx + 8, ty + 23, F_TINY, Ty.MED, FAINT);
        }
        y += tileRows * (th2 + tgap) + 6;
        text(g, "RECENT LUCK", ix, y, F_TINY, Ty.BOLD, FAINT);
        y += 9;
        int[] by = Crates.dropsByRarity();
        int total = Math.max(1, hist.size());
        Rarity[] rs = Rarity.values();
        float grow = outCubic((t - tabAt) / 0.6f);
        for (int i = rs.length - 1; i >= 0; i--) {
            if (y > y0 + h0 - 20) break;
            dot(g, ix + 3, y + 4, 2.8f, rs[i].tint());
            text(g, rs[i].label, ix + 10, y + 4, F_SMALL, Ty.MED, 0xFFB4C0DC);
            textR(g, String.valueOf(by[i]), ix + iw, y + 4, F_SMALL, Ty.SEMI, by[i] > 0 ? TEXT : FAINT);
            bar(g, ix, y + 11, iw, 2.5f, grow * by[i] / (float) total, rs[i].tint());
            y += 19;
        }
        Crates.Drop best = null;
        for (Crates.Drop d : hist) if (best == null || Crates.rarityOf(d.knife()).ordinal() > Crates.rarityOf(best.knife()).ordinal()) best = d;
        if (best != null && y0 + h0 - y > 56) {
            fill(g, ix, y + 2, iw, 1f / s, BORDER);
            text(g, "BEST DROP", ix, y + 11, F_TINY, Ty.BOLD, FAINT);
            float bh = Math.min(76, y0 + h0 - y - 44);
            Rarity br = Crates.rarityOf(best.knife());
            glow(g, x0 + sw / 2f - 1, y + 20 + bh / 2f - 1, 2, 2, 1, br.tint(), bh * 0.5f, 0.18f);
            knife(g, kindOf(best.knife()), ix, y + 18, iw, bh, false, 0f);
            textC(g, fit(nameOf(best.knife()).replace(" · ", " "), iw, F_BODY, Ty.SEMI), x0 + sw / 2f, y + 26 + bh, F_BODY, Ty.SEMI, br.tint());
        }
        // right: the list of drops
        float lx = x0 + sw + 14, lw = x0 + w0 - lx;
        text(g, "Recent drops", lx, y0 + 6, F_H, Ty.BOLD, TEXT);
        if (!hist.isEmpty()) textR(g, "Last " + hist.size(), lx + lw, y0 + 6.5f, F_SMALL, Ty.MED, FAINT);
        float gy = y0 + 20, gh = h0 - 20;
        if (hist.isEmpty()) {
            float ecy = gy + gh / 2f;
            rect(g, lx, gy, lw, gh, 12, 0xFF0A0F1E);
            stroke(g, lx, gy, lw, gh, 12, BORDER);
            textC(g, "Nothing opened yet", lx + lw / 2f, ecy - 18, F_H, Ty.BOLD, SUB);
            textC(g, "Every knife you unbox will show up here.", lx + lw / 2f, ecy - 4, F_SMALL, Ty.MED, FAINT);
            float bw = 110;
            button(g, H_TAB, lx + lw / 2f - bw / 2f, ecy + 10, bw, 22, "Go to crates", null, GOLD, true, false, t, dt);
            return;
        }
        float rowH = 34, gap = 5;
        float maxScroll = Math.max(0, hist.size() * (rowH + gap) - gap - gh);
        histScroll = Math.max(0, Math.min(maxScroll, histScroll));
        histScrollShown = approach(histScrollShown, histScroll, 18f, dt);
        scissor(g, lx - 2, gy, lw + 4, gh);
        boolean inView = myu >= gy && myu < gy + gh;
        for (int i = 0; i < hist.size(); i++) {
            float y1 = gy + i * (rowH + gap) - histScrollShown;
            if (y1 + rowH < gy || y1 > gy + gh) continue;
            Crates.Drop d = hist.get(i);
            Rarity r = Crates.rarityOf(d.knife());
            float e = outCubic((t - tabAt - i * 0.025f) / 0.25f);
            float ox = (1f - e) * 12f;
            boolean h = inView && hot(lx, y1, lw, rowH);
            float hv = hover(7000 + i, h, dt);
            rect(g, lx + ox, y1, lw, rowH, 8, Draw.mix(SURFACE, Draw.mix(SURFACE, r.tint(), 0.16f), hv));
            stroke(g, lx + ox, y1, lw, rowH, 8, Draw.mix(BORDER, Draw.rgba(r.tint(), 0.6f), 0.2f + 0.5f * hv));
            rect(g, lx + ox, y1 + 9, 2.5f, rowH - 18, 1.2f, r.tint());
            knife(g, kindOf(d.knife()), lx + ox + 10, y1 + 3, 54, rowH - 6, false, 0f);
            Crates.Crate cr = Crates.crate(d.crate());
            String badge = d.duplicate() ? "DUPLICATE" : "NEW";
            float bw = tw(badge, F_TINY, Ty.BOLD) + 10;
            float right = lx + ox + lw - 12;
            float nameW = right - Math.max(bw, tw(ago(d.at()), F_SMALL, Ty.MED)) - 12 - (lx + ox + 72);
            text(g, fit(nameOf(d.knife()).replace(" · ", " "), nameW, F_BODY, Ty.BOLD), lx + ox + 72, y1 + 11, F_BODY, Ty.BOLD, TEXT);
            text(g, fit(r.label + (cr == null ? "" : "  ·  " + cr.name), nameW, F_SMALL, Ty.MED), lx + ox + 72, y1 + 23, F_SMALL, Ty.MED, r.tint());
            textR(g, ago(d.at()), right, y1 + 11, F_SMALL, Ty.MED, FAINT);
            chip(g, right - bw, y1 + 23, badge, d.duplicate() ? FAINT : GREEN, false);
            if (e < 1f) rect(g, lx + ox, y1, lw, rowH, 8, Draw.rgba(PANEL_BOT, 1f - e));
            if (h) hit(H_HIST, i, lx, y1, lw, rowH);
        }
        g.disableScissor();
        if (maxScroll > 0) {
            float bh = Math.max(20, gh * gh / (hist.size() * (rowH + gap)));
            float by2 = gy + (gh - bh) * histScrollShown / maxScroll;
            rect(g, lx + lw + 4, by2, 2.5f, bh, 1.25f, BORDER_HI);
        }
    }

    // ------------------------------------------------------------------ redeem tab
    private void redeemTab(GuiGraphics g, float x0, float y0, float w0, float h0, float t, float dt) {
        float cw = Math.min(460, w0), cx = x0 + w0 / 2f, x = cx - cw / 2f;
        float c1 = 132, c2 = 116;
        boolean both = h0 >= c1 + 12 + c2;
        float y = y0 + Math.max(0, (h0 - (both ? c1 + 12 + c2 : c1)) / 2f);
        rect(g, x, y, cw, c1, 14, SURFACE2, SURFACE);
        stroke(g, x, y, cw, c1, 14, BORDER);
        textC(g, "Redeem a code", cx, y + 22, F_TITLE, Ty.XBOLD, TEXT);
        textC(g, "Promo codes give keys and tokens to open more crates.", cx, y + 38, F_SMALL, Ty.MED, SUB);
        float bw = 96, fh = 30, fx = x + 24, fy = y + 54, fw = cw - 48 - bw - 8;
        float fv = hover(5000, codeBox != null && codeBox.isFocused(), dt);
        glow(g, fx, fy, fw, fh, 8, GOLD, 8, 0.04f + 0.14f * fv);
        rect(g, fx, fy, fw, fh, 8, 0xFF090D1A);
        stroke(g, fx, fy, fw, fh, 8, Draw.mix(BORDER_HI, GOLD, fv));
        hit(H_FIELD, -1, fx, fy, fw, fh);
        placeCodeBox(fx + 10, fy + fh / 2f, fw - 20);
        String val = codeBox == null ? "" : codeBox.getValue();
        float tcy = fy + fh / 2f, tx = fx + 12;
        if (val.isEmpty()) text(g, "ZEUS-XXXX-XXXX", tx, tcy, F_BODY + 1, Ty.MED, 0xFF3A4766);
        else text(g, fit(val, fw - 24, F_BODY + 1, Ty.SEMI), tx, tcy, F_BODY + 1, Ty.SEMI, TEXT);
        if (codeBox != null && codeBox.isFocused() && (int) (t * 1.9f) % 2 == 0) {
            int cur = Math.max(0, Math.min(val.length(), codeBox.getCursorPosition()));
            float cx2 = tx + tw(val.substring(0, cur), F_BODY + 1, Ty.SEMI) + 0.6f;
            fill(g, cx2, tcy - 6, 1.2f, 12, GOLD);
        }
        button(g, H_REDEEM, fx + fw + 8, fy, bw, fh, "Redeem", null, GOLD, codeBox != null && !codeBox.getValue().isBlank(), true, t, dt);
        if (!codeMsg.isEmpty()) {
            long since = com.zlorbie788.zeus.core.Clock.millis() - codeAt;
            float a = Math.min(1f, since / 200f);
            float shake = codeOk ? 0f : (1f - Math.min(1f, since / 350f)) * 4f * (float) Math.sin(since / 18.0);
            textC(g, fit(codeMsg, cw - 30, F_BODY, Ty.SEMI), cx + shake, y + 106, F_BODY, Ty.SEMI, Draw.rgba(codeOk ? GREEN : RED, a));
        }
        if (!both) return;
        y += c1 + 12;
        rect(g, x, y, cw, c2, 14, SURFACE);
        stroke(g, x, y, cw, c2, 14, BORDER);
        text(g, "How to earn", x + 18, y + 18, F_H, Ty.BOLD, GOLD);
        textR(g, "Opened " + Crates.opened(), x + cw - 18, y + 18.5f, F_SMALL, Ty.MED, FAINT);
        String[] lines = {"1 Zeus token for every 5 minutes of active play. Standing still earns nothing.",
                "A key costs " + Crates.KEY_PRICE + " tokens and any crate opens with " + Crates.KEYS_PER_OPEN + " keys. Every 90 minutes of play: a free crate.",
                "Already own the knife? You get tokens back instead, more for rarer drops."};
        for (int i = 0; i < lines.length; i++) {
            float ly = y + 42 + i * 24;
            if (i == 0) coin(g, x + 25, ly, 6);
            else if (i == 1) keyIcon(g, x + 18, ly, 13, 0xFFC8D4F0);
            else {
                dot(g, x + 25, ly, 6, 0xFF1F9A58);
                tick(g, x + 25, ly, 6.5f, 0xFFFFFFFF);
            }
            text(g, fit(lines[i], cw - 62, F_SMALL, Ty.MED), x + 40, ly, F_SMALL, Ty.MED, SUB);
        }
    }

    // ------------------------------------------------------------------ daily tab
    private void dailyTab(GuiGraphics g, float x0, float y0, float w0, float h0, float t) {
        float cw = Math.min(540, w0), x = x0 + (w0 - cw) / 2f;
        text(g, "Daily quests", x, y0 + 6, F_H, Ty.BOLD, TEXT);
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        long left = java.time.Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMillis();
        textR(g, "New quests in " + clock(left), x + cw, y0 + 6.5f, F_SMALL, Ty.MED, FAINT);
        List<com.zlorbie788.zeus.core.Quests.Quest> qs = com.zlorbie788.zeus.core.Quests.list();
        float ch = Math.max(40, Math.min(62, (h0 - 22 - 44) / Math.max(1, qs.size()) - 8));
        float y = y0 + 22;
        for (int i = 0; i < qs.size(); i++) {
            var q = qs.get(i);
            float in = outCubic((t - tabAt - i * 0.06f) / 0.35f);
            float yy = y + (1f - in) * 10f, cy = yy + ch / 2f;
            boolean done = q.done();
            rect(g, x, yy, cw, ch, 12, done ? Draw.mix(SURFACE2, GREEN, 0.10f) : SURFACE2, SURFACE);
            stroke(g, x, yy, cw, ch, 12, done ? Draw.rgba(GREEN, 0.5f) : BORDER);
            dot(g, x + 24, cy, 11, done ? 0xFF1F9A58 : 0xFF1D2846);
            if (done) tick(g, x + 24, cy, 9, 0xFFFFFFFF);
            else textC(g, String.valueOf(i + 1), x + 24, cy, F_BODY, Ty.BOLD, SUB);
            String reward = "+" + q.kind().reward + " tokens";
            float rw2 = tw(reward, F_SMALL, Ty.BOLD) + 18, rh = 20;
            float rx = x + cw - 14 - rw2;
            rect(g, rx, cy - rh / 2f, rw2, rh, rh / 2f, done ? Draw.rgba(GREEN, 0.18f) : Draw.rgba(GOLD, 0.14f));
            stroke(g, rx, cy - rh / 2f, rw2, rh, rh / 2f, done ? Draw.rgba(GREEN, 0.5f) : Draw.rgba(GOLD, 0.45f));
            textC(g, reward, rx + rw2 / 2f, cy, F_SMALL, Ty.BOLD, done ? GREEN : GOLD);
            float tx = x + 46, avail = rx - 14 - tx;
            String count = q.progress() + " / " + q.target();
            text(g, fit(q.text(), avail, F_BODY + 0.5f, Ty.SEMI), tx, cy - 6, F_BODY + 0.5f, Ty.SEMI, done ? 0xFFBFF5DA : TEXT);
            float cwid = tw(count, F_SMALL, Ty.SEMI);
            float bw = Math.max(20, avail - cwid - 10);
            bar(g, tx, cy + 5, bw, 3, q.fraction() * outCubic((t - tabAt - 0.1f) / 0.6f), done ? GREEN : GOLD);
            text(g, count, tx + bw + 10, cy + 6.5f, F_SMALL, Ty.SEMI, SUB);
            y += ch + 8;
        }
        boolean all = com.zlorbie788.zeus.core.Quests.allDone();
        String bonus = all ? "All done! Bonus paid: +" + com.zlorbie788.zeus.core.Quests.BONUS + " tokens"
                : "Finish all three for +" + com.zlorbie788.zeus.core.Quests.BONUS + " bonus tokens";
        if (y + 10 < y0 + h0) textC(g, fit(bonus, cw, F_BODY, Ty.SEMI), x + cw / 2f, y + 6, F_BODY, Ty.SEMI, all ? GREEN : SUB);
        String st = Crates.streakClaimedToday() ? "Daily streak: day " + Crates.streak() + " claimed" : "Daily streak: play " + clock(Crates.streakPlayLeft()) + " more today";
        if (y + 24 < y0 + h0) textC(g, fit(st, cw, F_SMALL, Ty.MED), x + cw / 2f, y + 21, F_SMALL, Ty.MED, FAINT);
    }

    // ------------------------------------------------------------------ inspect
    private void openInspect(Item it) {
        inspect = it;
        inspectAt = now();
        inspectYaw = -0.35f;
        inspectSpin = 0f;
        hideCodeBox();
        snd(SoundEvents.ARMOR_EQUIP_GENERIC, 1.3f, 0.5f);
    }

    private float[] inspectBox() {
        float bw = Math.min(W * 0.62f, 440), bh = Math.min(H * 0.46f, 240);
        return new float[] {W / 2f - bw / 2f, H / 2f - bh / 2f - 40, bw, bh};
    }

    private void renderInspect(GuiGraphics g, float t, float dt) {
        hits.clear();
        float u = t - inspectAt;
        float e = outCubic(u / 0.3f);
        fill(g, 0, 0, W, H, Draw.rgba(0xE0030610, e));
        Item it = inspect;
        Rarity r = it.rarity();
        int rc = r.tint();
        float[] b = inspectBox();
        glow(g, W / 2f - 90, b[1] + b[3] / 2f - 40, 180, 80, 40, rc, Math.min(W, H) * 0.42f, 0.12f * e);
        // turn: drag with the mouse, or it drifts slowly on its own
        if (dragging) {
            inspectSpin = (float) (mxu - dragLastX) * 0.035f / Math.max(dt, 1e-3f) * 0.016f;
            inspectYaw += (float) (mxu - dragLastX) * 0.035f;
            dragLastX = mxu;
        } else {
            inspectSpin = approach(inspectSpin, 0.35f * dt, 3f, dt);
            inspectYaw += inspectSpin;
        }
        float pop = 0.85f + 0.15f * outBack(u / 0.4f);
        float bw = b[2] * pop, bh = b[3] * pop;
        float bob = 3f * (float) Math.sin(u * 1.8f);
        knifeSpin(g, Crates.hdOf(it.id()), W / 2f - bw / 2f, b[1] + b[3] / 2f - bh / 2f + bob, bw, bh, inspectYaw, 0.06f * (float) Math.sin(u * 0.7f));
        boolean own = Crates.owns(it.id());
        String full = nameOf(it.id());
        float ty = b[1] + b[3] + 14;
        float ta = outCubic((u - 0.1f) / 0.3f);
        textC(g, typeName(full).toUpperCase(Locale.ROOT), W / 2f, ty, F_SMALL, Ty.BOLD, Draw.rgba(SUB, ta));
        textC(g, shortName(full), W / 2f, ty + 17, 19, Ty.XBOLD, Draw.rgba(TEXT, ta));
        Crate from = Crates.crateOf(it.id());
        String line = r.label + "  ·  " + r.odds() + "  ·  " + (from == null ? "starter knife" : from.name);
        textC(g, line, W / 2f, ty + 36, F_SMALL, Ty.SEMI, Draw.rgba(rc, ta));
        textC(g, own ? "Drag to turn it" : "Locked  ·  open the " + (from == null ? "crates" : from.name) + " to get it", W / 2f, ty + 50, F_SMALL,
                Ty.MED, Draw.rgba(own ? FAINT : RED, ta));
        float w = 112, gap = 8, bhh = 26;
        float y = Math.min(H - bhh - 12, ty + 64);
        float bt = outCubic((u - 0.2f) / 0.3f);
        float yy = y + (1f - bt) * 10f;
        if (own) {
            boolean held = Crates.hdOf(it.id()) == KnifeSwing.heldHd();
            button(g, H_EQUIP, W / 2f - w - gap / 2f, yy, w, bhh, held ? "In hand" : "Equip", null, rc, !held, true, t, dt);
            button(g, H_DONE, W / 2f + gap / 2f, yy, w, bhh, "Close", null, 0xFF5E73A0, true, false, t, dt);
        } else {
            button(g, H_DONE, W / 2f - w / 2f, yy, w, bhh, "Close", null, 0xFF5E73A0, true, false, t, dt);
        }
    }

    private void inspectClick(float mx, float my) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            float[] h = hits.get(i);
            if (!in(mx, my, h[2], h[3], h[4], h[5])) continue;
            if (h[0] == H_EQUIP) {
                equip(inspect.id());
                inspect = null;
            } else if (h[0] == H_DONE) {
                inspect = null;
            }
            return;
        }
        float[] b = inspectBox();
        if (in(mx, my, b[0] - 20, b[1] - 20, b[2] + 40, b[3] + 40)) {
            dragging = true;
            dragLastX = mx;
        } else if (my < b[1] - 30 || my > H - 10) {
            inspect = null;
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }

    // ------------------------------------------------------------------ floaters (+1 token, +1 key) and the toast
    private void floater(String text, int colour, float x, float y, float t) {
        floats.add(new Object[] {text, colour, x, y, t});
    }

    private void floaters(GuiGraphics g, float t) {
        for (int i = floats.size() - 1; i >= 0; i--) {
            Object[] f = floats.get(i);
            float u = t - (float) f[4];
            if (u > 1.2f) {
                floats.remove(i);
                continue;
            }
            float a = u < 0.15f ? u / 0.15f : 1f - clamp01((u - 0.7f) / 0.5f);
            float y = (float) f[3] + outCubic(u / 1.2f) * 12f;
            textC(g, (String) f[0], (float) f[2], y, F_BODY, Ty.BOLD, Draw.rgba((int) f[1], a));
        }
    }

    private void toast(GuiGraphics g) {
        if (flash.isEmpty()) return;
        long since = com.zlorbie788.zeus.core.Clock.millis() - flashAt;
        float a = Math.min(1f, since / 150f) * (1f - clamp01((since - 2200) / 600f));
        if (a <= 0f) {
            if (since > 2200) flash = "";
            return;
        }
        float w = tw(flash, F_BODY, Ty.SEMI) + 34, h = 26;
        float x = W / 2f - w / 2f, y = py + ph - 40 - h - 12 + (1f - outCubic(since / 250f)) * 8f;
        Draw.shadow(g, P(x), P(y), P(w), P(h), P(h / 2f), P(8), 0.6f * a);
        rect(g, x, y, w, h, h / 2f, Draw.rgba(0xFF121A30, a));
        stroke(g, x, y, w, h, h / 2f, Draw.rgba(GREEN, 0.6f * a));
        dot(g, x + 14, y + h / 2f, 3, Draw.rgba(GREEN, a));
        text(g, flash, x + 24, y + h / 2f, F_BODY, Ty.SEMI, Draw.rgba(TEXT, a));
    }

    // ------------------------------------------------------------------ input
    private void setTab(int i) {
        if (i == tab) return;
        snd(SoundEvents.UI_BUTTON_CLICK, 1.2f, 0.5f);
        prevTab = tab;
        tab = i;
        tabAt = now();
        if (tab == T_REDEEM) setFocused(codeBox);
    }

    private void setSel(int i) {
        if (i == sel) return;
        snd(SoundEvents.UI_BUTTON_CLICK, 1.4f, 0.4f);
        sel = i;
        selAt = now();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        float mx = (float) event.x() * gs / s, my = (float) event.y() * gs / s;
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        if (opening != null) {
            opening.click(mx, my);
            return true;
        }
        if (inspect != null) {
            inspectClick(mx, my);
            return true;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            float[] h = hits.get(i);
            if (!in(mx, my, h[2], h[3], h[4], h[5])) continue;
            switch ((int) h[0]) {
                case H_TAB -> setTab((int) h[1]);
                case H_CRATE -> setSel((int) h[1]);
                case H_OPEN -> startOpening(Crates.CRATES.get(sel));
                case H_BUY -> {
                    int n = Crates.buyKeys(event.hasShiftDown() ? Crates.KEYS_PER_OPEN : 1);
                    if (n > 0) {
                        snd(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2f, 0.6f);
                        say("Bought " + (n == 1 ? "a key" : n + " keys") + " for " + n * Crates.KEY_PRICE + " tokens");
                    }
                }
                case H_REDEEM -> redeem();
                case H_COLL -> openInspect(allItems().get((int) h[1]));
                case H_INSPECT -> openInspect(Crates.CRATES.get(sel).items.get((int) h[1]));
                case H_HIST -> {
                    var d = Crates.history().get((int) h[1]);
                    if (Crates.owns(d.knife())) openInspect(new Item(d.knife(), Crates.rarityOf(d.knife())));
                }
                case H_MULTI -> {
                    Crates.setMulti((int) h[1]);
                    snd(SoundEvents.UI_BUTTON_CLICK, 1.3f + 0.1f * (int) h[1], 0.45f);
                }
                case H_AUTO -> {
                    Crates.setAuto(!Crates.auto());
                    snd(SoundEvents.UI_BUTTON_CLICK, Crates.auto() ? 1.6f : 1.1f, 0.5f);
                }
                case H_QUICK -> {
                    Crates.setQuick(!Crates.quick());
                    snd(SoundEvents.UI_BUTTON_CLICK, Crates.quick() ? 1.6f : 1.1f, 0.5f);
                }
                case H_CLOSE -> onClose();
                case H_FIELD -> {
                    setFocused(codeBox);
                    return super.mouseClicked(event, doubleClick);
                }
                default -> {
                }
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (inspect != null || opening != null) return true;
        if (tab == T_COLL) {
            collScroll -= (float) sy * 40f;
            return true;
        }
        if (tab == T_HIST) {
            histScroll -= (float) sy * 34f;
            return true;
        }
        if (tab == T_CRATES) {
            setSel(Math.floorMod(sel - (int) Math.signum(sy), Crates.CRATES.size()));
            return true;
        }
        return super.mouseScrolled(x, y, sx, sy);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (opening != null) {
            if (key == 256) opening.escape();
            else if (key == 32 || key == 257) opening.skip();
            return true;
        }
        if (inspect != null) {
            if (key == 256 || key == 257) inspect = null;
            return true;
        }
        if (key == 256) {
            onClose();
            return true;
        }
        if (tab == T_REDEEM && (key == 257 || key == 335)) {
            redeem();
            return true;
        }
        if (tab == T_CRATES && codeBox != null && !codeBox.isFocused()) {
            if (key == 264) setSel((sel + 1) % Crates.CRATES.size());
            else if (key == 265) setSel(Math.floorMod(sel - 1, Crates.CRATES.size()));
            else if (key == 257 || key == 32) startOpening(Crates.CRATES.get(sel));
        }
        // number keys pick a tab
        if (key >= 49 && key < 49 + TABS.length && (codeBox == null || !codeBox.isFocused())) {
            setTab(key - 49);
            return true;
        }
        return super.keyPressed(event);
    }

    private void redeem() {
        Crates.Redeem r = Crates.redeem(codeBox.getValue());
        codeMsg = r.message();
        codeOk = r.ok();
        codeAt = com.zlorbie788.zeus.core.Clock.millis();
        if (r.ok()) {
            codeBox.setValue("");
            snd(SoundEvents.PLAYER_LEVELUP, 1.4f, 0.6f);
        } else {
            snd(SoundEvents.NOTE_BLOCK_BASS, 0.6f, 0.6f);
        }
    }

    private void say(String msg) {
        flash = msg;
        flashAt = com.zlorbie788.zeus.core.Clock.millis();
    }

    private void equip(String id) {
        int hd = Crates.hdOf(id);
        if (hd < 0 || !Crates.owns(id)) return;
        KnifeSwing.equipHd(hd);
        snd(SoundEvents.ARMOR_EQUIP_IRON, 1.2f, 0.7f);
        say("Now holding " + nameOf(id).replace(" · ", " "));
    }

    private void announce(Crates.Result r) {
        try {
            if (r.item().rarity().ordinal() < Rarity.DIVINE.ordinal() || minecraft.gui == null) return;
            int col = r.item().rarity().tint() & 0xFFFFFF;
            net.minecraft.network.chat.MutableComponent m = Component.literal("[Zeus Crates] ").withColor(0xF5C04A)
                    .append(Component.literal("You unboxed ").withColor(0xE9F1FF))
                    .append(Component.literal(nameOf(r.item().id()).replace(" · ", " | ")).withColor(col))
                    .append(Component.literal(" (" + r.item().rarity().label + ")").withColor(col));
            minecraft.gui.getChat().addMessage(m);
        } catch (RuntimeException ignored) {
        }
    }

    private void startOpening(Crate c) {
        int n = Math.min(Crates.multi(), Crates.canOpenCount());
        if (n <= 0) return;
        List<Crates.Result> res = Crates.open(c, n);
        if (res.isEmpty()) return;
        // the reveal shows the winners big: start reading their large sheets now, seconds before they are needed
        for (Crates.Result r : res) KnifeHDGui.prefetch(Crates.hdOf(r.item().id()), true);
        opening = new Opening(c, res, Math.round(lastCrateCx), Math.round(lastCrateCy), Math.round(lastCrateSize));
        hideCodeBox();
        snd(SoundEvents.CHEST_LOCKED, 1.3f, 0.7f);
    }

    private static int hue(float h) {
        h = h - (float) Math.floor(h);
        return 0xFF000000 | (java.awt.Color.HSBtoRGB(h, 0.58f, 1f) & 0xFFFFFF);
    }

    // ------------------------------------------------------------------ the opening sequence
    private final class Opening {
        // timeline (seconds); Quick open shortens every step
        final boolean fast = Crates.quick();
        final float FLY = fast ? 0.22f : 0.45f;       // crate flies from the panel to the middle
        final float RATTLE = fast ? 0.18f : 0.42f;    // shakes harder and harder
        final float BURST = 0.34f;                     // latches let go and the lid swings open
        final float SPIN0 = FLY + RATTLE + 0.05f;
        final float SPIN = fast ? 2.4f : 5.6f;        // reel runs and slows
        final float SETTLE = fast ? 0.25f : 0.40f;    // overshoot ticks back onto the winner
        final float HOLD = fast ? 0.30f : 0.55f;      // winner lifts, light beam
        final float MORPH = 0.50f;                     // knife flies out of the card into the reveal
        static final float FADE = 0.25f;               // the dark backdrop fades in over this long

        final float STAGGER = fast ? 0.20f : 0.38f;  // with several crates, each reel stops a little after the one above
        final float AUTO_WAIT = fast ? 1.6f : 2.6f;

        final Crate crate;
        final List<Crates.Result> results;
        final Crates.Result res;
        final int n;
        final List<List<Item>> reels = new ArrayList<>();
        final List<Item> reel;
        final int win;
        final float[] lands, overs;
        final float land;
        final float over;
        final int[] lastCards;
        final boolean[] equipped;
        final boolean[] landRung;
        boolean autoStopped;
        final float fromX, fromY, fromSize;
        final long start = com.zlorbie788.zeus.core.Clock.nanoTime();
        float skipBy;
        int lastCard = Integer.MIN_VALUE;
        long lastTick;
        boolean burstSound, revealSound, latchSound;
        final boolean chroma;
        int chimes;
        final float[][] sparks = new float[90][6];
        final float[][] confetti;

        Opening(Crate c, List<Crates.Result> rs, int fromX, int fromY, int fromSize) {
            crate = c;
            results = new ArrayList<>(rs);
            n = results.size();
            Crates.Result best = results.get(0);
            for (Crates.Result r0 : results) if (r0.item().rarity().ordinal() > best.item().rarity().ordinal()) best = r0;
            res = best;
            Crates.Result r = best;
            this.fromX = fromX;
            this.fromY = fromY;
            this.fromSize = Math.max(40, fromSize);
            boolean anyChroma = false;
            for (Crates.Result r0 : results) anyChroma |= r0.item().rarity() == Rarity.CHROMA;
            chroma = anyChroma;
            Random rnd = new Random();
            win = 56;
            lands = new float[n];
            overs = new float[n];
            lastCards = new int[n];
            equipped = new boolean[n];
            landRung = new boolean[n];
            for (int k = 0; k < n; k++) {
                List<Item> rl = new ArrayList<>();
                for (int i = 0; i < 64; i++) rl.add(Crates.roll(c));
                rl.set(win, results.get(k).item());
                reels.add(rl);
                lands[k] = (rnd.nextFloat() - 0.5f) * 0.70f;
                overs[k] = 0.14f + rnd.nextFloat() * 0.16f;
                lastCards[k] = Integer.MIN_VALUE;
            }
            reel = reels.get(0);
            land = lands[0];
            over = overs[0];
            for (float[] sp : sparks) {
                double a = rnd.nextDouble() * Math.PI * 2;
                float v = 70 + rnd.nextFloat() * 260;
                sp[0] = (float) Math.cos(a) * v;
                sp[1] = (float) Math.sin(a) * v - 50;
                sp[2] = 1 + rnd.nextFloat() * 2.2f;
                sp[3] = 0.6f + rnd.nextFloat() * 1.0f;
                sp[4] = rnd.nextFloat();
                sp[5] = rnd.nextFloat() * 6.28f;
            }
            boolean gold = r.item().rarity().ordinal() >= Rarity.OLYMPIAN.ordinal();
            confetti = new float[chroma ? 130 : gold ? 70 : 0][6];
            for (float[] q : confetti) {
                q[0] = rnd.nextFloat();
                q[1] = rnd.nextFloat() * 1.5f;
                q[2] = 30 + rnd.nextFloat() * 50;
                q[3] = rnd.nextFloat() * 6.28f;
                q[4] = 2 + rnd.nextFloat() * 3;
                q[5] = rnd.nextFloat();
            }
        }

        float t() {
            return (com.zlorbie788.zeus.core.Clock.nanoTime() - start) / 1e9f + skipBy;
        }

        boolean covers() {
            return t() >= FADE;
        }

        float landedAt(int k) {
            return SPIN0 + SPIN + k * STAGGER;
        }

        float landedAt() {
            return landedAt(n - 1);
        }

        float revealAt() {
            return landedAt(n - 1) + SETTLE + HOLD;
        }

        void skip() {
            float t = t();
            if (t < revealAt()) skipBy += revealAt() - t;
        }

        void escape() {
            if (t() < revealAt()) skip();
            else close();
        }

        void close() {
            opening = null;
            openedAt = now();
        }

        float pos(float t, int r) {
            float sp = t - SPIN0;
            if (sp <= 0) return 0;
            float dur = SPIN + r * STAGGER;
            float target = win + lands[r];
            if (sp < dur) {
                float k = sp / dur;
                return (target + overs[r]) * (1f - (float) Math.pow(1f - k, 4.4f));
            }
            float b = inOutCubic((sp - dur) / SETTLE);
            return target + overs[r] * (1f - b);
        }

        float pos(float t) {
            return pos(t, 0);
        }

        float vel(float t, int r) {
            return (pos(t + 0.02f, r) - pos(t - 0.02f, r)) / 0.04f;
        }

        float vel(float t) {
            return vel(t, 0);
        }

        boolean autoOn() {
            return Crates.auto() && !autoStopped;
        }

        void click(float mx, float my) {
            float t = t();
            if (t < revealAt()) {
                skip();
                return;
            }
            for (int i = hits.size() - 1; i >= 0; i--) {
                float[] h = hits.get(i);
                if (!in(mx, my, h[2], h[3], h[4], h[5])) continue;
                if (h[0] == H_EQUIP && n > 1) {
                    // several knives: equip the one picked and stay, to look at the others
                    int k = (int) h[1];
                    equip(results.get(k).item().id());
                    java.util.Arrays.fill(equipped, false);
                    equipped[k] = true;
                } else if (h[0] == H_EQUIP) {
                    equip(res.item().id());
                    close();
                } else if (h[0] == H_STOP) {
                    autoStopped = true;
                    Crates.setAuto(false);
                    snd(SoundEvents.UI_BUTTON_CLICK, 1.0f, 0.5f);
                } else if (h[0] == H_AGAIN) {
                    close();
                    startOpening(crate);
                } else if (h[0] == H_DONE) {
                    close();
                }
                return;
            }
        }

        // reel geometry shared by the reel and the morph into the reveal, in units
        float cw() {
            return Math.max(92, Math.min(128, W / 7.5f));
        }

        float ch() {
            return Math.round(cw() * 1.22f);
        }

        void render(GuiGraphics g, float clock, float dt) {
            float t = t();
            hits.clear();
            float dim = clamp01(t / FADE);
            // fully opaque once faded in, so nothing behind needs drawing
            fill(g, 0, 0, W, H, dim >= 1f ? 0xFF020408 : Draw.rgba(0xF2020408, dim));
            if (t < SPIN0 + 0.35f) intro(g, t, clock);
            if (t >= SPIN0 - 0.15f && t < revealAt() + 0.4f) reelView(g, t, clock);
            if (t >= revealAt()) {
                float u = t - revealAt();
                if (n == 1) reveal(g, u, clock, dt);
                else revealMany(g, u, clock, dt);
                autoNext(g, u);
            } else {
                textC(g, "Click or press space to skip", W / 2f, H - 16, F_SMALL, Ty.MED, 0xA05E73A0);
            }
        }

        void autoNext(GuiGraphics g, float u) {
            if (!Crates.auto() || autoStopped) return;
            int can = Crates.canOpenCount();
            if (can <= 0) {
                autoStopped = true;
                say("Auto open stopped: out of keys");
                return;
            }
            float f = clamp01((u - 0.9f) / (AUTO_WAIT - 0.9f));
            if (f > 0f) {
                float bw = 160, by = H - 22;
                rect(g, W / 2f - bw / 2f, by, bw, 3, 1.5f, 0xFF161F38);
                rect(g, W / 2f - bw / 2f, by, bw * f, 3, 1.5f, GOLD);
                textC(g, "Next crate in a moment  ·  click Stop to stay here", W / 2f, by - 8, F_TINY + 0.3f, Ty.MED, SUB);
            }
            if (u >= AUTO_WAIT) {
                close();
                startOpening(crate);
            }
        }

        void intro(GuiGraphics g, float t, float clock) {
            float cx = W / 2f, cy = H / 2f;
            float fly = inOutCubic(t / FLY);
            float x = fromX + (cx - fromX) * fly, y = fromY + (cy - fromY) * fly;
            float size = fromSize + (Math.min(170, H / 2.2f) - fromSize) * fly;
            float rattle = clamp01((t - FLY) / RATTLE);
            float shake = rattle * rattle * 6f;
            float lid = clamp01((t - FLY - RATTLE) / BURST);
            float fade = 1f - clamp01((t - SPIN0) / 0.35f);
            if (rattle > 0 && !burstSound && lid <= 0) {
                // a rattle of clicks that speeds up
                int n = (int) (rattle * rattle * 10);
                if (n > lastCard) {
                    lastCard = n;
                    snd(SoundEvents.CHEST_LOCKED, 1.2f + n * 0.05f, 0.25f + rattle * 0.3f);
                }
            }
            if (!latchSound && rattle >= 1f) {
                latchSound = true;
                snd(SoundEvents.IRON_TRAPDOOR_OPEN, 1.75f, 0.45f);
                snd(SoundEvents.CHAIN_BREAK, 1.6f, 0.4f);
            }
            if (!burstSound && lid > 0.05f) {
                burstSound = true;
                lastCard = Integer.MIN_VALUE;
                snd(SoundEvents.ENDER_CHEST_OPEN, 1.1f, 0.8f);
                snd(SoundEvents.FIREWORK_ROCKET_BLAST, 1.3f, 0.5f);
            }
            if (fade <= 0f) return;
            glow(g, x - size / 3f, y - size / 4f, size * 2 / 3f, size / 2f, 30, crate.colour, Math.max(20, size / 2f),
                    Math.min(1f, (0.02f + 0.05f * rattle + 0.1f * lid) * fade * Math.max(20, size / 2f) / 3f));
            crate(g, crate, x, y, size, clock, lid, lid > 0 ? 0f : shake, rattle * 0.5f + lid);
            if (lid > 0f) {
                // shockwave ring and a flash of the crate's colour
                float since = t - FLY - RATTLE;
                float rr = 10 + since * size * 3.2f;
                float ra = 1f - clamp01(since / 0.6f);
                ring(g, x, y, rr, Draw.rgba(Draw.lighten(crate.colour, 0.4f), 0.7f * ra));
                fill(g, 0, 0, W, H, Draw.rgba(Draw.lighten(crate.colour, 0.6f), 0.30f * (1f - lid) * lid * 4f * fade));
            }
        }

        void ring(GuiGraphics g, float cx, float cy, float r, int colour) {
            r = Math.max(2, r);
            int seg = Math.max(24, Math.min(120, Math.round(r * s / 7f)));
            float step = (float) (Math.PI * 2 / seg);
            for (int i = 0; i < seg; i++) {
                float a0 = i * step, a1 = (i + 1) * step + 0.004f;
                line(g, cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r, cx + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r,
                        1.6f, colour);
            }
        }

        void reelView(GuiGraphics g, float t, float clock) {
            // one reel per crate, stacked; with more reels each is a little smaller
            float k = n == 1 ? 1f : n == 2 ? 0.80f : 0.64f;
            float cw = cw() * k, ch = Math.round(ch() * k), gap = 8 * k;
            float step = cw + gap;
            float pad = n == 1 ? 18 : 10, rowGap = 14;
            float railH = ch + 2 * pad;
            float total = n * railH + (n - 1) * rowGap;
            float top = H / 2f - total / 2f;
            float slide = outCubic((t - (SPIN0 - 0.15f)) / 0.4f);
            float out = clamp01((t - revealAt()) / 0.3f);
            float a = slide * (1f - out);
            if (a <= 0.01f) return;
            float slideX = (1f - slide) * W * 0.5f;
            // a click as each card passes the marker (one reel's worth, so three reels do not triple it)
            for (int r = 0; r < n; r++) {
                int under = (int) Math.floor(pos(t, r) + 0.5f);
                if (under != lastCards[r]) {
                    long now = com.zlorbie788.zeus.core.Clock.nanoTime();
                    if (lastCards[r] != Integer.MIN_VALUE && now - lastTick > 26_000_000L && t < landedAt(r) + SETTLE + 0.05f) {
                        float speed = Math.abs(vel(t, r));
                        snd(SoundEvents.UI_BUTTON_CLICK, 1.45f + Math.min(0.55f, speed / 55f), 0.20f + Math.min(0.15f, speed / 200f));
                        lastTick = now;
                    }
                    lastCards[r] = under;
                }
            }
            textC(g, n == 1 ? crate.name : n + " × " + crate.name, W / 2f, top - 14, F_TITLE + 1, Ty.XBOLD, Draw.rgba(Draw.lighten(crate.colour, 0.15f), a));
            for (int r = 0; r < n; r++) {
                List<Item> rl = reels.get(r);
                float railY = top + r * (railH + rowGap), cy = railY + pad;
                float pos = pos(t, r), speed = Math.abs(vel(t, r));
                boolean landed = t >= landedAt(r) + SETTLE;
                float hold = clamp01((t - (landedAt(r) + SETTLE)) / HOLD);
                rect(g, 0, railY, W, railH, 0, Draw.rgba(0xF00C1222, a), Draw.rgba(0xF0070A14, a));
                fill(g, 0, railY, W, 1f / s, Draw.rgba(crate.colour, 0.6f * a));
                fill(g, 0, railY + railH - 1f / s, W, 1f / s, Draw.rgba(crate.colour, 0.6f * a));
                scissor(g, 0, railY, W, railH);
                int first = (int) Math.floor(pos - (W / 2f) / step) - 1;
                cardsBegin();
                for (int i = Math.max(0, first); i < Math.min(rl.size(), first + (int) (W / step) + 4); i++) {
                    if (landed && i == win) continue;          // drawn on top, outside the clip
                    float fx = W / 2f + (i - pos) * step + slideX;
                    // cards near the marker grow a little and brighten
                    float near = Math.max(0f, 1f - Math.abs(i - pos));
                    float sc = 1f + 0.06f * near * (speed < 18 ? 1f : 0.4f);
                    float dimK = landed ? 1f - 0.7f * outCubic(hold * 2f) : 1f;
                    float ccy = cy + ch / 2f + (landed ? outCubic(hold * 1.5f) * 8f * k : 0f);
                    Card cd = cardAdd().set(rl.get(i), fx - cw * sc / 2f, ccy - ch * sc / 2f, cw * sc, ch * sc, false, false, 0f, false);
                    cd.sc = sc;
                    cd.veil = 1f - Math.min(1f, a * dimK);
                    cd.veilColour = 0xFF04060C;
                }
                cardsDraw(g, 0f);
                // speed streaks and soft dark edges
                if (speed > 8) {
                    float kk = Math.min(1f, (speed - 8) / 30f);
                    Random rr = new Random((long) (pos * 3) + r * 7919L);
                    for (int sI = 0; sI < 16 / n + 4; sI++) {
                        float yy = cy + rr.nextInt(Math.max(1, (int) ch));
                        float len = 40 + rr.nextInt(140);
                        float xx = rr.nextInt(Math.max(1, (int) W));
                        fill(g, xx, yy, len, 1f / s, Draw.rgba(0xFFFFFFFF, 0.10f * kk * a));
                    }
                }
                float ew = Math.max(1, W / 40f);
                for (int e = 0; e < 10; e++) {
                    float ek = (1f - e / 10f);
                    int col = Draw.rgba(0xFF04060C, 0.10f * ek * a);
                    fill(g, e * ew, railY, ew, railH, col);
                    fill(g, W - (e + 1) * ew, railY, ew, railH, col);
                }
                g.disableScissor();
            }
            // the marker: one gold line through every reel, with a pointer at each end
            float mxl = W / 2f, mTop = top - 6, mBot = top + total + 6;
            boolean allLanded = t >= landedAt() + SETTLE;
            float holdAll = clamp01((t - (landedAt() + SETTLE)) / HOLD);
            glow(g, mxl - 0.5f, mTop, 1f, mBot - mTop, 0, GOLD, 8, 0.6f * a);
            fill(g, mxl - 0.6f, mTop, 1.2f, mBot - mTop, Draw.rgba(GOLD, a * (1f - 0.8f * (allLanded ? holdAll : 0f))));
            for (int kk = 0; kk < 6; kk++) {
                fill(g, mxl - 6 + kk, mTop - 3 + kk, 12 - 2 * kk, 1, Draw.rgba(GOLD, a));
                fill(g, mxl - 6 + kk, mBot + 2 - kk, 12 - 2 * kk, 1, Draw.rgba(GOLD, a));
            }
            // each winner lifts out of its reel with a beam of its rarity's light
            for (int r = 0; r < n; r++) {
                if (t < landedAt(r) + SETTLE) continue;
                float hold = clamp01((t - (landedAt(r) + SETTLE)) / HOLD);
                Rarity rar = results.get(r).item().rarity();
                if (!landRung[r]) {
                    landRung[r] = true;
                    snd(SoundEvents.NOTE_BLOCK_CHIME, 1.6f + 0.12f * r, 0.7f);
                    if (rar.ordinal() >= Rarity.DIVINE.ordinal()) snd(SoundEvents.BEACON_ACTIVATE, 1.6f, 0.5f);
                }
                float railY = top + r * (railH + rowGap), cy = railY + pad;
                float fx = W / 2f + (win - pos(t, r)) * step - cw / 2f;
                float lift = outBack(hold * 1.3f);
                float sc = 1f + 0.22f * lift * (n == 1 ? 1f : 0.6f);
                int col = rar.tint();
                float bw = cw * (0.3f + 0.9f * outCubic(hold));
                if (n == 1) {
                    glow(g, fx + cw / 2f - bw / 2f, 0, bw, H, 4, col, 26, 0.30f * outCubic(hold * 2f) * a);
                    fill(g, fx + cw / 2f - 0.6f, 0, 1.2f, H, Draw.rgba(Draw.lighten(col, 0.5f), 0.5f * outCubic(hold * 2f) * a));
                } else {
                    glow(g, fx + cw / 2f - bw / 2f, railY, bw, railH, 4, col, 18, 0.30f * outCubic(hold * 2f) * a);
                }
                g.pose().pushMatrix();
                g.pose().translate((fx + cw / 2f) * s, (cy + ch / 2f - lift * 10f * k) * s);
                g.pose().scale(sc, sc);
                g.pose().translate(-cw / 2f * s, -ch / 2f * s);
                float pulse = 0.5f + 0.5f * (float) Math.sin(clock * 10f);
                glow(g, 0, 0, cw, ch, 7, col, 16, (0.6f + 0.5f * pulse) * a);
                card(g, reels.get(r).get(win), 0, 0, cw, ch, false, false, 0f, 0f, false);
                g.pose().popMatrix();
            }
        }

        void revealMany(GuiGraphics g, float u, float clock, float dt) {
            Rarity rar = res.item().rarity();
            int col = rar.tint();
            boolean big = rar.ordinal() >= Rarity.TITAN.ordinal();
            if (!revealSound) {
                revealSound = true;
                for (Crates.Result r : results) announce(r);
                if (chroma) {
                    snd(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.15f, 0.9f);
                    snd(SoundEvents.AMETHYST_CLUSTER_BREAK, 1.4f, 0.9f);
                } else if (big) {
                    snd(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f);
                } else if (rar.ordinal() >= Rarity.DIVINE.ordinal()) {
                    snd(SoundEvents.PLAYER_LEVELUP, 1.1f, 0.8f);
                    snd(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1f);
                } else {
                    snd(SoundEvents.PLAYER_LEVELUP, 1.4f, 0.6f);
                }
            }
            float e = outCubic(u / 0.45f);
            float cx = W / 2f, cy = H / 2f - 20;
            if (u < 0.25f) fill(g, 0, 0, W, H, Draw.rgba(0xFFFFFFFF, (chroma ? 0.6f : 0.4f) * (1f - u / 0.25f)));
            // the rarest drop sets the light: its colour, turning rays, a ring
            float rad = Math.min(W, H) * 0.5f;
            glow(g, cx - 120, cy - 40, 240, 80, 40, col, rad, 0.22f * e);
            float len = Math.max(W, H) * 0.75f;
            int rays = 24;
            for (int layer = 0; layer < (chroma ? 2 : 1); layer++) {
                float rot = (layer == 0 ? 1 : -0.6f) * clock * 0.22f;
                for (int i = 0; i < rays; i++) {
                    double ang = rot + (i + layer * 0.33) * Math.PI * 2 / rays;
                    float alpha = (i % 2 == 0 ? 0.10f : 0.05f) * e;
                    int rc = chroma ? hue(i / (float) rays + clock * 0.08f) : col;
                    for (int kk = -1; kk <= 1; kk++) {
                        double aa = ang + kk * 0.02;
                        line(g, cx, cy, cx + (float) Math.cos(aa) * len, cy + (float) Math.sin(aa) * len, 1.6f, Draw.rgba(rc, alpha * (1f - Math.abs(kk) * 0.3f)));
                    }
                }
            }
            if (u < 0.8f) ring(g, cx, cy, 20 + u * Math.max(W, H) * 0.6f, Draw.rgba(Draw.lighten(col, 0.4f), 0.6f * (1f - u / 0.8f)));
            // a column per knife
            float colW = Math.min(W * 0.28f, 250), gap = 16;
            float colH = Math.min(H * 0.62f, 300);
            float total = n * colW + (n - 1) * gap;
            float x0 = cx - total / 2f, top = cy - colH / 2f;
            for (int i = 0; i < n; i++) {
                Crates.Result r = results.get(i);
                Rarity ri = r.item().rarity();
                int rc = ri.tint();
                float ui = u - i * 0.12f;
                float pe = outBack(ui / 0.45f);
                if (pe <= 0f) continue;
                float x = x0 + i * (colW + gap), y = top + (1f - outCubic(ui / 0.4f)) * 18f;
                g.pose().pushMatrix();
                g.pose().translate((x + colW / 2f) * s, (y + colH / 2f) * s);
                g.pose().scale(0.9f + 0.1f * pe, 0.9f + 0.1f * pe);
                g.pose().translate(-(x + colW / 2f) * s, -(y + colH / 2f) * s);
                float ca = clamp01(ui / 0.25f);
                if (ri.ordinal() >= Rarity.TITAN.ordinal()) glow(g, x, y, colW, colH, 14, rc, 14, (0.25f + 0.1f * (float) Math.sin(clock * 3 + i)) * ca);
                rect(g, x, y, colW, colH, 14, Draw.rgba(Draw.mix(0xFF0E1426, rc, 0.10f), ca), Draw.rgba(Draw.mix(0xFF080B16, rc, 0.20f), ca));
                stroke(g, x, y, colW, colH, 14, Draw.rgba(Draw.mix(BORDER_HI, rc, 0.6f), ca));
                if (ri == Rarity.CHROMA) sheen(g, x, y, colW, colH, 0x40FFFFFF, (clock * 0.45f) % 1.8f);
                String rl = ri.label.toUpperCase(Locale.ROOT);
                float cwid = tw(rl, F_SMALL, Ty.XBOLD) + 18;
                rect(g, x + colW / 2f - cwid / 2f, y + 10, cwid, 15, 7.5f, Draw.rgba(rc, ca));
                textC(g, rl, x + colW / 2f, y + 17.5f, F_SMALL, Ty.XBOLD, Draw.rgba(0xFF0A0A12, ca));
                float kh = colH * 0.50f;
                float yaw = -0.35f - (1f - outCubic(ui / 0.9f)) * 3.14159f + 0.4f * (float) Math.sin(ui * 1.1f + i) * clamp01((ui - 0.5f) / 0.6f);
                knifeSpin(g, Crates.hdOf(r.item().id()), x + 8, y + 32, colW - 16, kh, yaw, 0.08f * (float) Math.sin(ui * 0.8f + i));
                String full = nameOf(r.item().id());
                float ty = y + 32 + kh + 8;
                textC(g, typeName(full).toUpperCase(Locale.ROOT), x + colW / 2f, ty, F_TINY + 0.3f, Ty.BOLD, Draw.rgba(SUB, ca));
                textC(g, fit(shortName(full), colW - 16, F_H + 3, Ty.XBOLD), x + colW / 2f, ty + 14, F_H + 3, Ty.XBOLD, Draw.rgba(TEXT, ca));
                String badge = r.duplicate() ? "Duplicate  ·  +" + r.refund() + " token" + (r.refund() == 1 ? "" : "s") : "New knife!";
                textC(g, badge, x + colW / 2f, ty + 29, F_SMALL, Ty.BOLD, Draw.rgba(r.duplicate() ? SUB : GREEN, ca));
                g.pose().popMatrix();
                float bt = outCubic((ui - 0.7f) / 0.3f);
                if (bt > 0f) {
                    float bw = colW - 40, bh = 22;
                    float by = y + colH - bh - 12 + (1f - bt) * 8f;
                    button(g, H_EQUIP, x + 20, by, bw, bh, equipped[i] ? "In hand" : "Equip", null, rc, !equipped[i], !equipped[i], clock, dt);
                    // the shared button helper registers index 0: point this one at its knife
                    if (!equipped[i] && !hits.isEmpty() && hits.get(hits.size() - 1)[0] == H_EQUIP) hits.get(hits.size() - 1)[1] = i;
                }
            }
            // open again, or stop auto, and done
            boolean again = Crates.canOpen(crate);
            float w = 130, bgap = 8, bh3 = 28;
            int nb = again ? 2 : 1;
            float x = cx - (nb * w + (nb - 1) * bgap) / 2f;
            float y = Math.min(H - bh3 - 30, top + colH + 18);
            float bt = outCubic((u - 1.0f) / 0.3f);
            if (bt > 0f) {
                float yy = y + (1f - bt) * 14f;
                if (again) {
                    if (autoOn()) button(g, H_STOP, x, yy, w, bh3, "Stop auto", null, RED, true, false, clock, dt);
                    else {
                        int m = Math.min(Crates.multi(), Crates.canOpenCount());
                        button(g, H_AGAIN, x, yy, w, bh3, m > 1 ? "Open " + m + " more" : "Open another", null, crate.colour, true, false, clock, dt);
                    }
                    x += w + bgap;
                }
                button(g, H_DONE, x, yy, w, bh3, "Done", null, 0xFF5E73A0, true, false, clock, dt);
            }
        }

        void reveal(GuiGraphics g, float u, float clock, float dt) {
            Rarity rar = res.item().rarity();
            int col = rar.tint();
            boolean big = rar.ordinal() >= Rarity.TITAN.ordinal();
            if (!revealSound) announce(res);
            if (chroma) {
                // a rising bell chord, one note at a time, then a shimmer
                float[] notes = {0.84f, 1.0f, 1.26f, 1.5f, 1.68f, 2.0f};
                while (chimes < notes.length && u >= chimes * 0.085f) {
                    snd(SoundEvents.NOTE_BLOCK_BELL, notes[chimes], 0.55f);
                    if (chimes % 2 == 0) snd(SoundEvents.AMETHYST_BLOCK_CHIME, notes[chimes], 0.8f);
                    chimes++;
                }
                if (chimes == notes.length && u > 0.62f) {
                    chimes++;
                    snd(SoundEvents.AMETHYST_CLUSTER_BREAK, 1.4f, 0.9f);
                    snd(SoundEvents.BEACON_POWER_SELECT, 1.5f, 0.6f);
                }
            }
            if (!revealSound) {
                revealSound = true;
                if (chroma) {
                    snd(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.15f, 0.9f);
                    snd(SoundEvents.TOTEM_USE, 1.7f, 0.3f);
                } else if (big) {
                    snd(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f);
                    snd(SoundEvents.TOTEM_USE, 1.4f, 0.35f);
                } else if (rar.ordinal() >= Rarity.DIVINE.ordinal()) {
                    snd(SoundEvents.PLAYER_LEVELUP, 1.1f, 0.8f);
                    snd(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1f);
                } else {
                    snd(SoundEvents.PLAYER_LEVELUP, 1.4f, 0.6f);
                }
            }
            float below = 8 + 46 + 18 + 8 + 28;
            float bhMax = Math.max(70, Math.min(H * 0.42f, Math.min(230, H - below - 44)));
            float bwMax = Math.min(W * 0.6f, 420);
            float cx = W / 2f, cy = (H - (bhMax + below)) / 2f + bhMax / 2f + 10;
            // shake on the rarest drops
            float shk = big ? (1f - clamp01(u / 0.5f)) * 5f : 0f;
            g.pose().pushMatrix();
            g.pose().translate(shk * (float) Math.sin(u * 70) * s, shk * (float) Math.cos(u * 57) * s);
            float e = outCubic(u / 0.45f);
            if (chroma) {
                if (u < 0.45f) fill(g, 0, 0, W, H, Draw.rgba(0xFFFFFFFF, 0.75f * (1f - u / 0.45f)));
                // a rainbow light round the edges of the screen that keeps turning
                float ew = Math.max(8, W / 14f);
                for (int i = 0; i < 4; i++) {
                    int ec = hue(clock * 0.2f + i * 0.25f);
                    float ea = 0.10f * e * ew / 3f;
                    if (i == 0) glow(g, 0, -2, W, 2, 1, ec, ew, ea);
                    if (i == 1) glow(g, W, 0, 2, H, 1, ec, ew, ea);
                    if (i == 2) glow(g, 0, H, W, 2, 1, ec, ew, ea);
                    if (i == 3) glow(g, -2, 0, 2, H, 1, ec, ew, ea);
                }
            } else if (u < 0.25f) fill(g, 0, 0, W, H, Draw.rgba(0xFFFFFFFF, 0.45f * (1f - u / 0.25f)));
            // soft rarity light and turning rays (two layers on big drops, three for Chroma)
            float rad = Math.min(W, H) * 0.45f;
            glow(g, cx - 60, cy - 30, 120, 60, 30, col, rad, 0.25f * e);
            float len = Math.max(W, H) * 0.75f;
            int rays = 24;
            for (int layer = 0; layer < (chroma ? 3 : big ? 2 : 1); layer++) {
                float rot = (layer == 0 ? 1 : layer == 1 ? -0.6f : 0.35f) * clock * 0.22f;
                for (int i = 0; i < rays; i++) {
                    double ang = rot + (i + layer * 0.33) * Math.PI * 2 / rays;
                    float alpha = (i % 2 == 0 ? 0.12f : 0.06f) * e * (layer == 0 ? 1f : 0.6f);
                    int rc = chroma ? hue(i / (float) rays + layer * 0.17f + clock * 0.08f) : col;
                    for (int k = -2; k <= 2; k++) {
                        double aa = ang + k * 0.017;
                        line(g, cx, cy, cx + (float) Math.cos(aa) * len, cy + (float) Math.sin(aa) * len, 1.6f, Draw.rgba(rc, alpha * (1f - Math.abs(k) * 0.22f)));
                    }
                }
            }
            // shockwave rings
            for (int r = 0; r < (rar.ordinal() >= Rarity.DIVINE.ordinal() ? 2 : 1); r++) {
                float ru = u - r * 0.14f;
                if (ru > 0 && ru < 0.8f) ring(g, cx, cy, 20 + ru * Math.max(W, H) * 0.6f, Draw.rgba(Draw.lighten(col, 0.4f), 0.6f * (1f - ru / 0.8f)));
            }
            // sparks thrown out by the burst, then falling and twinkling
            for (float[] sp : sparks) {
                float life = u / sp[3];
                if (life >= 1f) continue;
                float x = cx + sp[0] * u, y = cy + sp[1] * u + 170f * u * u;
                float tw2 = 0.6f + 0.4f * (float) Math.sin(clock * 18 + sp[5]);
                float sz = Math.max(1, sp[2]);
                fill(g, x, y, sz, sz, Draw.rgba(chroma ? hue(sp[4] + clock * 0.3f) : sp[4] < 0.5f ? Draw.lighten(col, 0.5f) : 0xFFFFF4D0, (1f - life) * tw2));
            }
            // gold confetti for an Olympian, rainbow for a Chroma
            for (float[] q : confetti) {
                float fall = (u * 0.35f + q[1]) % 1.6f;
                float x = q[0] * W + (float) Math.sin(u * 2 + q[3]) * 14;
                float y = -20 + fall * (H + 40) / 1.5f;
                g.pose().pushMatrix();
                g.pose().translate(x * s, y * s);
                g.pose().rotate(u * 3 + q[3]);
                int w = Math.max(1, Math.round(q[4] * s)), h = Math.max(1, Math.round(q[4] * 0.5f * s * Math.abs((float) Math.cos(u * 6 + q[3]))));
                g.fill(-w, -h, w, h, chroma ? hue(q[5] + u * 0.15f) : q[5] < 0.5f ? 0xFFFFD66A : 0xFFFFF2C0);
                g.pose().popMatrix();
            }
            // the knife flies out of the winning card into the middle, turning as it comes
            float m = outCubic(u / MORPH);
            float cw = cw(), ch = ch();
            float fromW = cw * 1.22f - 6, fromH = ch * 1.22f - 26;
            float fromCx = W / 2f - land * (cw + 8), fromCy = H / 2f - 10;
            float bw = fromW + (bwMax - fromW) * m, bh = fromH + (bhMax - fromH) * m;
            float kx = fromCx + (cx - fromCx) * m, ky = fromCy + (cy - fromCy) * m;
            float settle = clamp01((u - MORPH) / 0.6f);
            float yaw = -0.35f - (1f - outCubic(u / 0.9f)) * 3.14159f + 0.5f * (float) Math.sin(u * 1.1f) * settle;
            float tilt = 0.10f * (float) Math.sin(u * 0.8f) * settle;
            float bob = 4f * (float) Math.sin(u * 2f) * settle;
            knifeSpin(g, Crates.hdOf(res.item().id()), kx - bw / 2f, ky - bh / 2f + bob, bw, bh, yaw, tilt);
            // the rarity chip pops in above
            String rl = rar.label.toUpperCase(Locale.ROOT);
            float ra = outBack((u - 0.35f) / 0.35f);
            if (ra > 0) {
                float rw = tw(rl, F_BODY, Ty.XBOLD) + 26, rh = 18;
                float chipY = Math.max(6, cy - bhMax / 2f - 22);
                g.pose().pushMatrix();
                g.pose().translate(cx * s, (chipY + rh / 2f) * s);
                g.pose().scale(ra, ra);
                g.pose().translate(-cx * s, -(chipY + rh / 2f) * s);
                if (chroma) {
                    glow(g, cx - rw / 2f, chipY, rw, rh, rh / 2f, col, 10, 0.6f);
                    rect(g, cx - rw / 2f, chipY, rw, rh, rh / 2f, 0xFF0A0A14);
                    stroke(g, cx - rw / 2f, chipY, rw, rh, rh / 2f, col);
                    float lx = cx - tw(rl, F_BODY, Ty.XBOLD) / 2f;
                    for (int i = 0; i < rl.length(); i++) {
                        String letter = rl.substring(i, i + 1);
                        text(g, letter, lx, chipY + rh / 2f, F_BODY, Ty.XBOLD, hue(clock * 0.5f - i * 0.11f));
                        lx += tw(letter, F_BODY, Ty.XBOLD);
                    }
                    sheen(g, cx - rw / 2f, chipY, rw, rh, 0x60FFFFFF, (clock * 0.6f) % 1.8f);
                } else {
                    glow(g, cx - rw / 2f, chipY, rw, rh, rh / 2f, col, 8, 0.25f);
                    rect(g, cx - rw / 2f, chipY, rw, rh, rh / 2f, Draw.lighten(col, 0.1f), Draw.darken(col, 0.15f));
                    textC(g, rl, cx, chipY + rh / 2f, F_BODY, Ty.XBOLD, 0xFF0A0A12);
                }
                g.pose().popMatrix();
            }
            // the name card slides up, the name types itself in, a shine runs across
            float ty = cy + bhMax / 2f + 8;
            String full = nameOf(res.item().id());
            float ta = outCubic((u - 0.30f) / 0.35f);
            float slide = (1f - ta) * 12f;
            String nm = shortName(full);
            float bw2 = Math.max(tw(nm, 19, Ty.XBOLD), 170) + 60, bh2 = 46;
            if (ta > 0) {
                rect(g, cx - bw2 / 2f, ty + slide, bw2, bh2, 12, Draw.rgba(Draw.mix(0xFF0C1122, col, 0.12f), ta), Draw.rgba(0xFF080B16, ta));
                stroke(g, cx - bw2 / 2f, ty + slide, bw2, bh2, 12, Draw.rgba(Draw.mix(BORDER_HI, col, 0.5f), ta));
                fill(g, cx - 18, ty + slide, 36, 1.4f, Draw.rgba(col, ta));
                textC(g, typeName(full).toUpperCase(Locale.ROOT), cx, ty + 13 + slide, F_SMALL, Ty.BOLD, Draw.rgba(SUB, ta));
                int shown = Math.min(nm.length(), (int) Math.ceil(nm.length() * clamp01((u - 0.4f) / 0.35f)));
                textC(g, nm.substring(0, shown), cx, ty + 30 + slide, 19, Ty.XBOLD, Draw.rgba(TEXT, ta));
                float sh = (u - 0.9f) / 0.7f;
                if (sh > 0 && sh < 1.6f) sheen(g, cx - bw2 / 2f, ty + slide, bw2, bh2, 0x50FFFFFF, sh);
            }
            // new or duplicate stamps in
            String badge = res.duplicate() ? "Duplicate  ·  +" + res.refund() + " token" + (res.refund() == 1 ? "" : "s") : "New knife!";
            int bc = res.duplicate() ? SUB : GREEN;
            float ba = clamp01((u - 0.75f) / 0.2f);
            if (ba > 0) {
                float bs = 1f + 0.6f * (1f - outCubic(ba));
                textC(g, badge, cx, ty + bh2 + 13, F_H * bs, Ty.BOLD, Draw.rgba(bc, ba));
            }
            g.pose().popMatrix();
            // buttons rise in one after another
            boolean again = Crates.canOpen(crate);
            float w = 120, gap = 8, bh3 = 28;
            int n = again ? 3 : 2;
            float x = cx - (n * w + (n - 1) * gap) / 2f;
            float y = Math.min(H - bh3 - 10, ty + bh2 + 28);
            for (int b = 0; b < n; b++) {
                float bt = outCubic((u - 0.9f - b * 0.07f) / 0.3f);
                if (bt > 0) {
                    float yy = y + (1f - bt) * 14f;
                    if (b == 0) button(g, H_EQUIP, x, yy, w, bh3, "Equip", null, col, true, true, clock, dt);
                    else if (again && b == 1 && autoOn()) button(g, H_STOP, x, yy, w, bh3, "Stop auto", null, RED, true, false, clock, dt);
                    else if (again && b == 1) button(g, H_AGAIN, x, yy, w, bh3, "Open another", null, crate.colour, true, false, clock, dt);
                    else button(g, H_DONE, x, yy, w, bh3, "Done", null, 0xFF5E73A0, true, false, clock, dt);
                }
                x += w + gap;
            }
        }
    }
}
