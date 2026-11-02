package net.flantyle;

public class LightEngine {
    public static final int MAX_LIGHT = 15;

    public static int pack(int sky, int block) {
        return ((sky & 0xF) << 4) | (block & 0xF);
    }

    public static int skyOf(int packed) {
        return (packed >> 4) & 0xF;
    }

    public static int blockOf(int packed) {
        return packed & 0xF;
    }
}