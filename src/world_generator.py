import os
import ctypes
import numpy as np
from log_time import get_log_time

# 加载 C++ DLL（如果存在）
DLL_PATH = os.path.join(os.path.dirname(__file__), "world_generator.dll")

try:
    _dll = ctypes.CDLL(DLL_PATH)
    _dll.world_generator_c.argtypes = [
        ctypes.POINTER(ctypes.c_float),  # float* data
        ctypes.c_int,                    # int size
        ctypes.c_float,                  # float scale
        ctypes.c_int,                    # int height
        ctypes.c_int                     # int seed
    ]
    _dll.world_generator_c.restype = None
    _HAS_CPP_DLL = True
    print(f"[{get_log_time()}] [world/info] C++ DLL loaded successfully")
except Exception as e:
    _HAS_CPP_DLL = False
    print(f"[{get_log_time()}] [world/warning] C++ DLL not found, falling back to Python noise")
    print(f"[{get_log_time()}] [world/warning] Error: {e}")

# 默认参数
DEFAULT_SIZE = 512
DEFAULT_SCALE = 0.08
DEFAULT_HEIGHT_SCALE = 35
DEFAULT_SEED = 42
PROGRESS_INTERVAL = 10000

# 使用C++ DLL生成高度图
def generate_heightmap_cpp(size, scale, height_scale, seed):
    """调用C++ DLL生成高度图，返回 numpy 数组"""
    if not _HAS_CPP_DLL:
        raise RuntimeError("C++ DLL not available")
    
    heightmap = np.zeros((size, size), dtype=np.float32)
    _dll.world_generator_c(
        heightmap.ctypes.data_as(ctypes.POINTER(ctypes.c_float)),
        size,
        scale,
        height_scale,
        seed
    )
    return heightmap

# 纯Python噪声生成（备选方案，DLL加载失败时使用）
def generate_heightmap_python(size, scale, height_scale, seed):
    """纯Python版高度图生成（备选）"""
    from opensimplex import OpenSimplex
    noise_gen = OpenSimplex(seed=seed)
    heightmap = np.zeros((size, size), dtype=np.float32)
    
    for x in range(size):
        for z in range(size):
            y = noise_gen.noise2(x * scale, z * scale)
            heightmap[x, z] = int((y + 1) / 2 * height_scale)
            if (x * size + z) % 10000 == 0:
                print(f"[{get_log_time()}] [world/progress] Height map: {x * size + z}/{size * size} columns")
    
    return heightmap

# 根据高度图生成方块（公共逻辑）
def build_blocks_from_heightmap(block_adder, heightmap, size):
    """从高度图数组生成方块，包含草/泥土/石头分层"""
    total_columns = size * size
    count = 0
    total_blocks = 0
    
    for x in range(size):
        for z in range(size):
            top_y = int(heightmap[x, z])
            if top_y < 0:
                top_y = 0
            
            # 从地表向下填充到 y=0
            for block_y in range(top_y, -1, -1):
                offset = top_y - block_y
                total_blocks += 1
                
                if offset == 0:
                    # 地表：草地
                    block_adder(
                        x, block_y, z,
                        face_0="grass", face_1="grass", face_2="grass",
                        face_3="grass", face_4="grass_top", face_5="dirt"
                    )
                elif offset <= 3:
                    # 次表层：泥土
                    block_adder(
                        x, block_y, z,
                        face_0="dirt", face_1="dirt", face_2="dirt",
                        face_3="dirt", face_4="dirt", face_5="dirt"
                    )
                else:
                    # 深层：石头
                    block_adder(
                        x, block_y, z,
                        face_0="stone", face_1="stone", face_2="stone",
                        face_3="stone", face_4="stone", face_5="stone"
                    )
            
            count += 1
            if count % PROGRESS_INTERVAL == 0:
                print(f"[{get_log_time()}] [world/progress] Block generation: {count}/{total_columns} columns")
    
    print(f"[{get_log_time()}] [world/info] Total blocks generated: ~{total_blocks}")

# 地形生成入口（由 flantyle.py 调用）
def world_generator(block_adder, size=DEFAULT_SIZE, scale=DEFAULT_SCALE,
                    height_scale=DEFAULT_HEIGHT_SCALE, seed=DEFAULT_SEED,
                    force_python=False):
    """
    生成地形的主入口。
    
    参数:
        block_adder: 主程序的 setblocks_append 方法
        size: 世界边长
        scale: 噪声缩放
        height_scale: 最大高度
        seed: 随机种子
        force_python: 强制使用Python版本（调试用）
    """
    print(f"[{get_log_time()}] [world/info]: World is generating...")
    
    # 决定使用哪种方式生成高度图
    use_cpp = _HAS_CPP_DLL and not force_python
    
    if use_cpp:
        print(f"[{get_log_time()}] [world/info] Using C++ DLL for noise generation (seed={seed})")
        heightmap = generate_heightmap_cpp(size, scale, height_scale, seed)
    else:
        print(f"[{get_log_time()}] [world/info] Using Python noise generation (seed={seed})")
        heightmap = generate_heightmap_python(size, scale, height_scale, seed)
    
    # 打印高度图统计信息
    print(f"[{get_log_time()}] [world/info] Heightmap generated: min={heightmap.min():.2f}, max={heightmap.max():.2f}, avg={heightmap.mean():.2f}")
    
    # 根据高度图生成方块
    build_blocks_from_heightmap(block_adder, heightmap, size)
    
    print(f"[{get_log_time()}] [world/info] World generation completed")

# 独立测试（如果直接运行此文件）
if __name__ == "__main__":
    def mock_block_adder(x, y, z, face_0="", face_1="", face_2="", face_3="", face_4="", face_5=""):
        print(f"Block at ({x}, {y}, {z})")
    
    world_generator(mock_block_adder, size=16, force_python=False)