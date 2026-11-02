package net.flantyle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChunkManager {
    private static final Map<Long, ChunkSystem> chunks = new HashMap<>();
    private static final int LOAD_RADIUS = 8;
    private static final int UNLOAD_RADIUS = 12;

    private static final long GEN_BUDGET_NS = 4_000_000L;
    private static final long BUILD_BUDGET_NS = 4_000_000L;

    private static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    public static Map<Long, ChunkSystem> getAllChunks() {
        return chunks;
    }

    public static ChunkSystem getChunk(int cx, int cz) {
        return chunks.get(key(cx, cz));
    }

    public static void worldAddBlock(int wx, int wy, int wz, String blockType) {
        int cx = ChunkSystem.worldToChunkCoord(wx);
        int cz = ChunkSystem.worldToChunkCoord(wz);
        long k = key(cx, cz);

        ChunkSystem chunkSystem = chunks.get(k);
        if (chunkSystem == null) {
            chunkSystem = new ChunkSystem(cx, cz);
            chunks.put(k, chunkSystem);
        }
        chunkSystem.chunkBlockAppend(wx, wy, wz, blockType);
        markNeighborsDirty(cx, cz);
    }

    public static int getPackedLight(int wx, int wy, int wz) {
        int cx = ChunkSystem.worldToChunkCoord(wx);
        int cz = ChunkSystem.worldToChunkCoord(wz);
        ChunkSystem chunk = chunks.get(key(cx, cz));
        if (chunk == null) return LightEngine.pack(15, 0);
        return chunk.getPackedLightWorld(wx, wy, wz);
    }

    public static void worldRemoveBlock(int wx, int wy, int wz) {
        int cx = ChunkSystem.worldToChunkCoord(wx);
        int cz = ChunkSystem.worldToChunkCoord(wz);

        ChunkSystem chunk = chunks.get(key(cx, cz));
        if (chunk == null) return;

        chunk.chunkBlockRemove(wx, wy, wz);
        markNeighborsDirty(cx, cz);
    }

    public static ChunkSystem.BlockData getBlock(int wx, int wy, int wz) {
        int cx = ChunkSystem.worldToChunkCoord(wx);
        int cz = ChunkSystem.worldToChunkCoord(wz);
        ChunkSystem chunkSystem = chunks.get(key(cx, cz));
        if (chunkSystem == null) return null;

        int lx = ChunkSystem.worldToLocalCoord(wx);
        int lz = ChunkSystem.worldToLocalCoord(wz);
        return chunkSystem.getBlockByLocal(lx, wy, lz);
    }

    public static void preloadChunks(float camX, float camZ) {
        int pcx = ChunkSystem.worldToChunkCoord((int) Math.floor(camX));
        int pcz = ChunkSystem.worldToChunkCoord((int) Math.floor(camZ));

        List<int[]> missing = new ArrayList<>();
        for (int dx = -LOAD_RADIUS; dx <= LOAD_RADIUS; dx++) {
            for (int dz = -LOAD_RADIUS; dz <= LOAD_RADIUS; dz++) {
                int cx = pcx + dx;
                int cz = pcz + dz;
                if (!chunks.containsKey(key(cx, cz))) {
                    missing.add(new int[]{cx, cz, dx * dx + dz * dz});
                }
            }
        }

        missing.sort(Comparator.comparingInt(a -> a[2]));

        for (int[] m : missing) {
            ChunkSystem chunk = new ChunkSystem(m[0], m[1]);
            WorldGenerator.generateChunk(chunk);
            chunks.put(key(m[0], m[1]), chunk);
            markNeighborsDirty(m[0], m[1]);
        }

        for (ChunkSystem chunk : chunks.values()) {
            chunk.recalcLight();
        }

        for (ChunkSystem chunk : chunks.values()) {
            chunk.rebuildMesh();
        }

        System.out.println("[INFO-CHUNK] Preloaded " + chunks.size() + " chunks");
    }

    public static void updateLoadedChunks(float camX, float camZ) {
        int pcx = ChunkSystem.worldToChunkCoord((int) Math.floor(camX));
        int pcz = ChunkSystem.worldToChunkCoord((int) Math.floor(camZ));

        List<int[]> missing = new ArrayList<>();
        for (int dx = -LOAD_RADIUS; dx <= LOAD_RADIUS; dx++) {
            for (int dz = -LOAD_RADIUS; dz <= LOAD_RADIUS; dz++) {
                int cx = pcx + dx;
                int cz = pcz + dz;
                if (!chunks.containsKey(key(cx, cz))) {
                    missing.add(new int[]{cx, cz, dx * dx + dz * dz});
                }
            }
        }

        missing.sort(Comparator.comparingInt(a -> a[2]));

        long deadline = System.nanoTime() + GEN_BUDGET_NS;
        for (int[] m : missing) {
            if (System.nanoTime() > deadline) break;

            ChunkSystem chunkSystem = new ChunkSystem(m[0], m[1]);
            WorldGenerator.generateChunk(chunkSystem);
            chunks.put(key(m[0], m[1]), chunkSystem);
            markNeighborsDirty(m[0], m[1]);
        }

        unloadFarChunks(pcx, pcz);
    }

    private static void unloadFarChunks(int pcx, int pcz) {
        List<Long> toRemove = new ArrayList<>();

        for (Map.Entry<Long, ChunkSystem> e : chunks.entrySet()) {
            ChunkSystem c = e.getValue();
            int dx = c.getCx() - pcx;
            int dz = c.getCz() - pcz;

            if (Math.abs(dx) > UNLOAD_RADIUS || Math.abs(dz) > UNLOAD_RADIUS) {
                c.releaseGL();
                toRemove.add(e.getKey());
            }
        }

        for (Long k : toRemove) {
            chunks.remove(k);
        }
    }

    private static void markNeighborsDirty(int cx, int cz) {
        ChunkSystem c;
        c = chunks.get(key(cx + 1, cz)); if (c != null) c.markDirty();
        c = chunks.get(key(cx - 1, cz)); if (c != null) c.markDirty();
        c = chunks.get(key(cx, cz + 1)); if (c != null) c.markDirty();
        c = chunks.get(key(cx, cz - 1)); if (c != null) c.markDirty();
    }

    private static final long LIGHT_BUDGET_NS = 2_000_000L;

    public static void updateAllDirtyChunks() {
        long deadline = System.nanoTime() + LIGHT_BUDGET_NS;

        for (ChunkSystem chunk : chunks.values()) {
            if (chunk.isLightDirty()) {
                chunk.recalcLight();
                if (System.nanoTime() > deadline) return;
            }
        }

        deadline = System.nanoTime() + BUILD_BUDGET_NS;

        for (ChunkSystem chunk : chunks.values()) {
            if (chunk.isDirty()) {
                chunk.rebuildMesh();
                if (System.nanoTime() > deadline) break;
            }
        }
    }
}