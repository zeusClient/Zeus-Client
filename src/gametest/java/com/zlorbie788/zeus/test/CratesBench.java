package com.zlorbie788.zeus.test;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.zlorbie788.zeus.core.Crates;
import com.zlorbie788.zeus.ui.CratesScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.state.GuiRenderState;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Measures what the Crates screen costs the CPU each frame, independent of the graphics card: the time to
 * run render() and build every vertex, how many draw batches that makes (a new batch starts whenever the
 * pipeline, texture or clip changes) and how many vertices and text runs it sends. Run with ZEUS_BENCH=1.
 */
@SuppressWarnings("UnstableApiUsage")
final class CratesBench {
    private CratesBench() {
    }

    private static final int WARM = 40, FRAMES = 300;

    static void run(ClientGameTestContext ctx) {
        ctx.waitFor(c -> c.screen != null, 20 * 60);
        if (System.getenv("ZEUS_BENCH_HITCH") != null) {
            hitches(ctx);
            return;
        }
        ctx.getInput().resizeWindow(1920, 1080);
        ctx.runOnClient(c -> {
            c.options.guiScale().set(0);
            c.resizeDisplay();
        });
        ctx.runOnClient(c -> ZeusScreensTest.grant());
        ctx.setScreen(() -> new CratesScreen(null));
        ZeusScreensTest.run(ctx, 1.0f);
        StringBuilder out = new StringBuilder("\nZEUS-BENCH scenario            ms/frame   batches  elements  vertices  texts\n");
        ctx.runOnClient(c -> out.append(measure(c, "crates tab", -1, -1)));
        ctx.runOnClient(c -> {
            int w = c.getWindow().getGuiScaledWidth(), h = c.getWindow().getGuiScaledHeight();
            out.append(measure(c, "crates tab, card hovered", w * 3 / 4, h / 2 - 40));
        });
        for (int tab = 1; tab <= 4; tab++) {
            int t = tab;
            ctx.runOnClient(c -> {
                ZeusScreensTest.set(c.screen, "tab", t);
                ZeusScreensTest.set(c.screen, "tabAt", -10f);
            });
            ZeusScreensTest.run(ctx, 0.5f);
            String[] names = {"", "collection tab", "history tab", "redeem tab", "daily tab"};
            ctx.runOnClient(c -> out.append(measure(c, names[t], -1, -1)));
            if (t == 1) {
                ctx.runOnClient(c -> {
                    int w = c.getWindow().getGuiScaledWidth(), h = c.getWindow().getGuiScaledHeight();
                    out.append(measure(c, "collection, card hovered", w / 2, h / 2));
                });
            }
        }
        ctx.runOnClient(c -> ZeusScreensTest.set(c.screen, "tab", 0));
        ctx.runOnClient(c -> Crates.setQuick(false));
        ctx.runOnClient(c -> ZeusScreensTest.open(c, "hades"));
        ZeusScreensTest.run(ctx, 2.2f);
        ctx.runOnClient(c -> out.append(measure(c, "opening: reel spinning", -1, -1)));
        ZeusScreensTest.run(ctx, 5.3f);
        ctx.runOnClient(c -> out.append(measure(c, "opening: reveal", -1, -1)));
        ctx.runOnClient(c -> ZeusScreensTest.set(c.screen, "opening", null));
        ctx.runOnClient(c -> {
            try {
                Method m = CratesScreen.class.getDeclaredMethod("openInspect", Crates.Item.class);
                m.setAccessible(true);
                m.invoke(c.screen, new Crates.Item("chroma_phoenix", Crates.Rarity.CHROMA));
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        });
        ZeusScreensTest.run(ctx, 0.9f);
        ctx.runOnClient(c -> out.append(measure(c, "inspect", -1, -1)));
        System.out.println(out);
        ctx.setScreen(() -> null);
        ctx.waitTicks(4);
    }

    /**
     * Opens a crate on a freshly opened screen and times every real game frame (each tick of the test draws one)
     * from the click to the reveal: a picture read on the render thread shows up as one long frame.
     */
    private static void hitches(ClientGameTestContext ctx) {
        ctx.getInput().resizeWindow(1920, 1080);
        ctx.runOnClient(c -> {
            c.options.guiScale().set(0);
            c.resizeDisplay();
        });
        ctx.runOnClient(c -> ZeusScreensTest.grant());
        ctx.runOnClient(c -> Crates.setQuick(false));
        ctx.setScreen(() -> new CratesScreen(null));
        ZeusScreensTest.run(ctx, 1.5f);
        String[] crates = {"hades", "nyx", "tempest"};
        StringBuilder out = new StringBuilder("\n");
        for (String crate : crates) {
            ctx.runOnClient(c -> ZeusScreensTest.open(c, crate));
            java.util.List<Long> ms = new java.util.ArrayList<>();
            long start = System.nanoTime(), worstAt = 0, worst = 0;
            while (System.nanoTime() - start < 8_500_000_000L) {
                long t0 = System.nanoTime();
                ctx.waitTick();
                long d = (System.nanoTime() - t0) / 1_000_000L;
                ms.add(d);
                if (d > worst) {
                    worst = d;
                    worstAt = (t0 - start) / 1_000_000L;
                }
            }
            java.util.List<Long> sorted = new java.util.ArrayList<>(ms);
            java.util.Collections.sort(sorted);
            long p50 = sorted.get(sorted.size() / 2), p95 = sorted.get((int) (sorted.size() * 0.95));
            int over = 0;
            for (long d : ms) if (d > 3 * p50 + 20) over++;
            out.append(String.format(Locale.ROOT, "ZEUS-HITCH %-8s frames %4d  median %4d ms  p95 %4d ms  worst %4d ms at %5d ms  long frames %d%n",
                    crate, ms.size(), p50, p95, worst, worstAt, over));
            ctx.runOnClient(c -> ZeusScreensTest.set(c.screen, "opening", null));
            ctx.waitTicks(10);
        }
        System.out.println(out);
        ctx.setScreen(() -> null);
        ctx.waitTicks(4);
    }

    @SuppressWarnings("unchecked")
    private static final java.util.Comparator<net.minecraft.client.gui.render.state.GuiElementRenderState> SORT = sorter();

    @SuppressWarnings("unchecked")
    private static java.util.Comparator<net.minecraft.client.gui.render.state.GuiElementRenderState> sorter() {
        try {
            java.lang.reflect.Field f = net.minecraft.client.gui.render.GuiRenderer.class.getDeclaredField("ELEMENT_SORT_COMPARATOR");
            f.setAccessible(true);
            return (java.util.Comparator<net.minecraft.client.gui.render.state.GuiElementRenderState>) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static String measure(Minecraft c, String name, int mx, int my) {
        GuiRenderState state = new GuiRenderState();
        Map<VertexFormat, BufferBuilder> builders = new HashMap<>();
        Map<VertexFormat, ByteBufferBuilder> bytes = new HashMap<>();
        long total = 0;
        int batches = 0, elements = 0, texts = 0;
        long vertices = 0;
        for (int i = 0; i < WARM + FRAMES; i++) {
            state.reset();
            long t0 = System.nanoTime();
            GuiGraphics g = new GuiGraphics(c, state, mx, my);
            c.screen.render(g, mx, my, 0f);
            // the GUI renderer sorts each layer by clip, pipeline and texture before drawing: count batches the same way
            state.sortElements(SORT);
            int[] counts = new int[3];
            Object[] last = new Object[3];
            state.forEachElement(e -> {
                VertexFormat f = e.pipeline().getVertexFormat();
                BufferBuilder bb = builders.computeIfAbsent(f, k -> {
                    ByteBufferBuilder b = new ByteBufferBuilder(1 << 20);
                    bytes.put(k, b);
                    return new BufferBuilder(b, VertexFormat.Mode.QUADS, k);
                });
                e.buildVertices(bb);
                counts[1]++;
                if (!Objects.equals(last[0], e.pipeline()) || !Objects.equals(last[1], e.textureSetup()) || !Objects.equals(last[2], e.scissorArea())) {
                    counts[0]++;
                    last[0] = e.pipeline();
                    last[1] = e.textureSetup();
                    last[2] = e.scissorArea();
                }
            }, GuiRenderState.TraverseRange.ALL);
            state.forEachText(tx -> counts[2]++);
            long verts = 0;
            for (Map.Entry<VertexFormat, BufferBuilder> en : builders.entrySet()) {
                MeshData md = en.getValue().build();
                if (md != null) {
                    verts += md.drawState().vertexCount();
                    md.close();
                }
                en.setValue(new BufferBuilder(bytes.get(en.getKey()), VertexFormat.Mode.QUADS, en.getKey()));
            }
            long dt = System.nanoTime() - t0;
            if (i >= WARM) {
                total += dt;
                batches += counts[0];
                elements += counts[1];
                texts += counts[2];
                vertices += verts;
            }
        }
        for (ByteBufferBuilder b : bytes.values()) b.close();
        return String.format(Locale.ROOT, "ZEUS-BENCH %-26s %8.3f %9d %9d %9d %6d%n", name, total / 1e6 / FRAMES,
                batches / FRAMES, elements / FRAMES, vertices / FRAMES, texts / FRAMES);
    }
}
