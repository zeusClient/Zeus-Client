package com.zlorbie788.zeus.mods;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public final class SchematicPlan {
    public record Place(BlockPos at, Block block, Item item, String state) {}

    public final String name;
    public final Path file;
    public final BlockPos origin;
    public final List<Place> order;
    public final Map<Item, Integer> needs;
    public final int unplaceable;

    private SchematicPlan(String name, Path file, BlockPos origin, List<Place> order, Map<Item, Integer> needs, int unplaceable) {
        this.name = name;
        this.file = file;
        this.origin = origin;
        this.order = order;
        this.needs = needs;
        this.unplaceable = unplaceable;
    }

    public static List<Path> folders() {
        List<Path> out = new ArrayList<>();
        try {
            out.add(FabricLoader.getInstance().getGameDir().resolve("schematics"));
            out.add(FabricLoader.getInstance().getConfigDir().resolve("zeus").resolve("schematics"));
        } catch (RuntimeException ignored) {
        }
        return out;
    }

    private static List<Path> cached = List.of();
    private static long cachedAt;

    public static synchronized List<Path> files() {
        long now = System.currentTimeMillis();
        if (now - cachedAt < 5000L) return cached;
        cachedAt = now;
        cached = scan();
        return cached;
    }

    private static List<Path> scan() {
        List<Path> all = new ArrayList<>();
        for (Path dir : folders()) {
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> walk = Files.walk(dir, 4)) {
                walk.filter(Files::isRegularFile).filter(p -> {
                    String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                    return n.endsWith(".litematic") || n.endsWith(".schem") || n.endsWith(".schematic");
                }).forEach(all::add);
            } catch (IOException ignored) {
            }
        }
        all.sort(Comparator.comparingLong((Path p) -> {
            try {
                return Files.getLastModifiedTime(p).toMillis();
            } catch (IOException e) {
                return 0L;
            }
        }).reversed());
        return all;
    }

    public static String display(Path p) {
        String n = p.getFileName().toString();
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    private static String squash(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public static Path find(String words) {
        List<Path> all = files();
        if (all.isEmpty()) return null;
        String w = words == null ? "" : squash(words.replaceAll("(?i)\\b(the|my|schematic|schem|litematic|latest|last|newest)\\b", ""));
        if (w.isEmpty()) return all.get(0);
        for (Path p : all) if (squash(display(p)).equals(w)) return p;
        for (Path p : all) if (squash(display(p)).startsWith(w)) return p;
        for (Path p : all) if (squash(display(p)).contains(w)) return p;
        for (Path p : all) if (w.contains(squash(display(p)))) return p;
        return null;
    }

    public static SchematicPlan load(Path file, BlockPos origin) throws IOException {
        SchematicData data = SchematicData.read(file);
        List<Place> order = new ArrayList<>();
        Map<Item, Integer> needs = new LinkedHashMap<>();
        int unplaceable = 0;
        for (SchematicData.Cell c : data.cells) {
            String state = c.state();
            int br = state.indexOf('[');
            String id = br < 0 ? state : state.substring(0, br);
            Identifier key = Identifier.tryParse(id);
            if (key == null || !BuiltInRegistries.BLOCK.containsKey(key)) {
                unplaceable++;
                continue;
            }
            Block block = BuiltInRegistries.BLOCK.getValue(key);
            if (block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR) continue;
            // the second half of doors, beds and tall plants comes with the first
            String props = br < 0 ? "" : state.substring(br);
            if (props.contains("half=upper") || props.contains("part=head")) continue;
            Item item = block.asItem();
            if (item == Items.AIR) {
                unplaceable++;
                continue;
            }
            BlockPos at = origin.offset(c.x(), c.y(), c.z());
            order.add(new Place(at, block, item, state));
            needs.merge(item, 1, Integer::sum);
        }
        double cx = origin.getX() + data.width / 2.0, cz = origin.getZ() + data.length / 2.0;
        order.sort(Comparator.comparingInt((Place p) -> p.at().getY())
                .thenComparingDouble(p -> Math.hypot(p.at().getX() + 0.5 - cx, p.at().getZ() + 0.5 - cz)));
        Map<Item, Integer> sorted = new LinkedHashMap<>();
        needs.entrySet().stream().sorted(Map.Entry.<Item, Integer>comparingByValue().reversed())
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return new SchematicPlan(display(file), file, origin, order, sorted, unplaceable);
    }

    public static String itemName(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).getPath();
    }

    public static String jobFor(Item item, int count) {
        String id = itemName(item);
        int n = Math.max(1, Math.min(256, count));
        if (id.endsWith("_log") || id.endsWith("_wood") || id.endsWith("_stem")) return "chop " + Math.max(1, (n + 4) / 5) + " trees";
        switch (id) {
            case "cobblestone", "cobbled_deepslate", "dirt", "sand", "red_sand", "gravel", "clay_ball", "netherrack",
                 "andesite", "diorite", "granite", "tuff", "calcite", "deepslate", "coal", "raw_iron", "raw_copper",
                 "raw_gold", "diamond", "redstone", "lapis_lazuli", "emerald" -> {
                return "mine " + n + " " + id.replace('_', ' ');
            }
            case "stone" -> {
                return "smelt " + n + " stone";
            }
            case "glass" -> {
                return "smelt " + n + " glass";
            }
            case "smooth_stone" -> {
                return "smelt " + n + " smooth stone";
            }
            case "iron_ingot", "gold_ingot", "copper_ingot" -> {
                return "smelt " + n + " " + id.replace("_ingot", "");
            }
            default -> {
            }
        }
        if (com.zlorbie788.zeus.core.Craft.find(id) != null || id.endsWith("_planks") || id.endsWith("_slab")
                || id.endsWith("_stairs") || id.endsWith("_fence") || id.endsWith("_door") || id.endsWith("_trapdoor")
                || id.endsWith("_wall") || id.endsWith("_bricks") || id.equals("torch") || id.equals("stick")
                || id.equals("ladder") || id.equals("chest") || id.equals("crafting_table") || id.equals("furnace")) {
            return "craft " + n + " " + id.replace('_', ' ');
        }
        return null;
    }
}
