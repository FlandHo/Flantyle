package net.flantyle;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ResourceRegistry {
    private static final Map<String, String> resourceTextureRegister = new HashMap<>();

    public static void registerTextures(String registryName, String registryPath) {
        resourceTextureRegister.put(registryName, registryPath);
    }


    public static void registerAll() {
        registerTextures("bedrock", "/textures/bedrock.png");
        registerTextures("dirt", "/textures/dirt.png");
        registerTextures("grass_block", "/textures/grass_block.png");
        registerTextures("grass_block_top", "/textures/grass_block_top.png");
        registerTextures("stone", "/textures/stone.png");
        registerTextures("oak", "/textures/oak.png");
        registerTextures("oak_top", "/textures/oak_top.png");
    }

    public static String getPath(String registryName) {
        return resourceTextureRegister.get(registryName);
    }

    public static Set<String> getAllNames() {
        return resourceTextureRegister.keySet();
    }
}