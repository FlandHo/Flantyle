package net.flantyle;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class ChunkSystem {
    public static final int CHUNK_SIZE = 16;
    public static final int BLOCK_SCALE = 2;
    public static final int CHUNK_WORLD_SIZE = CHUNK_SIZE * BLOCK_SCALE;
    public static final String FALLBACK_TEX = "__fallback__";

    private static final int[] FACE_DX = {0, 0, -2, 2, 0, 0};
    private static final int[] FACE_DY = {0, 0, 0, 0, 2, -2};
    private static final int[] FACE_DZ = {2, -2, 0, 0, 0, 0};

    private static final int QUEUE_CAP = CHUNK_SIZE * 256 * CHUNK_SIZE * 2;

    private final int cx;
    private final int cz;

    private final Map<Long, BlockData> blocks = new HashMap<>();
    private final int[][][] lightData = new int[CHUNK_SIZE][256][CHUNK_SIZE];
    private final int[] queueBuf = new int[QUEUE_CAP * 3];

    private final Map<String, Integer> vaoMap = new HashMap<>();
    private final Map<String, Integer> vertexCountMap = new HashMap<>();

    private boolean dirty = true;
    private boolean lightDirty = true;

    public ChunkSystem(int cx, int cz) {
        this.cx = cx;
        this.cz = cz;
    }

    public int getCx() { return cx; }
    public int getCz() { return cz; }
    public boolean isDirty() { return dirty; }
    public boolean isLightDirty() { return lightDirty; }
    public void markDirty() { this.dirty = true; }
    public void markLightDirty() { this.lightDirty = true; this.dirty = true; }
    public void clearDirty() { this.dirty = false; }
    public Map<Long, BlockData> getBlocks() { return blocks; }
    public Map<String, Integer> getVaoMap() { return vaoMap; }
    public Map<String, Integer> getVertexCountMap() { return vertexCountMap; }

    public static long packLocalKey(int lx, int ly, int lz) {
        return ((long) (lx & 0xFF) << 16) | ((long) (ly & 0xFF) << 8) | (lz & 0xFF);
    }

    public static int worldToChunkCoord(int w) {
        return Math.floorDiv(w, CHUNK_WORLD_SIZE);
    }

    public static int worldToLocalCoord(int w) {
        return Math.floorMod(w, CHUNK_WORLD_SIZE) / BLOCK_SCALE;
    }

    private int getLightLocal(int lx, int ly, int lz) {
        if (lx < 0 || lx >= CHUNK_SIZE || lz < 0 || lz >= CHUNK_SIZE) {
            return LightEngine.pack(15, 0);
        }
        if (ly < 0 || ly > 255) return LightEngine.pack(15, 0);
        return lightData[lx][ly][lz];
    }

    private void setLightLocal(int lx, int ly, int lz, int packed) {
        if (lx < 0 || lx >= CHUNK_SIZE || lz < 0 || lz >= CHUNK_SIZE) return;
        if (ly < 0 || ly > 255) return;
        lightData[lx][ly][lz] = packed;
    }

    public int getPackedLightWorld(int wx, int wy, int wz) {
        int lx = worldToLocalCoord(wx);
        int lz = worldToLocalCoord(wz);
        return getLightLocal(lx, wy, lz);
    }

    public void chunkBlockAppend(int wx, int wy, int wz, String blockType) {
        if (worldToChunkCoord(wx) != cx || worldToChunkCoord(wz) != cz) return;

        int lx = worldToLocalCoord(wx);
        int lz = worldToLocalCoord(wz);

        if (wy < 0 || wy > 255) return;

        String[] faces;
        if (!BlockType.exists(blockType)) {
            faces = new String[]{FALLBACK_TEX, FALLBACK_TEX, FALLBACK_TEX,
                    FALLBACK_TEX, FALLBACK_TEX, FALLBACK_TEX};
        } else {
            faces = BlockType.getFaces(blockType);
        }

        blocks.put(packLocalKey(lx, wy, lz),
                new BlockData(wx, wy, wz, lx, wy, lz, blockType, faces));
        lightDirty = true;
        dirty = true;
    }

    public void chunkBlockRemove(int wx, int wy, int wz) {
        if (worldToChunkCoord(wx) != cx || worldToChunkCoord(wz) != cz) return;

        int lx = worldToLocalCoord(wx);
        int lz = worldToLocalCoord(wz);

        long k = packLocalKey(lx, wy, lz);
        if (!blocks.containsKey(k)) return;

        blocks.remove(k);
        lightDirty = true;
        dirty = true;
    }

    public BlockData getBlockByLocal(int lx, int ly, int lz) {
        return blocks.get(packLocalKey(lx, ly, lz));
    }

    public void releaseGL() {
        for (int vao : vaoMap.values()) {
            glDeleteVertexArrays(vao);
        }
        vaoMap.clear();
        vertexCountMap.clear();
        blocks.clear();
    }

    public void recalcLight() {
        if (!lightDirty) return;

        recalcSkyLight();
        recalcBlockLight();

        lightDirty = false;
        dirty = true;
    }

    private void recalcSkyLight() {
        int head = 0, tail = 0;

        for (int lx = 0; lx < CHUNK_SIZE; lx++) {
            for (int lz = 0; lz < CHUNK_SIZE; lz++) {
                boolean blocked = false;
                for (int y = 255; y >= 0; y--) {
                    int oldBlock = LightEngine.blockOf(lightData[lx][y][lz]);

                    if (blocks.containsKey(packLocalKey(lx, y, lz))) {
                        blocked = true;
                        lightData[lx][y][lz] = LightEngine.pack(0, oldBlock);
                    } else if (!blocked) {
                        lightData[lx][y][lz] = LightEngine.pack(15, oldBlock);
                        queueBuf[tail * 3] = lx;
                        queueBuf[tail * 3 + 1] = y;
                        queueBuf[tail * 3 + 2] = lz;
                        tail = (tail + 1) % QUEUE_CAP;
                    } else {
                        lightData[lx][y][lz] = LightEngine.pack(0, oldBlock);
                    }
                }
            }
        }

        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
        while (head != tail) {
            int lx = queueBuf[head * 3];
            int y = queueBuf[head * 3 + 1];
            int lz = queueBuf[head * 3 + 2];
            head = (head + 1) % QUEUE_CAP;

            int cur = LightEngine.skyOf(lightData[lx][y][lz]);
            int next = cur - 1;
            if (next <= 0) continue;

            for (int[] d : dirs) {
                int nx = lx + d[0];
                int nz = lz + d[2];
                if (nx < 0 || nx >= CHUNK_SIZE || nz < 0 || nz >= CHUNK_SIZE) continue;
                if (blocks.containsKey(packLocalKey(nx, y, nz))) continue;

                int old = lightData[nx][y][nz];
                if (LightEngine.skyOf(old) < next) {
                    lightData[nx][y][nz] = LightEngine.pack(next, LightEngine.blockOf(old));
                    queueBuf[tail * 3] = nx;
                    queueBuf[tail * 3 + 1] = y;
                    queueBuf[tail * 3 + 2] = nz;
                    tail = (tail + 1) % QUEUE_CAP;
                }
            }
        }
    }

    private void recalcBlockLight() {
        int head = 0, tail = 0;

        for (int lx = 0; lx < CHUNK_SIZE; lx++) {
            for (int lz = 0; lz < CHUNK_SIZE; lz++) {
                for (int y = 0; y <= 255; y++) {
                    int old = lightData[lx][y][lz];
                    lightData[lx][y][lz] = LightEngine.pack(LightEngine.skyOf(old), 0);
                }
            }
        }

        for (BlockData b : blocks.values()) {
            int light = BlockType.getLight(b.type);
            if (light > 0) {
                int old = lightData[b.lx][b.wy][b.lz];
                lightData[b.lx][b.wy][b.lz] =
                        LightEngine.pack(LightEngine.skyOf(old), light);
                queueBuf[tail * 3] = b.lx;
                queueBuf[tail * 3 + 1] = b.wy;
                queueBuf[tail * 3 + 2] = b.lz;
                tail = (tail + 1) % QUEUE_CAP;
            }
        }

        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (head != tail) {
            int lx = queueBuf[head * 3];
            int y = queueBuf[head * 3 + 1];
            int lz = queueBuf[head * 3 + 2];
            head = (head + 1) % QUEUE_CAP;

            int cur = LightEngine.blockOf(lightData[lx][y][lz]);
            int next = cur - 1;
            if (next <= 0) continue;

            for (int[] d : dirs) {
                int nx = lx + d[0];
                int ny = y + d[1];
                int nz = lz + d[2];
                if (nx < 0 || nx >= CHUNK_SIZE || nz < 0 || nz >= CHUNK_SIZE) continue;
                if (ny < 0 || ny > 255) continue;
                if (blocks.containsKey(packLocalKey(nx, ny, nz))) continue;

                int old = lightData[nx][ny][nz];
                if (LightEngine.blockOf(old) < next) {
                    lightData[nx][ny][nz] =
                            LightEngine.pack(LightEngine.skyOf(old), next);
                    queueBuf[tail * 3] = nx;
                    queueBuf[tail * 3 + 1] = ny;
                    queueBuf[tail * 3 + 2] = nz;
                    tail = (tail + 1) % QUEUE_CAP;
                }
            }
        }
    }

    private float sampleVertexLight(BlockData block, int face, float vx, float vy, float vz) {
        int nx = FACE_DX[face];
        int ny = FACE_DY[face];
        int nz = FACE_DZ[face];

        int ox = (int) (vx * 2);
        int oy = (int) (vy * 2);
        int oz = (int) (vz * 2);

        int sx = block.wx + nx;
        int sy = block.wy + ny;
        int sz = block.wz + nz;

        int[] xs, ys, zs;
        if (nx != 0) {
            xs = new int[]{sx, sx, sx, sx};
            ys = new int[]{block.wy, block.wy + oy, block.wy, block.wy + oy};
            zs = new int[]{block.wz, block.wz, block.wz + oz, block.wz + oz};
        } else if (ny != 0) {
            xs = new int[]{block.wx, block.wx + ox, block.wx, block.wx + ox};
            ys = new int[]{sy, sy, sy, sy};
            zs = new int[]{block.wz, block.wz, block.wz + oz, block.wz + oz};
        } else {
            xs = new int[]{block.wx, block.wx + ox, block.wx, block.wx + ox};
            ys = new int[]{block.wy, block.wy + oy, block.wy, block.wy + oy};
            zs = new int[]{sz, sz, sz, sz};
        }

        int lightSum = 0;
        int occluded = 0;

        for (int i = 0; i < 4; i++) {
            int packed = ChunkManager.getPackedLight(xs[i], ys[i], zs[i]);
            int sky = LightEngine.skyOf(packed);
            int blk = LightEngine.blockOf(packed);
            lightSum += Math.max(sky, blk);

            if (nx != 0 || nz != 0) {
                if (ChunkManager.getBlock(xs[i], ys[i], zs[i]) != null) {
                    occluded++;
                }
            }
        }

        float avgLight = (lightSum / 4f) / LightEngine.MAX_LIGHT;
        float ao = 1.0f - occluded * 0.2f;
        if (ao < 0.2f) ao = 0.2f;

        return avgLight * ao;
    }

    public void rebuildMesh() {
        for (int vao : vaoMap.values()) {
            glDeleteVertexArrays(vao);
        }
        vaoMap.clear();
        vertexCountMap.clear();

        if (blocks.isEmpty()) {
            dirty = false;
            return;
        }

        Map<String, List<Float>> groups = new HashMap<>();

        for (BlockData block : blocks.values()) {
            for (int face = 0; face < 6; face++) {
                int nx = block.wx + FACE_DX[face];
                int ny = block.wy + FACE_DY[face];
                int nz = block.wz + FACE_DZ[face];

                if (ChunkManager.getBlock(nx, ny, nz) != null) continue;

                String texName = block.faces[face];
                List<Float> data = groups.computeIfAbsent(texName, k -> new ArrayList<>());

                int[] faceVerts = Flantyle.FACES[face];
                float[] uv = Flantyle.FACE_UV[face];

                int[] triOrder = {0, 1, 2, 0, 2, 3};
                float tx = FACE_DX[face] / 2f;
                float ty = FACE_DY[face] / 2f;
                float tz = FACE_DZ[face] / 2f;

                for (int t : triOrder) {
                    int vIdx = faceVerts[t];
                    float px = Flantyle.VERTICES[vIdx * 3];
                    float py = Flantyle.VERTICES[vIdx * 3 + 1];
                    float pz = Flantyle.VERTICES[vIdx * 3 + 2];

                    float light = sampleVertexLight(block, face, px, py, pz);

                    data.add(px + block.wx);
                    data.add(py + block.wy);
                    data.add(pz + block.wz);
                    data.add(uv[t * 2]);
                    data.add(uv[t * 2 + 1]);
                    data.add(tx);
                    data.add(ty);
                    data.add(tz);
                    data.add(light);
                }
            }
        }

        for (Map.Entry<String, List<Float>> e : groups.entrySet()) {
            String texName = e.getKey();
            List<Float> list = e.getValue();
            float[] arr = new float[list.size()];
            for (int i = 0; i < arr.length; i++) arr[i] = list.get(i);

            int vao = createVao(arr);

            vaoMap.put(texName, vao);
            vertexCountMap.put(texName, arr.length / 9);
        }

        dirty = false;
    }

    private static int createVao(float[] verts) {
        int vao = glGenVertexArrays();
        glBindVertexArray(vao);

        int vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        FloatBuffer fb = BufferUtils.createFloatBuffer(verts.length);
        fb.put(verts).flip();
        glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);

        int stride = 9 * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 3, GL_FLOAT, false, stride, 5L * Float.BYTES);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(3, 1, GL_FLOAT, false, stride, 8L * Float.BYTES);
        glEnableVertexAttribArray(3);

        glBindVertexArray(0);
        return vao;
    }

    public static class BlockData {
        public final int wx, wy, wz;
        public final int lx, ly, lz;
        public final String type;
        public final String[] faces;

        public BlockData(int wx, int wy, int wz,
                         int lx, int ly, int lz,
                         String type, String[] faces) {
            this.wx = wx;
            this.wy = wy;
            this.wz = wz;
            this.lx = lx;
            this.ly = ly;
            this.lz = lz;
            this.type = type;
            this.faces = faces;
        }
    }
}