package net.flantyle;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StructureManager {
    public static final String STRUCTURE_PATH = "/structure/";

    private static final Map<String, String> registry = new HashMap<>();
    private static final Map<String, Structure> cache = new HashMap<>();

    public static void register(String name) {
        registry.put(name, name);
    }

    public static void register(String name, String filename) {
        registry.put(name, filename);
    }

    public static void registerAll() {
        register("tree_0");
    }

    public static Structure get(String name) {
        String filename = registry.get(name);
        if (filename == null) {
            System.out.println("[WARN-STRUCTURE] Not registered: " + name);
            return null;
        }
        return load(filename);
    }

    public static List<String> getAllNames() {
        return new ArrayList<>(registry.keySet());
    }

    public static Structure load(String filename) {
        if (cache.containsKey(filename)) {
            return cache.get(filename);
        }

        String path = STRUCTURE_PATH + filename + ".fst";

        InputStream is = StructureManager.class.getResourceAsStream(path);
        if (is == null) {
            System.out.println("[ERROR] Structure not found: " + path);
            return null;
        }

        int maxX = 0, maxY = 0, maxZ = 0;
        List<int[]> rawCoords = new ArrayList<>();
        List<String> rawNames = new ArrayList<>();

        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {

            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.startsWith("max_x")) {
                    maxX = parseIntValue(line);
                } else if (line.startsWith("max_y")) {
                    maxY = parseIntValue(line);
                } else if (line.startsWith("max_z")) {
                    maxZ = parseIntValue(line);
                } else {
                    String[] parts = line.split("\\s+");
                    if (parts.length < 4) continue;
                    int x = Integer.parseInt(parts[0]);
                    int y = Integer.parseInt(parts[1]);
                    int z = Integer.parseInt(parts[2]);
                    String name = parts[3];
                    rawCoords.add(new int[]{x, y, z});
                    rawNames.add(name);
                }
            }
        } catch (Exception e) {
            System.out.println("[ERROR] Load structure failed: " + e);
            return null;
        }

        int centerLx = maxX / 2;
        int centerLz = maxZ / 2;

        List<BlockOffset> offsets = new ArrayList<>(rawCoords.size());
        for (int i = 0; i < rawCoords.size(); i++) {
            int[] p = rawCoords.get(i);
            int ox = (p[0] - centerLx) * 2;
            int oy = p[1] * 2;
            int oz = (p[2] - centerLz) * 2;
            offsets.add(new BlockOffset(ox, oy, oz, rawNames.get(i)));
        }

        Structure s = new Structure(filename, maxX, maxY, maxZ, offsets);
        cache.put(filename, s);

        System.out.println("[INFO-STRUCTURE] Loaded " + filename
                + " size=" + maxX + "x" + maxY + "x" + maxZ
                + " blocks=" + offsets.size());

        return s;
    }

    public static void place_system(Structure structure, int ceX, int ceY, int ceZ) {
        if (structure == null) return;

        int alignedX = Math.floorDiv(ceX, 2) * 2;
        int alignedY = Math.floorDiv(ceY, 2) * 2;
        int alignedZ = Math.floorDiv(ceZ, 2) * 2;

        for (BlockOffset b : structure.blocks) {
            int wx = alignedX + b.ox;
            int wy = alignedY + b.oy;
            int wz = alignedZ + b.oz;
            ChunkManager.worldAddBlock(wx, wy, wz, b.blockName);
        }

        System.out.println("[INFO-STRUCTURE] Placed " + structure.name
                + " at (" + alignedX + "," + alignedY + "," + alignedZ + ")"
                + " blocks=" + structure.blocks.size());
    }

    public static void place(String name, int ceX, int ceY, int ceZ) {
        Structure s = get(name);
        place_system(s, ceX, ceY, ceZ);
    }

    public static void clearCache() {
        cache.clear();
    }

    private static int parseIntValue(String line) {
        int eq = line.indexOf('=');
        if (eq < 0) return 0;
        return Integer.parseInt(line.substring(eq + 1).trim());
    }

    public static class Structure {
        public final String name;
        public final int sizeX, sizeY, sizeZ;
        public final List<BlockOffset> blocks;

        public Structure(String name, int sizeX, int sizeY, int sizeZ,
                         List<BlockOffset> blocks) {
            this.name = name;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.blocks = blocks;
        }
    }

    public static class BlockOffset {
        public final int ox, oy, oz;
        public final String blockName;

        public BlockOffset(int ox, int oy, int oz, String blockName) {
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            this.blockName = blockName;
        }
    }
}