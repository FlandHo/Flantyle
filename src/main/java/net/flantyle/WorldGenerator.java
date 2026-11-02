package net.flantyle;

import java.util.Random;

public class WorldGenerator {
    private static final double NOISE_SCALE = 0.025;
    private static final double MOUNTAIN_NOISE_SCALE = 0.012;
    private static final double MASK_NOISE_SCALE = 0.005;

    private static final int OCTAVES = 4;
    private static final double PERSISTENCE = 0.5;
    private static final double LACUNARITY = 2.0;

    private static final double PLAIN_WEIGHT = 0.35;
    private static final double MOUNTAIN_WEIGHT = 1.0;

    private static final double MASK_LOW = 0.45;
    private static final double MASK_HIGH = 0.65;

    private static final double SOIL_NOISE_SCALE = 0.07;
    private static final double SOIL_DETAIL_SCALE = 0.25;
    private static final double SOIL_DETAIL_STRENGTH = 0.35;
    private static final int SOIL_MAX_DEPTH = 6;
    private static final double SOIL_ZERO_THRESHOLD = 0.15;
    private static final double SOIL_CURVE = 1.6;

    private static long worldSeed = 0;
    private static final int[] p = new int[512];

    static {
        setSeed(0);
    }

    public static void setSeed(long seed) {
        worldSeed = seed;

        int[] perm = new int[256];
        for (int i = 0; i < 256; i++) perm[i] = i;

        Random rng = new Random(seed);
        for (int i = 255; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int temp = perm[i];
            perm[i] = perm[j];
            perm[j] = temp;
        }

        for (int i = 0; i < 256; i++) {
            p[i] = perm[i];
            p[256 + i] = perm[i];
        }

        System.out.println("[INFO-WORLD] Seed set to " + seed);
    }

    public static long getSeed() {
        return worldSeed;
    }

    public static void generateChunk(ChunkSystem chunkSystem) {
        int cx = chunkSystem.getCx();
        int cz = chunkSystem.getCz();

        int minH = Flantyle.WORLD_MIN_HEIGHT;
        int maxH = Flantyle.WORLD_MAX_HEIGHT;
        int range = maxH - minH;

        for (int lx = 0; lx < ChunkSystem.CHUNK_SIZE; lx++) {
            for (int lz = 0; lz < ChunkSystem.CHUNK_SIZE; lz++) {

                int rawWx = cx * ChunkSystem.CHUNK_SIZE + lx;
                int rawWz = cz * ChunkSystem.CHUNK_SIZE + lz;

                double base = fbm(rawWx * NOISE_SCALE, 0, rawWz * NOISE_SCALE);
                double baseN = (base + 1) * 0.5;

                double ridge = ridgedFbm(rawWx * MOUNTAIN_NOISE_SCALE, 0,
                        rawWz * MOUNTAIN_NOISE_SCALE);

                double mask = fbm(rawWx * MASK_NOISE_SCALE, 0,
                        rawWz * MASK_NOISE_SCALE);
                double maskN = (mask + 1) * 0.5;
                double mMask = smoothstep(MASK_LOW, MASK_HIGH, maskN);

                double mixed = baseN * PLAIN_WEIGHT
                        + ridge * mMask * MOUNTAIN_WEIGHT;

                if (mixed < 0) mixed = 0;
                if (mixed > 1) mixed = 1;

                int height = minH + (int) (mixed * range);

                double soilN = fbm(rawWx * SOIL_NOISE_SCALE, 0,
                        rawWz * SOIL_NOISE_SCALE);
                soilN = (soilN + 1) * 0.5;

                double soilDetail = fbm(rawWx * SOIL_DETAIL_SCALE, 0,
                        rawWz * SOIL_DETAIL_SCALE);
                soilDetail = (soilDetail + 1) * 0.5;
                soilN = soilN * (1.0 - SOIL_DETAIL_STRENGTH)
                        + soilDetail * SOIL_DETAIL_STRENGTH;

                int soilDepth;
                if (soilN < SOIL_ZERO_THRESHOLD) {
                    soilDepth = 0;
                } else {
                    double t = (soilN - SOIL_ZERO_THRESHOLD)
                            / (1.0 - SOIL_ZERO_THRESHOLD);
                    t = Math.pow(t, SOIL_CURVE);
                    soilDepth = (int) (t * SOIL_MAX_DEPTH);
                }

                for (int rawY = minH; rawY <= height; rawY++) {
                    int wx = rawWx * 2;
                    int wy = rawY * 2;
                    int wz = rawWz * 2;

                    String type;
                    if (rawY == height) {
                        type = "grass_block";
                    } else if (height - rawY <= soilDepth) {
                        type = "dirt";
                    } else {
                        type = "stone";
                    }
                    chunkSystem.chunkBlockAppend(wx, wy, wz, type);
                }
            }
        }
    }

    private static double fbm(double x, double y, double z) {
        double total = 0;
        double amplitude = 1;
        double frequency = 1;
        double maxAmplitude = 0;

        for (int i = 0; i < OCTAVES; i++) {
            total += noise(x * frequency, y * frequency, z * frequency) * amplitude;
            maxAmplitude += amplitude;
            amplitude *= PERSISTENCE;
            frequency *= LACUNARITY;
        }

        return total / maxAmplitude;
    }

    private static double ridgedFbm(double x, double y, double z) {
        double total = 0;
        double amplitude = 1;
        double frequency = 1;
        double maxAmplitude = 0;

        for (int i = 0; i < OCTAVES; i++) {
            double n = noise(x * frequency, y * frequency, z * frequency);

            n = 1.0 - Math.abs(n);
            n = n * n;

            total += n * amplitude;
            maxAmplitude += amplitude;
            amplitude *= PERSISTENCE;
            frequency *= LACUNARITY;
        }

        double r = total / maxAmplitude;
        return Math.pow(r, 1.35);
    }

    private static double smoothstep(double edge0, double edge1, double x) {
        double t = (x - edge0) / (edge1 - edge0);
        if (t < 0) t = 0;
        if (t > 1) t = 1;
        return t * t * (3 - 2 * t);
    }

    private static double noise(double x, double y, double z) {
        int X = (int) Math.floor(x) & 255;
        int Y = (int) Math.floor(y) & 255;
        int Z = (int) Math.floor(z) & 255;

        x -= Math.floor(x);
        y -= Math.floor(y);
        z -= Math.floor(z);

        double u = fade(x);
        double v = fade(y);
        double w = fade(z);

        int A = p[X] + Y, AA = p[A] + Z, AB = p[A + 1] + Z;
        int B = p[X + 1] + Y, BA = p[B] + Z, BB = p[B + 1] + Z;

        return lerp(w, lerp(v, lerp(u, grad(p[AA], x, y, z),
                                grad(p[BA], x - 1, y, z)),
                        lerp(u, grad(p[AB], x, y - 1, z),
                                grad(p[BB], x - 1, y - 1, z))),
                lerp(v, lerp(u, grad(p[AA + 1], x, y, z - 1),
                                grad(p[BA + 1], x - 1, y, z - 1)),
                        lerp(u, grad(p[AB + 1], x, y - 1, z - 1),
                                grad(p[BB + 1], x - 1, y - 1, z - 1))));
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static double grad(int hash, double x, double y, double z) {
        int h = hash & 15;
        double u = h < 8 ? x : y;
        double v = h < 4 ? y : h == 12 || h == 14 ? x : z;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }
}