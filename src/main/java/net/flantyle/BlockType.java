package net.flantyle;

import java.util.HashMap;
import java.util.Map;

public class BlockType {
    private static final Map<String, String[]> REGISTRY = new HashMap<>();

    public static void register(String name, String... textures) {
        String[] faces = new String[6];

        switch (textures.length) {
            case 1:
                for (int i = 0; i < 6; i++) faces[i] = textures[0];
                break;
            case 2:
                faces[0] = textures[0];
                faces[1] = textures[0];
                faces[2] = textures[0];
                faces[3] = textures[0];
                faces[4] = textures[1];
                faces[5] = textures[0];
                break;
            case 3:
                faces[0] = textures[0];
                faces[1] = textures[0];
                faces[2] = textures[0];
                faces[3] = textures[0];
                faces[4] = textures[1];
                faces[5] = textures[2];
                break;
            case 6:
                System.arraycopy(textures, 0, faces, 0, 6);
                break;
            default:
                throw new IllegalArgumentException(
                        "Block requires 1, 2, 3, or 6 textures, got " + textures.length);
        }

        REGISTRY.put(name, faces);
    }

    public static int getLight(String name) {
        if (name == null) return 0;
        switch (name) {
            case "torch":      return 14;
            case "glowstone":  return 15;
            case "lava":       return 15;
            case "lantern":    return 15;
            default:           return 0;
        }
    }

    public static void registerAll() {
        register("stone", "stone");
        register("dirt", "dirt");
        register("bedrock", "bedrock");
        register("grass_block", "grass_block", "grass_block_top", "dirt");
        register("oak_log", "oak", "oak_top");
        register("oak_leaves", "stone");
    }

    public static boolean exists(String name) {
        return REGISTRY.containsKey(name);
    }

    public static String[] getFaces(String name) {
        return REGISTRY.get(name);
    }
}