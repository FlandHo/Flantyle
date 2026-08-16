# 加载资源文件
import os
from log_time import *

texture_load_done_count = 0
texture_registry = {}
TEXTURE_NAME_MAP = {
    "grass":      "grass_block.png",
    "grass_top":  "grass_block_top.png",
    "dirt":       "dirt.png",
    "stone":      "stone.png",
    "bedrock":    "bedrock.png",
    "error":      "error_texture.png",
}

resource_path = os.path.join(os.path.dirname(__file__), "resources/textures")
def textures_listdir(path):
    global texture_load_done_count
    for entry in os.listdir(path):
        full_path = os.path.join(path, entry)
        if os.path.isdir(full_path):
            textures_listdir(full_path)
        else:
            texture_name = full_path.replace(resource_path + "\\", "")
            texture_registry[texture_name] = full_path
            print(f"[{get_log_time()}] [resource/info]: Texture file: {texture_name} loaded successfully")
            texture_load_done_count += 1

textures_listdir(resource_path)
print(f"[{get_log_time()}] [resource/info]: Loaded {texture_load_done_count} textures to / Registry")