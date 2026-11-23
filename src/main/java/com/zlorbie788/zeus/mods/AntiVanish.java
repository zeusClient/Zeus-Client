package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import com.zlorbie788.zeus.render.EdgeArrow;
import com.zlorbie788.zeus.render.Projector;
import com.zlorbie788.zeus.render.Wire;
import com.zlorbie788.zeus.ui.Accent;
import com.zlorbie788.zeus.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AntiVanish extends Module {
    private static volatile AntiVanish self;
    private final Setting.Bool tab = add(new Setting.Bool("Body missing from the player list", "Flag a player you can see who is not in the tab list.", true));
    private final Setting.Bool digger = add(new Setting.Bool("Unseen digger", "Flag block-breaking by a player you cannot see.", true));
    private final Setting.Bool tabPacket = add(new Setting.Bool("Track tab-list removal packets", "Correlate a server tab removal with a player entity that remains visible nearby.", true));
    private final Setting.Num minConfidence = add(new Setting.Num("Minimum alert confidence", "Raise an alert only when the evidence score reaches this threshold.", 45, 25, 90, 5, " %"));
    private final Setting.Bool showEvidence = add(new Setting.Bool("Show evidence score", "Display the evidence confidence in the alert card.", true));
    private final Setting.Bool spec = add(new Setting.Bool("Spectators close to you", "Warn when a player in spectator mode is near you. The server sends them to your game like any player, you just do not see them.", true));
    private final Setting.Num specRange = add(new Setting.Num("Spectator range", "How close a spectator has to be.", 48, 8, 128, 4, " blocks")).showIf(() -> this.spec.on());
    private final Setting.Num near = add(new Setting.Num("Digger range", "Only breaking this close to you counts.", 24, 6, 64, 2, " blocks"));
    private final Setting.Num show = add(new Setting.Num("Show the card for", "How long the warning stays.", 6, 2, 20, 1, " s"));
    private final Setting.Bool chat = add(new Setting.Bool("Also a message at the top", "A short message as well as the card.", true));

    private record Sus(UUID id, String name, long since, long until, String why, int x, int y, int z, int confidence) {}

    private final Map<UUID, Long> pending = new HashMap<>();
    private final Map<UUID, Long> removedFromTab = new HashMap<>();
    private final Map<UUID, String> knownNames = new HashMap<>();
    private final Map<UUID, Long> alerted = new HashMap<>();
    private final Map<UUID, Integer> evidenceScores = new HashMap<>();
    private final Map<UUID, Long> evidenceTimes = new HashMap<>();
    private final Map<UUID, java.util.Set<String>> evidenceReasons = new HashMap<>();
    private static final Identifier ELDER =
            Identifier.fromNamespaceAndPath("minecraft", "textures/entity/guardian_elder.png");

    private Sus current;
    private int ticks;
    private long lastLeak;

    public AntiVanish() {
        super("antivanish", "Anti Vanish", "Warns you when a vanished player is close: \"NAME is watching you\".", Category.UTILITY);
        self = this;
    }

    public static void leak(int entityId, BlockPos pos) {
        AntiVanish a = self;
        if (a == null || !a.enabled() || !a.digger.on()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || entityId == mc.player.getId() || mc.level.getEntity(entityId) != null) return;
        if (mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > a.near.get() * a.near.get()) return;
        long now = System.currentTimeMillis();
        if (now - a.lastLeak < 4000) return;
        a.lastLeak = now;
        a.raise(null, "Unknown player", "block-breaking packet from an entity not present in your client world", pos.getX(), pos.getY(), pos.getZ(), 45);
    }

    public static void noteTabRemoval(Object packet) {
        AntiVanish a = self;
        if (a == null || !a.enabled() || !a.tabPacket.on() || packet == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        try {
            for (java.lang.reflect.Field field : packet.getClass().getDeclaredFields()) {
                if (!Iterable.class.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                Object value = field.get(packet);
                if (!(value instanceof Iterable<?> values)) continue;
                for (Object entry : values) if (entry instanceof UUID id) {
                    var info = mc.getConnection().getPlayerInfo(id);
                    if (info != null) a.knownNames.put(id, info.getProfile().name());
                    a.removedFromTab.put(id, System.currentTimeMillis());
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private boolean raise(UUID id, String name, String why, int x, int y, int z, int weight) {
        long now = System.currentTimeMillis();
        int confidence = weight;
        String evidence = why;
        if (id != null) {
            long previousAt = evidenceTimes.getOrDefault(id, 0L);
            if (now - previousAt > 20_000) { evidenceScores.remove(id); evidenceReasons.remove(id); }
            java.util.Set<String> reasons = evidenceReasons.computeIfAbsent(id, ignored -> new java.util.HashSet<>());
            if (reasons.add(why)) {
                int previous = evidenceScores.getOrDefault(id, 0);
                confidence = Math.min(100, previous == 0 ? weight : previous + Math.max(10, weight / 2));
                evidenceScores.put(id, confidence);
            } else confidence = evidenceScores.getOrDefault(id, weight);
            evidenceTimes.put(id, now);
            evidence = String.join("; ", reasons);
        }
        if (confidence < minConfidence.get()) return false;
        long ms = (long) (show.get() * 1000);
        current = new Sus(id, name, now, now + ms, evidence, x, y, z, confidence);
        if (chat.on()) Zeus.toast(name + " may be vanished (" + confidence + "% evidence)", false);
        return true;
    }

    @Override
    protected void onEnable() {
        pending.clear();
        removedFromTab.clear();
        knownNames.clear();
        alerted.clear(); evidenceScores.clear(); evidenceTimes.clear(); evidenceReasons.clear();
        current = null;
    }

    @Override
    protected void onDisable() {
        current = null;
    }

    @Override
    protected void onTick(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.getConnection() == null) return;
        if (++ticks % 10 != 0) return;
        long now = System.currentTimeMillis();
        if (tabPacket.on()) {
            for (var entry : new java.util.ArrayList<>(removedFromTab.entrySet())) {
                UUID id = entry.getKey();
                if (now - entry.getValue() < 2200) continue;
                AbstractClientPlayer body = find(mc, id);
                if (body != null && body.isAlive() && mc.getConnection().getPlayerInfo(id) == null) {
                    Long last = alerted.get(id);
                    if (last == null || now - last >= 60000) {
                        if (raise(id, knownNames.getOrDefault(id, body.getName().getString()),
                                "tab-removal packet while player entity remained loaded", body.getBlockX(), body.getBlockY(), body.getBlockZ(), 85)) alerted.put(id, now);
                    }
                }
                removedFromTab.remove(id);
            }
        }
        if (spec.on()) {
            double r2 = specRange.get() * specRange.get();
            for (AbstractClientPlayer p : mc.level.players()) {
                if (p == mc.player || !p.isSpectator() || mc.player.distanceToSqr(p) > r2) continue;
                UUID id = p.getUUID();
                Long last = alerted.get(id);
                if (last != null && now - last < 45000) continue;
                if (raise(id, p.getName().getString(), "spectator mode close to you (weak clue)", p.getBlockX(), p.getBlockY(), p.getBlockZ(), 35)) alerted.put(id, now);
            }
        }
        if (!tab.on()) return;
        for (AbstractClientPlayer p : mc.level.players()) {
            if (p == mc.player) continue;
            UUID id = p.getUUID();
            if (mc.getConnection().getPlayerInfo(id) != null) {
                pending.remove(id);
                continue;
            }
            Long first = pending.putIfAbsent(id, now);
            if (first == null || now - first < 1800) continue;
            Long last = alerted.get(id);
            if (last != null && now - last < 60000) continue;
            boolean packetProof = removedFromTab.containsKey(id);
            if (raise(id, p.getName().getString(), "visible entity missing from tab list", p.getBlockX(), p.getBlockY(), p.getBlockZ(), packetProof ? 85 : 55)) alerted.put(id, now);
        }
        pending.keySet().removeIf(id -> find(mc, id) == null);
        knownNames.keySet().removeIf(id -> find(mc, id) == null && !removedFromTab.containsKey(id));
        evidenceTimes.entrySet().removeIf(e -> now - e.getValue() > 60_000);
        evidenceScores.keySet().retainAll(evidenceTimes.keySet());
        evidenceReasons.keySet().retainAll(evidenceTimes.keySet());
    }

    @Override
    public String info() {
        return current != null && current.until > System.currentTimeMillis() ? "!" : null;
    }

    @Override
    protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
        Sus c = current;
        if (c == null) return;
        long now = System.currentTimeMillis();
        if (now > c.until) {
            current = null;
            return;
        }
        float fadeIn = Math.min(1f, (now - c.since) / 350f);
        float fadeOut = Math.min(1f, (c.until - now) / 900f);
        float breathe = 0.90f + 0.10f * (float) Math.sin(now / 210.0);
        float a = Math.min(fadeIn, fadeOut) * breathe;
        if (a <= 0.01f) return;

        int cx = w / 2;
        int cy = h / 2 + Math.round(Math.min(w, h) * 0.26f);
        int room = Math.max(140, w / 2);
        head(g, w, h, now - c.since, a);
        highlight(g, mc, c, partial, w, h, a);
        arrow(g, mc, c, partial, w, h, a);
        String line1 = Draw.fit(c.name + " is watching you", room);
        Draw.boldCenterS(g, line1, cx, cy, Draw.rgba(0xFFFFFFFF, a), 1.15f);
        String evidence = showEvidence.on() ? "Evidence confidence: " + c.confidence + "%" : c.why;
        Draw.textCenterS(g, Draw.fit(evidence, room), cx, cy + 20, Draw.rgba(0xFFFFB3BA, a), 0.9f);
        String where = "x " + c.x + "   y " + c.y + "   z " + c.z + "   \u00B7   "
                + Math.round(Math.sqrt(mc.player.distanceToSqr(c.x + 0.5, c.y, c.z + 0.5))) + " blocks away";
        Draw.textCenterS(g, where, cx, cy + 34, Draw.rgba(Accent.DIM, a), 0.85f);
    }

    /** The elder guardian head, drawn in the middle of the screen so it is always centred. */
    private static void head(GuiGraphics g, int w, int h, long age, float a) {
        float grow = Math.min(1f, Math.max(0f, age) / 260f);
        float pop = 0.70f + 0.30f * (1f - (1f - grow) * (1f - grow));
        int size = Math.max(24, Math.round(Math.min(w, h) * 0.16f * pop));
        int x = w / 2 - size / 2;
        int y = h / 2 - size / 2;
        int alpha = Math.max(0, Math.min(255, Math.round(255f * a)));
        Draw.glow(g, x, y, size, size, Math.max(6, size / 4), Draw.rgba(0xFFFF4D5E, 0.30f * a),
                Math.max(8, size / 3), 0.9f);
        try {
            g.blit(RenderPipelines.GUI_TEXTURED, ELDER, x, y, 16f, 16f, size, size, 12, 12, 64, 64,
                    (alpha << 24) | 0xFFFFFF);
        } catch (RuntimeException ignored) {
        }
    }

    /** A box on the vanished player himself, or on the spot the alert came from when there is no body. */
    private static void highlight(GuiGraphics g, Minecraft mc, Sus c, float partial, int w, int h, float a) {
        AbstractClientPlayer body = c.id == null ? null : find(mc, c.id);
        double x1, y1, z1, x2, y2, z2;
        if (body != null) {
            var b = body.getBoundingBox();
            x1 = b.minX; y1 = b.minY; z1 = b.minZ;
            x2 = b.maxX; y2 = b.maxY; z2 = b.maxZ;
        } else {
            x1 = c.x; y1 = c.y; z1 = c.z;
            x2 = c.x + 1.0; y2 = c.y + 2.0; z2 = c.z + 1.0;
        }
        if (!Projector.prepare(mc, partial, w, h)) return;
        Wire.box(g, x1, y1, z1, x2, y2, z2, 1.8f, Draw.rgba(0xFFFF4D5E, a), true);
    }

    /** A pointer at the edge of the screen while the player is out of view or behind you. */
    private static void arrow(GuiGraphics g, Minecraft mc, Sus c, float partial, int w, int h, float a) {
        AbstractClientPlayer body = c.id == null ? null : find(mc, c.id);
        double x = body != null ? body.getX() : c.x + 0.5;
        double y = body != null ? body.getY() + body.getBbHeight() * 0.5 : c.y + 1.0;
        double z = body != null ? body.getZ() : c.z + 0.5;
        if (!Projector.prepare(mc, partial, w, h)) return;
        EdgeArrow.draw(g, mc, w, h, x, y, z, 0xFFFF4D5E, a);
    }

    private static AbstractClientPlayer find(Minecraft mc, UUID id) {
        if (mc.level == null) return null;
        for (AbstractClientPlayer p : mc.level.players()) if (p.getUUID().equals(id)) return p;
        return null;
    }

    public static List<String> help() {
        return List.of("Correlates visible-but-unlisted entities, tab-removal packets, spectator state and unseen block-breaking packets.",
                "Scores are heuristic evidence, not proof. The client cannot detect players the server never sends.");
    }
}
