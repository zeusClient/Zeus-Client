package com.zlorbie788.zeus.mods;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class DiscordPresence extends Module {
    private static volatile DiscordPresence self;

    private final Setting.Text appId = add(new Setting.Text("Application ID", "The number from discord.com/developers, or any Discord link that contains it (right click your name, Copy Link) - the digits are picked out for you. Only the connection needs it; nothing is logged in.", "", 120));
    private static final String DISCORD = "https://discord.gg/znrj2TfCP8";

    private final Setting.Text details = add(new Setting.Text("First line", "The top line of your card. In singleplayer it reads the singleplayer line instead.", "Playing Minecraft 1.21.11", 60));
    private final Setting.Text single = add(new Setting.Text("Singleplayer line", "What the top line says in a singleplayer world.", "Playing Minecraft 1.21.11", 60));
    private final Setting.Text state = add(new Setting.Text("Second line", "The line under it. Empty falls back to where you are.", "", 60));
    private final Setting.Text image = add(new Setting.Text("Image name", "Optional. Discord only draws an image that exists in your own application: upload one under Rich Presence, Art Assets and put its name here. With this empty the card shows your application's icon instead, which a fresh application already has.", "", 40));
    private final Setting.Bool showServer = add(new Setting.Bool("Show the server", "Puts the name of the server you are on in the second line (when it is empty). Off keeps it private.", false));
    private final Setting.Bool showWorld = add(new Setting.Bool("Show singleplayer / multiplayer", "Says whether you are in a singleplayer world or on a server. Off keeps that private.", true));
    private volatile String note = "Off.";

    static void frame(String json) {
        DiscordPresence m = self;
        if (m == null || json == null) return;
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            String evt = o.has("evt") && !o.get("evt").isJsonNull() ? o.get("evt").getAsString() : "";
            if ("ERROR".equals(evt)) {
                JsonObject d = o.has("data") && o.get("data").isJsonObject() ? o.getAsJsonObject("data") : new JsonObject();
                int code = d.has("code") ? d.get("code").getAsInt() : -1;
                String msg = d.has("message") ? d.get("message").getAsString() : "no message";
                m.note = "Discord refused the card: " + code + " - " + msg;
            } else if ("READY".equals(evt)) {
                m.note = "Connected to Discord.";
                Link l = m.link;
                if (l != null) l.ready = true;
            } else if ("DISPATCH".equals(evt)) {
                m.note = "Connected to Discord.";
            }
        } catch (RuntimeException ignored) {
        }
    }
    private final Setting.Info status = addSetting(new Setting.Info("Status", () -> List.of(note)));

    private void reconnect() {
        note = "Reconnecting...";
        onDisable();
        onEnable();
    }

    private volatile String liveWhere = "In the menus";
    private volatile String liveServer = "";
    private volatile long since;
    private Link link;

    public DiscordPresence() {
        super("discordpresence", "Discord Rich Presence", "Shows what you are playing on your Discord profile. Needs the Discord app open and an Application ID.", Category.UTILITY);
        anywhere();
        alias("discord", "rpc", "presence", "rich presence");
        add(new Setting.Action("Reconnect", "Closes the link and opens a new one, for when Discord was started after the game.", this::reconnect));
        self = this;
    }

    @Override
    protected void onEnable() {
        since = System.currentTimeMillis() / 1000L;
        String id = appId.get().replaceAll("[^0-9]", "");
        if (id.isEmpty()) {
            note = "Paste your Application ID first.";
            Zeus.toast("Discord Presence: paste your Application ID first (discord.com/developers)", false);
            return;
        }
        Link previous = link;
        link = new Link(id, previous);
        link.start();
        note = "Connecting to Discord...";
    }

    @Override
    protected void onDisable() {
        if (link != null) link.stop();
        link = null;
        note = "Off.";
    }

    @Override
    public String info() {
        return link != null && link.connected ? "live" : null;
    }

    static void pulse(Minecraft mc) {
        DiscordPresence m = self;
        if (m == null || !m.enabled()) return;
        if (mc.level == null) {
            m.liveWhere = "In the menus";
            m.liveServer = "";
        } else if (mc.getCurrentServer() != null) {
            m.liveWhere = "On a server";
            m.liveServer = mc.getCurrentServer().name == null ? "" : mc.getCurrentServer().name;
        } else {
            m.liveWhere = "In a singleplayer world";
            m.liveServer = "";
        }
    }

    private JsonObject card() {
        JsonObject a = new JsonObject();
        boolean singleplayer = liveWhere.contains("singleplayer");
        String d = singleplayer ? single.get().trim() : details.get().trim();
        if (!d.isEmpty()) a.addProperty("details", d);
        String s = state.get().trim();
        if (s.isEmpty()) {
            if (singleplayer && showWorld.on()) s = "In singleplayer";
            else if (showServer.on() && !liveServer.isBlank()) s = liveServer;
        }
        if (s.isEmpty()) s = "Zeus Client";
        else if (!s.toLowerCase(Locale.ROOT).contains("zeus")) s = s + " | Zeus Client";
        a.addProperty("state", s);
        JsonObject t = new JsonObject();
        t.addProperty("start", since);
        a.add("timestamps", t);
        String img = image.get().trim();
        if (!img.isEmpty()) {
            JsonObject assets = new JsonObject();
            assets.addProperty("large_image", img);
            assets.addProperty("large_text", "Zeus");
            a.add("assets", assets);
        }
        com.google.gson.JsonArray buttons = new com.google.gson.JsonArray();
        JsonObject join = new JsonObject();
        join.addProperty("label", "Join the Zeus Discord");
        join.addProperty("url", DISCORD);
        buttons.add(join);
        a.add("buttons", buttons);
        return a;
    }

    private final class Link implements Runnable {
        private final String id;
        private final Thread thread;
        private final Thread previous;
        private volatile boolean running = true;
        volatile boolean connected;
        private Pipe pipe;
        private String sent = "";
        private long sentAt;
        private long retry = 4000;
        private long openedAt;
        volatile boolean ready;

        Link(String id, Link previous) {
            this.id = id;
            this.previous = previous == null ? null : previous.thread;
            this.thread = new Thread(this, "Zeus-Discord");
            this.thread.setDaemon(true);
        }

        void start() {
            thread.start();
        }

        void stop() {
            running = false;
            thread.interrupt();
        }

        @Override
        public void run() {
            try {
                if (previous != null) previous.join(1500);
            } catch (InterruptedException e) {
                return;
            }
            while (running) {
                try {
                    if (pipe == null) {
                        pipe = open(id);
                        if (pipe == null) {
                            note = "Discord is not open (or not reachable). Trying again...";
                            Thread.sleep(retry);
                            retry = Math.min(retry * 2, 30000);
                            continue;
                        }
                        retry = 4000;
                        sent = "";
                        ready = false;
                        openedAt = System.currentTimeMillis();
                        connected = true;
                        note = "Talking to Discord...";
                    }
                    pipe.drain();
                    String now = card().toString();
                    long t = System.currentTimeMillis();
                    if (!ready) {
                        if (t - openedAt > 5000) {
                            note = pipe.dead()
                                    ? "Discord closed the link - the Application ID is probably wrong."
                                    : "Discord did not answer the handshake.";
                            closePipe();
                            connected = false;
                        }
                        Thread.sleep(250);
                        continue;
                    }
                    if (!now.equals(sent) ? t - sentAt > 1500 : t - sentAt > 60000) {
                        pipe.send(1, activity(now));
                        sent = now;
                        sentAt = t;
                    }
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    break;
                } catch (IOException | RuntimeException e) {
                    connected = false;
                    note = "Lost Discord (" + e.getClass().getSimpleName() + "). Trying again...";
                    closePipe();
                    try {
                        Thread.sleep(retry);
                    } catch (InterruptedException ie) {
                        break;
                    }
                }
            }
            if (pipe != null) {
                try {
                    pipe.send(1, clear());
                } catch (IOException | RuntimeException ignored) {
                }
            }
            closePipe();
            connected = false;
        }

        private void closePipe() {
            if (pipe != null) pipe.close();
            pipe = null;
        }

        private String activity(String card) {
            JsonObject args = new JsonObject();
            args.addProperty("pid", ProcessHandle.current().pid());
            args.add("activity", JsonParser.parseString(card));
            JsonObject root = new JsonObject();
            root.addProperty("cmd", "SET_ACTIVITY");
            root.add("args", args);
            root.addProperty("nonce", UUID.randomUUID().toString());
            return root.toString();
        }

        private String clear() {
            JsonObject args = new JsonObject();
            args.addProperty("pid", ProcessHandle.current().pid());
            JsonObject root = new JsonObject();
            root.addProperty("cmd", "SET_ACTIVITY");
            root.add("args", args);
            root.addProperty("nonce", UUID.randomUUID().toString());
            return root.toString();
        }
    }

    private interface Pipe {
        void write(byte[] data) throws IOException;

        void drain() throws IOException;

        void close();

        default boolean dead() {
            return false;
        }

        default void send(int opcode, String json) throws IOException {
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            ByteBuffer b = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
            b.putInt(opcode).putInt(body.length).put(body);
            write(b.array());
        }
    }

    private static Pipe open(String appId) {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        for (int i = 0; i < 10; i++) {
            try {
                Pipe p;
                if (windows) {
                    p = null;
                    for (String prefix : new String[] {"\\\\.\\pipe\\", "\\\\?\\pipe\\"}) {
                        try {
                            p = new WinPipe(prefix + "discord-ipc-" + i);
                            break;
                        } catch (IOException | RuntimeException ignored) {
                        }
                    }
                    if (p == null) continue;
                } else {
                    p = null;
                    for (Path dir : socketDirs()) {
                        Path path = dir.resolve("discord-ipc-" + i);
                        if (!path.toFile().exists()) continue;
                        SocketChannel ch = SocketChannel.open(StandardProtocolFamily.UNIX);
                        try {
                            ch.connect(UnixDomainSocketAddress.of(path));
                        } catch (IOException e) {
                            ch.close();
                            continue;
                        }
                        ch.configureBlocking(false);
                        p = new UnixPipe(ch);
                        break;
                    }
                    if (p == null) continue;
                }
                JsonObject hello = new JsonObject();
                hello.addProperty("v", 1);
                hello.addProperty("client_id", appId);
                p.send(0, hello.toString());
                return p;
            } catch (IOException | RuntimeException ignored) {
            }
        }
        return null;
    }

    private static List<Path> socketDirs() {
        List<Path> out = new ArrayList<>();
        for (String key : new String[] {"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"}) {
            String v = System.getenv(key);
            if (v == null || v.isBlank()) continue;
            Path base = Path.of(v);
            out.add(base);
            out.add(base.resolve("app/com.discordapp.Discord"));
            out.add(base.resolve("snap.discord"));
        }
        out.add(Path.of("/tmp"));
        return out;
    }

    private static final class WinPipe implements Pipe {
        private final RandomAccessFile pipe;
        private final Thread reader = new Thread(this::listen, "Zeus-Discord-Read");
        private volatile boolean dead;

        WinPipe(String path) throws IOException {
            // One handle, not two: opening a pipe name again on Windows is a second client
            // connection, and Discord only ever answers the one the handshake was written to.
            this.pipe = new RandomAccessFile(path, "rw");
            reader.setDaemon(true);
            reader.start();
        }

        private void listen() {
            try {
                byte[] head = new byte[8];
                while (true) {
                    pipe.readFully(head);
                    int op = ByteBuffer.wrap(head, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                    int len = ByteBuffer.wrap(head, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                    if (len < 0 || len > (1 << 20)) break;
                    byte[] body = new byte[len];
                    pipe.readFully(body);
                    if (op == 1) frame(new String(body, StandardCharsets.UTF_8));
                }
            } catch (IOException | RuntimeException ignored) {
            }
            dead = true;
        }

        @Override
        public synchronized void write(byte[] data) throws IOException {
            pipe.write(data);
        }

        @Override
        public boolean dead() {
            return dead;
        }

        @Override
        public void drain() {
        }

        @Override
        public void close() {
            try {
                pipe.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static final class UnixPipe implements Pipe {
        private final SocketChannel ch;
        private final ByteBuffer scratch = ByteBuffer.allocate(8192);

        UnixPipe(SocketChannel ch) {
            this.ch = ch;
        }

        @Override
        public synchronized void write(byte[] data) throws IOException {
            ByteBuffer b = ByteBuffer.wrap(data);
            int spins = 0;
            while (b.hasRemaining()) {
                if (ch.write(b) == 0 && ++spins > 2000) throw new IOException("write stalled");
            }
        }

        private final ByteBuffer in = ByteBuffer.allocate(1 << 16);
        private volatile boolean dead;

        @Override
        public boolean dead() {
            return dead;
        }

        @Override
        public synchronized void drain() throws IOException {
            while (true) {
                scratch.clear();
                int n = ch.read(scratch);
                if (n < 0) {
                    dead = true;
                    throw new IOException("closed");
                }
                if (n == 0) break;
                scratch.flip();
                in.put(scratch);
            }
            in.flip();
            while (in.remaining() >= 8) {
                int start = in.position();
                int op = in.getInt();
                int len = in.getInt();
                if (len < 0 || len > (1 << 20) || in.remaining() < len) {
                    in.position(start);
                    break;
                }
                byte[] body = new byte[len];
                in.get(body);
                if (op == 1) frame(new String(body, StandardCharsets.UTF_8));
            }
            in.compact();
        }

        @Override
        public void close() {
            try {
                ch.close();
            } catch (IOException ignored) {
            }
        }
    }
}
