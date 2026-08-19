Flantyle — 开发者文档
================

Flantyle 是一个使用 Python、C++ 编写的3D沙盒游戏。

快速开始
----

1.  **安装 Python 依赖：**
    
        pip install PyOpenGL PyOpenGL_accelerate Pillow numpy glfw opensimplex
    
2.  **启动程序（无需 `--run` 参数）：**
    
        python flantyle.py
    

关键文件说明
------

*   **`flantyle.py`** — 引擎核心：窗口、渲染、事件循环。
*   **`world_generator.py`** — 地形生成调度.
*   **`textures.py`** — 纹理加载与名称映射。
*   **`log_time.py`** — 统一日志时间戳。
*   **`resources/textures/`** — 纹理图片（PNG）存放目录。

*   **新增地形生成逻辑**：在 `world_generator.py` 中新增函数。
*   **新增纹理**：在 `resources/textures/` 添加 PNG，并在 `textures.py` 的 `TEXTURE_NAME_MAP` 注册。

开发提示
----

*   快速迭代时，可在 `flantyle.py` 中临时将 `size` 改为 128 以缩短生成时间。
*   日志世界前缀统一由 `get_log_time()` 提供，无需手动格式化。

功能预览
----

<img width="400" height="300" alt="image" src="https://github.com/user-attachments/assets/1b82ab35-2004-46f4-886b-5257b64c2c44" />

*图为程序生成的地形，包含草地，泥土和岩石分层*

贡献与许可证
------

*   代码风格：Python 遵循 PEP 8，C++ 缩进 4 空格，避免过度设计。
*   提交前请测试小世界（128）和大世界（512）的性能。
*   本项目使用 MIT 许可证，见仓库根目录的 `LICENSE` 文件。

文档版本：2026-08-16  |  适用于 Flantyle 开发分支
