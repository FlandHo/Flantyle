#include <math.h>       // 提供 floor() 函数

// 配置参数
int SEED = 42;          // 世界种子
float SCALE = 0.05f;    // 噪声缩放（越小地形越平缓）
int HEIGHT_SCALE = 35;  // 最大高度

// 核心噪声函数
// 1. 哈希函数：将两个整数混合成一个伪随机整数
//    输入：x, y 网格坐标
//    输出：0 ~ 2^32 之间的随机整数
unsigned int hash(unsigned int x, unsigned int y) {
    unsigned int h = x * 374761393 + y * 668265263;
    h = (h ^ (h >> 13)) * 1274126177;
    return h ^ (h >> 16);
}

// 2. 二维伪随机数：返回 [0, 1] 范围的浮点数
//    输入：网格坐标 (x, y)，种子 seed
//    输出：0.0 ~ 1.0 的伪随机值
float random2D(int x, int y, int seed) {
    unsigned int h = hash(x + seed, y + seed);
    return (h & 0x7fffffff) / 2147483647.0f;
}

// 3. 平滑噪声：使用双线性插值生成连续噪声值
//    输入：浮点坐标 (x, y)
//    输出：0.0 ~ 1.0 之间的平滑值
float smoothNoise(float x, float y) {
    // 找到包含该点的网格单元
    int x0 = (int)floor(x), x1 = x0 + 1;
    int y0 = (int)floor(y), y1 = y0 + 1;
    
    // 计算在网格内的局部位置 (0~1)
    float fx = x - x0, fy = y - y0;
    
    // 平滑插值曲线（让过渡更自然）
    fx = fx * fx * (3 - 2 * fx);
    fy = fy * fy * (3 - 2 * fy);
    
    // 获取四个角的随机值
    float v00 = random2D(x0, y0, SEED);
    float v10 = random2D(x1, y0, SEED);
    float v01 = random2D(x0, y1, SEED);
    float v11 = random2D(x1, y1, SEED);
    
    // 双线性插值
    float top    = v00 * (1 - fx) + v10 * fx;
    float bottom = v01 * (1 - fx) + v11 * fx;
    return top * (1 - fy) + bottom * fy;
}

// 导出给 Python 调用的函数（必须用 extern "C" 包装）
extern "C" {

    // Windows 导出宏
    #ifdef _WIN32
    #define EXPORT __declspec(dllexport)
    #else
    #define EXPORT
    #endif

    // 主生成函数：生成高度图并填充到 data 数组
    // 参数：
    //   data:     输出数组指针（float*），由 Python 分配
    //   size:     世界边长（正方形）
    //   scale:    噪声缩放
    //   height:   最大高度
    //   seed:     随机种子
    EXPORT void world_generator_c(float* data, int size, float scale, int height, int seed) {
        // 将种子保存到全局变量，供 smoothNoise 使用
        SEED = seed;
        SCALE = scale;
        HEIGHT_SCALE = height;
        
        // 遍历所有 (x, z) 坐标
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                // 计算归一化坐标
                float fx = (float)x * scale;
                float fz = (float)z * scale;
                
                // 获取噪声值 [0, 1]
                float noise_val = smoothNoise(fx, fz);
                
                // 映射到 0 ~ height 之间的整数高度
                float block_y = noise_val * height;
                
                // 写入输出数组（按行存储）
                data[x * size + z] = block_y;
            }
        }
    }
}