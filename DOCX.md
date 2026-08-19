# Flantyle 开发文档

## 可修改的定义

大多数可以修改的全局定义都位于主类 **`Flantyle`** 中的 **`__init__`** 构造方法中。但不是任何数据都可修改，我们不建议在非必要时刻更改任何与渲染相关的数据或故意留空的数据键，防止错误发生。


### 几何方块数据

* #### **`self.vertices`**：此列表定义了基本立方体 **8 个顶点**的坐标

* #### **`self.faces`**：此列表定义了 **6 个面**的顶点索引组合

* #### **`self.face_uv`**：此列表定义了 **6 个面**的纹理坐标（UV 坐标），用于将 2D 纹理映射到 3D 方块表面


### 纹理与方块数据

* #### **`self.texture_map`**：纹理名称到 OpenGL 纹理 ID 的映射表。由 `load_texture` 方法填充，通常不需要手动修改

* #### **`self.blocks`**：存储所有方块数据的列表。当前版本已废弃，保留仅为兼容旧代码

* #### **`self.batch_mode`**：批量添加模式开关。当前版本已废弃

* #### **`self.batch_blocks`**：批量添加时的临时存储列表。当前版本已废弃


### VBO 渲染

* #### **`self.vbo_groups`**：纹理 ID 到 VBO 数据的映射。由 `rebuild_vbo` 方法构建，不建议手动修改

* #### **`self.vbo_needs_rebuild`**：标记 VBO 是否需要重建。当方块数据发生变化时，此标志会被设为 `True`，在下一帧渲染时触发重建

* #### **`self.block_set`**：存储所有方块坐标的集合 `(x, y, z)`，用于快速碰撞检测


### 玩家物理（常用调整）

* #### **`self.cam_pos`**：玩家脚底位置的世界坐标 `(x, y, z)`，修改此值可改变玩家的初始出生点

* #### **`self.velocity`**：玩家的速度向量 `(vx, vy, vz)`，通常由物理系统自动更新，不建议手动修改

* #### **`self.cam_yaw`**：玩家的水平旋转角（左右转头），单位为弧度

* #### **`self.cam_pitch`**：玩家的垂直旋转角（上下看），单位为弧度，范围限制在 `[-π/2.2, π/2.2]`

* #### **`self.player_speed`**：玩家水平移动速度，单位为格/秒，计算方式为 `self.cam_pos[0] += move_vec[0] * self.player_speed * dt`

* #### **`self.gravity`**：重力加速度，作用于玩家垂直速度，必须为负数

* #### **`self.jump_speed`**：跳跃时施加的垂直初速度，单位为格/秒

* #### **`self.is_on_ground`**：标记玩家是否站在地面上，由物理系统自动更新

* #### **`self.player_height`**：玩家碰撞箱的高度，当前值为 `3.6`（实际视觉高度为 `1.8` 格，因为坐标缩放 2 倍）

* #### **`self.player_width`**：玩家碰撞箱的水平宽度，当前值为 `1.0`（实际视觉宽度为 `0.5` 格）

* #### **`self.mouse_sensitivity`**：鼠标灵敏度，值越大视角旋转越快


### 窗口与输入

* #### **`self.win_width`**：游戏窗口的宽度，单位为像素

* #### **`self.win_height`**：游戏窗口的高度，单位为像素

* #### **`self.center_x`**：窗口中心点的 X 坐标，用于鼠标锁定，自动计算

* #### **`self.center_y`**：窗口中心点的 Y 坐标，用于鼠标锁定，自动计算

* #### **`self.keys`**：按键状态字典，键为按键名称，值为布尔值（是否按下）

* #### **`self.window`**：GLFW 窗口对象，由 `glfw.create_window` 创建

* #### **`self._last_cursor_pos`**：鼠标上一帧的位置缓存，用于计算鼠标移动增量，由 `mouse_motion_callback` 自动更新

* #### **`self.highlight_block`**：当前被瞄准的方块坐标 `(x, y, z)`，由射线检测更新，用于绘制高亮线框

----

## 方法定义


### 日志与资源加载

* #### **`get_log_time()`**：获取当前时间字符串，格式为 `HH:MM:SS`，用于日志输出

* #### **`load_texture(path)`**：加载图片并生成 OpenGL 纹理，返回纹理 ID


### 区块与坐标

* #### **`get_chunk(cx, cz)`**：获取或创建指定坐标的区块，返回 `Chunk` 对象

* #### **`world_to_local(wx, wy, wz)`**：将世界坐标转换为区块局部坐标，返回 `(cx, cz, lx, ly, lz)`

* #### **`is_block_at(wx, wy, wz)`**：检查指定世界坐标是否存在方块，返回 `bool`


### 方块操作

* #### **`chunk_block_append(wx, wy, wz, tex_1, tex_2, tex_3, tex_4, tex_5, tex_6)`**：在世界坐标处添加一个方块，自动计算所属区块

* #### **`setblocks_append(x, y, z, face_0, face_1, face_2, face_3, face_4, face_5)`**：添加方块，与 `chunk_block_append` 功能相同

* #### **`remove_block(wx, wy, wz)`**：移除指定世界坐标的方块，返回是否成功


### 世界生成

* #### **`generate_world(size_in_chunks, height_scale, seed)`**：使用噪声算法生成地形


### VBO 与渲染

* #### **`rebuild_chunk_vbo(chunk)`**：重建单个区块的 VBO

* #### **`render_chunks()`**：渲染所有需要更新的区块


### 窗口回调

* #### **`reshape_callback(win, w, h)`**：窗口尺寸变化时更新视口和投影矩阵

* #### **`mouse_motion_callback(win, x, y)`**：鼠标移动时更新玩家视角

* #### **`mouse_button_callback(win, button, action, mods)`**：鼠标点击时触发方块移除


### 物理与碰撞

* #### **`get_player_aabb(pos)`**：获取玩家碰撞箱的 AABB 范围，返回 `(min, max)`

* #### **`is_colliding_with_block(pos)`**：检测玩家在指定位置是否与方块碰撞，返回 `bool`

* #### **`resolve_collision_mc(target)`**：处理玩家与方块的碰撞响应

* #### **`physics_update(dt)`**：更新物理状态（重力、速度、碰撞、跳跃）

* #### **`ensure_player_not_stuck()`**：如果玩家卡在方块内，向上传送至安全位置


### 射线与瞄准

* #### **`get_target_block(max_dist)`**：从玩家眼睛发射射线，检测瞄准的方块，返回 `(block_pos, face_dir)` 或 `(None, None)`

* #### **`draw_highlight_box()`**：绘制瞄准方块的白色高亮线框

* #### **`draw_crosshair()`**：在屏幕中心绘制准星


### 主循环

* #### **`draw_frame(dt)`**：执行单帧渲染（物理 → 渲染 → 交换缓冲区）

* #### **`cleanup()`**：释放 OpenGL 资源并销毁窗口

* #### **`main()`**：程序入口，初始化窗口、加载资源、启动主循环

### 补充说明

- **渲染相关数据**（`vertices`、`faces`、`face_uv`）不建议随意修改，除非完全理解 OpenGL 顶点排列和纹理映射原理。
- **物理参数**（`gravity`、`jump_speed`、`player_speed`）可以在游戏运行前调整以获得不同的操作手感。
- **窗口尺寸**（`win_width`、`win_height`）建议通过 `reshape_callback` 动态调整，而不是直接修改变量。
- 所有以 `_` 开头的变量（如 `_last_cursor_pos`）仅供内部使用，请勿在外部修改。