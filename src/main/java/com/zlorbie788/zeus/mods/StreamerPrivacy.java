package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.Optional;

public final class StreamerPrivacy extends Module {
    private static volatile StreamerPrivacy instance;
    private final Setting.Bool hideTab = add(new Setting.Bool("Hide player list", "Hides the on-screen tab/player list while this module is enabled.", true));
    private final Setting.Bool hideSidebar = add(new Setting.Bool("Hide scoreboard sidebar", "Hides the right-side scoreboard locally while this module is enabled.", true));

    private final Setting.Bool hideName = add(new Setting.Bool("Hide my name", "Replaces your own name wherever it could show: chat, the tab list and its header, name tags, the sidebar, titles and subtitles, the action bar, boss bars, and every panel, toast or message this client draws.", true));
    private final Setting.Text as = add(new Setting.Text("Show me as", "What your name is replaced with.", "Player", 16)).showIf(hideName::on);

    private static volatile String cachedMe;
    private static volatile long cachedAt;

    public StreamerPrivacy() {
        super("streamerprivacy", "Streamer Privacy", "Locally hides selected identity-bearing HUD panels for screenshots/streams. Your username remains visible to the server and other players.", Category.INTERFACE);
        instance = this;
    }

    public static boolean hideTabList() { return instance != null && instance.enabled() && instance.hideTab.on(); }
    public static boolean hideSidebar() { return instance != null && instance.enabled() && instance.hideSidebar.on(); }

    /** Your account name while the name hiding is on, otherwise null. */
    private static String me() {
        StreamerPrivacy s = instance;
        if (s == null || !s.enabled() || !s.hideName.on()) return null;
        String me = cachedMe;
        long now = System.currentTimeMillis();
        if (me != null && now - cachedAt < 5000L) return me;
        try {
            Minecraft mc = Minecraft.getInstance();
            me = mc == null || mc.getUser() == null ? null : mc.getUser().getName();
        } catch (RuntimeException e) {
            me = null;
        }
        cachedMe = me;
        cachedAt = now;
        return me == null || me.isEmpty() ? null : me;
    }

    private static String alias() {
        StreamerPrivacy s = instance;
        String a = s == null ? null : s.as.get();
        a = a == null ? "" : a.trim();
        return a.isEmpty() ? "Player" : a;
    }

    public static Component scrub(Component c) {
        String me = me();
        if (c == null || me == null) return c;
        if (!c.getString().contains(me)) return c;
        String to = alias();
        MutableComponent out = Component.empty();
        c.visit((style, text) -> {
            out.append(Component.literal(text.replace(me, to)).withStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    public static String scrub(String text) {
        String me = me();
        if (me == null || text == null || text.isEmpty() || !text.contains(me)) return text;
        return text.replace(me, alias());
    }
}
