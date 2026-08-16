from OpenGL.GL import *
from OpenGL.GLU import *
from PIL import Image
from log_time import get_log_time
from textures import texture_registry, TEXTURE_NAME_MAP
import math
import numpy as np
import ctypes
import glfw

class Flantyle(object):
    def __init__(self):
        # ========== 几何数据 ==========
        self.vertices = [
            (-1, -1, -1), (1, -1, -1), (1, 1, -1), (-1, 1, -1),
            (-1, -1, 1), (1, -1, 1), (1, 1, 1), (-1, 1, 1)
        ]
        self.faces = [
            (4,5,6,7),  # 前
            (0,1,2,3),  # 后
            (0,4,7,3),  # 左
            (1,5,6,2),  # 右
            (3,2,6,7),  # 顶
            (0,1,5,4)   # 底
        ]
        self.face_uv = [
            [(0,1),(1,1),(1,0),(0,0)],
            [(0,1),(1,1),(1,0),(0,0)],
            [(0,1),(1,1),(1,0),(0,0)],
            [(0,1),(1,1),(1,0),(0,0)],
            [(0,1),(1,1),(1,0),(0,0)],
            [(0,1),(1,1),(1,0),(0,0)],
        ]

        self.texture_map = {}
        self.blocks = []
        self.batch_mode = False
        self.batch_blocks = []
        self.vbo_groups = {}
        self.vbo_needs_rebuild = True
        self.block_set = set()

        # ========== 玩家物理参数 ==========
        self.cam_pos = [0.0, 30.0, 0.0]          # 脚底位置
        self.velocity = [0.0, 0.0, 0.0]
        self.cam_yaw = 0.0
        self.cam_pitch = 0.0
        self.player_speed = 8.0
        self.gravity = -25.0                    # 重力加速度
        self.jump_speed = 11.0                  # 跳跃初速度
        self.is_on_ground = False
        self.player_height = 3.6                # 玩家高度（正常）
        self.player_width = 0.6                 # 玩家水平宽度

        self.mouse_sensitivity = 0.005

        self.win_width = 800
        self.win_height = 600
        self.center_x = self.win_width // 2
        self.center_y = self.win_height // 2

        self.keys = {
            b'w': False, b'a': False, b's': False, b'd': False,
            b' ': False, b'q': False
        }
        self.window = None
        self._last_cursor_pos = None

        self.main()

    # -------------------- 纹理加载 --------------------
    def load_texture(self, path):
        img = Image.open(path).convert('RGB')
        width, height = img.size
        tex_id = glGenTextures(1)
        glBindTexture(GL_TEXTURE_2D, tex_id)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, width, height, 0, GL_RGB, GL_UNSIGNED_BYTE, img.tobytes())
        return tex_id

    # -------------------- 方块管理 --------------------
    def block_upload(self, mode=True):
        if mode:
            self.batch_mode = True
            self.batch_blocks = []
            print(f"[{get_log_time()}] [info]: Batch mode ON")
        else:
            self.batch_mode = False
            if self.batch_blocks:
                self.blocks.extend(self.batch_blocks)
                self.batch_blocks = []
                # 延迟 VBO 构建，标记需要重建
                self.vbo_needs_rebuild = True
                print(f"[{get_log_time()}] [info]: Batch mode OFF, {len(self.blocks)} blocks total")
                # 构建 block_set 用于碰撞检测
                self.block_set = {(bx, by, bz) for (bx, by, bz, _) in self.blocks}
            else:
                print(f"[{get_log_time()}] [info]: Batch mode OFF, no blocks")

    def setblocks_append(self, x, y, z,
                         face_0="error", face_1="error", face_2="error",
                         face_3="error", face_4="error", face_5="error"):
        tex_names = [face_0, face_1, face_2, face_3, face_4, face_5]
        tex_ids = []
        for name in tex_names:
            tid = self.texture_map.get(name)
            if tid is None:
                tid = self.texture_map.get("error")
            tex_ids.append(tid)
        x *= 2; y *= 2; z *= 2
        block = (x, y, z, tex_ids)
        if self.batch_mode:
            self.batch_blocks.append(block)
        else:
            self.blocks.append(block)
            self.vbo_needs_rebuild = True

    # -------------------- VBO 构建（优化版） --------------------
    def rebuild_vbo(self):
        for tex_id, (vbo_id, _) in self.vbo_groups.items():
            glDeleteBuffers(1, [vbo_id])
        self.vbo_groups = {}

        if not self.blocks:
            print(f"[{get_log_time()}] [info]: No blocks to build VBO")
            return

        # 使用局部变量加速
        verts = self.vertices
        face_uv = self.face_uv
        block_positions = {(bx, by, bz) for (bx, by, bz, _) in self.blocks}
        temp_groups = {}
        tex_map = self.texture_map

        for (bx, by, bz, tex_ids) in self.blocks:
            neighbors = [
                (bx, by, bz + 2), (bx, by, bz - 2),
                (bx - 2, by, bz), (bx + 2, by, bz),
                (bx, by + 2, bz), (bx, by - 2, bz),
            ]

            for i, face in enumerate(self.faces):
                if neighbors[i] in block_positions:
                    continue

                tex = tex_ids[i]
                if tex is None:
                    tex = tex_map.get("error")
                if tex is None:
                    continue

                v0, v1, v2, v3 = face
                uv = face_uv[i]
                pts = [
                    (verts[v0][0] + bx, verts[v0][1] + by, verts[v0][2] + bz),
                    (verts[v1][0] + bx, verts[v1][1] + by, verts[v1][2] + bz),
                    (verts[v2][0] + bx, verts[v2][1] + by, verts[v2][2] + bz),
                    (verts[v3][0] + bx, verts[v3][1] + by, verts[v3][2] + bz),
                ]

                if tex not in temp_groups:
                    temp_groups[tex] = []
                group = temp_groups[tex]
                for j in range(4):
                    group.extend([pts[j][0], pts[j][1], pts[j][2], uv[j][0], uv[j][1]])

        total_vertices = 0
        for tex_id, vertex_data in temp_groups.items():
            if not vertex_data:
                continue
            arr = np.array(vertex_data, dtype=np.float32)
            vbo_id = glGenBuffers(1)
            glBindBuffer(GL_ARRAY_BUFFER, vbo_id)
            glBufferData(GL_ARRAY_BUFFER, arr.nbytes, arr, GL_STATIC_DRAW)
            glBindBuffer(GL_ARRAY_BUFFER, 0)
            vertex_count = len(vertex_data) // 5
            self.vbo_groups[tex_id] = (vbo_id, vertex_count)
            total_vertices += vertex_count

        print(f"[{get_log_time()}] [info]: VBO built: {len(self.vbo_groups)} texture groups, {total_vertices} vertices")
        self.vbo_needs_rebuild = False

    # -------------------- 窗口回调 --------------------
    def reshape_callback(self, window, width, height):
        if height == 0:
            height = 1
        self.win_width = width
        self.win_height = height
        self.center_x = width // 2
        self.center_y = height // 2
        glViewport(0, 0, width, height)
        glMatrixMode(GL_PROJECTION)
        glLoadIdentity()
        gluPerspective(45, width / height, 0.1, 200.0)
        glMatrixMode(GL_MODELVIEW)

    def mouse_motion_callback(self, window, xpos, ypos):
        if self._last_cursor_pos is None:
            self._last_cursor_pos = (xpos, ypos)
            return
        last_x, last_y = self._last_cursor_pos
        dx = xpos - last_x
        dy = ypos - last_y
        self._last_cursor_pos = (xpos, ypos)

        self.cam_yaw -= dx * self.mouse_sensitivity
        self.cam_pitch -= dy * self.mouse_sensitivity
        self.cam_pitch = max(-math.pi/2.2, min(math.pi/2.2, self.cam_pitch))

    def key_callback(self, window, key, scancode, action, mods):
        key_map = {
            glfw.KEY_W: b'w', glfw.KEY_A: b'a', glfw.KEY_S: b's', glfw.KEY_D: b'd',
            glfw.KEY_SPACE: b' ', glfw.KEY_LEFT_SHIFT: b'lshift',
        }
        if key in key_map:
            k = key_map[key]
            if action == glfw.PRESS:
                self.keys[k] = True
            elif action == glfw.RELEASE:
                self.keys[k] = False

    # ======================== 物理与碰撞（优化版） ========================

    def get_player_aabb(self, pos):
        """返回玩家碰撞箱 (min, max)"""
        half = self.player_width / 2
        min_ = (pos[0] - half, pos[1], pos[2] - half)
        max_ = (pos[0] + half, pos[1] + self.player_height, pos[2] + half)
        return min_, max_

    def aabb_overlap(self, a_min, a_max, b_min, b_max):
        return (a_min[0] < b_max[0] and a_max[0] > b_min[0] and
                a_min[1] < b_max[1] and a_max[1] > b_min[1] and
                a_min[2] < b_max[2] and a_max[2] > b_min[2])

    def is_colliding_with_block(self, pos):
        """检测玩家在pos位置是否与任何方块碰撞（高效版）"""
        if not self.block_set:
            return False
        half = self.player_width / 2
        min_x = pos[0] - half
        max_x = pos[0] + half
        min_y = pos[1]
        max_y = pos[1] + self.player_height
        min_z = pos[2] - half
        max_z = pos[2] + half

        start_x = int(math.floor(min_x - 1))
        end_x = int(math.ceil(max_x + 1))
        start_y = int(math.floor(min_y - 1))
        end_y = int(math.ceil(max_y + 1))
        start_z = int(math.floor(min_z - 1))
        end_z = int(math.ceil(max_z + 1))

        for bx in range(start_x, end_x + 1):
            for by in range(start_y, end_y + 1):
                for bz in range(start_z, end_z + 1):
                    if (bx, by, bz) in self.block_set:
                        b_min = (bx - 1, by - 1, bz - 1)
                        b_max = (bx + 1, by + 1, bz + 1)
                        if self.aabb_overlap((min_x, min_y, min_z), (max_x, max_y, max_z), b_min, b_max):
                            return True
        return False

    def resolve_collision(self, target_pos):
        """逐轴移动并处理碰撞"""
        orig_x, orig_y, orig_z = self.cam_pos
        new_x, new_y, new_z = target_pos

        # X轴
        test_pos = [new_x, orig_y, orig_z]
        if not self.is_colliding_with_block(test_pos):
            self.cam_pos[0] = new_x
        else:
            self.velocity[0] = 0

        # Z轴
        test_pos = [self.cam_pos[0], self.cam_pos[1], new_z]
        if not self.is_colliding_with_block(test_pos):
            self.cam_pos[2] = new_z
        else:
            self.velocity[2] = 0

        # Y轴
        test_pos = [self.cam_pos[0], new_y, self.cam_pos[2]]
        if not self.is_colliding_with_block(test_pos):
            self.cam_pos[1] = new_y
        else:
            self.cam_pos[1] = new_y
            if self.velocity[1] < 0:  # 向下
                for _ in range(20):
                    self.cam_pos[1] += 0.01
                    if not self.is_colliding_with_block(self.cam_pos):
                        break
                if self.is_colliding_with_block(self.cam_pos):
                    self.cam_pos[1] = orig_y
                else:
                    self.is_on_ground = True
                self.velocity[1] = 0
            else:  # 向上
                for _ in range(20):
                    self.cam_pos[1] -= 0.01
                    if not self.is_colliding_with_block(self.cam_pos):
                        break
                if self.is_colliding_with_block(self.cam_pos):
                    self.cam_pos[1] = orig_y
                self.velocity[1] = 0

    def physics_update(self, dt):
        self.is_on_ground = False

        # 重力
        self.velocity[1] += self.gravity * dt
        if self.velocity[1] < -30:
            self.velocity[1] = -30

        # 水平移动
        forward = [-math.sin(self.cam_yaw), 0, -math.cos(self.cam_yaw)]
        right = [math.cos(self.cam_yaw), 0, -math.sin(self.cam_yaw)]

        move_x = 0.0
        move_z = 0.0
        if self.keys.get(b'w', False):
            move_x += forward[0]; move_z += forward[2]
        if self.keys.get(b's', False):
            move_x -= forward[0]; move_z -= forward[2]
        if self.keys.get(b'a', False):
            move_x -= right[0]; move_z -= right[2]
        if self.keys.get(b'd', False):
            move_x += right[0]; move_z += right[2]

        mag = math.sqrt(move_x**2 + move_z**2)
        if mag > 0:
            move_x /= mag
            move_z /= mag
            self.velocity[0] = move_x * self.player_speed
            self.velocity[2] = move_z * self.player_speed
        else:
            self.velocity[0] *= 0.9
            self.velocity[2] *= 0.9
            if abs(self.velocity[0]) < 0.1: self.velocity[0] = 0
            if abs(self.velocity[2]) < 0.1: self.velocity[2] = 0

        new_pos = [
            self.cam_pos[0] + self.velocity[0] * dt,
            self.cam_pos[1] + self.velocity[1] * dt,
            self.cam_pos[2] + self.velocity[2] * dt
        ]

        self.resolve_collision(new_pos)

        # 跳跃
        if self.keys.get(b' ', False) and self.is_on_ground:
            self.velocity[1] = self.jump_speed
            self.is_on_ground = False

        # 重生（掉到 y < -10）
        if self.cam_pos[1] < -10:
            self.cam_pos = [0.0, 60.0, 0.0]  # 提高到安全高度
            self.velocity = [0.0, 0.0, 0.0]
            self.ensure_player_not_stuck()   # 防止卡在方块内

    def ensure_player_not_stuck(self):
        """检查玩家是否卡在方块内，若是则向上传送"""
        max_attempts = 200
        attempts = 0
        while self.is_colliding_with_block(self.cam_pos) and attempts < max_attempts:
            self.cam_pos[1] += 0.5
            attempts += 1
        if attempts >= max_attempts:
            self.cam_pos = [0.0, 100.0, 0.0]
        if attempts > 0:
            print(f"[{get_log_time()}] [info]: Player was stuck, teleported up by {attempts * 0.5:.1f} units")

    # ======================== 渲染 ========================

    def draw_frame(self, dt):
        self.physics_update(dt)

        # 首次绘制时构建 VBO（延迟构建）
        if self.vbo_needs_rebuild:
            self.rebuild_vbo()

        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)
        glEnable(GL_DEPTH_TEST)
        glLoadIdentity()

        # 摄像机位置 = 脚底 + 眼睛高度（约 1.6 单位）
        eye_pos = [self.cam_pos[0], self.cam_pos[1] + self.player_height * 0.9, self.cam_pos[2]]

        look_dir = [
            -math.sin(self.cam_yaw) * math.cos(self.cam_pitch),
            math.sin(self.cam_pitch),
            -math.cos(self.cam_yaw) * math.cos(self.cam_pitch)
        ]
        mag = math.sqrt(look_dir[0]**2 + look_dir[1]**2 + look_dir[2]**2)
        if mag > 0:
            look_dir[0] /= mag; look_dir[1] /= mag; look_dir[2] /= mag

        center = [
            eye_pos[0] + look_dir[0],
            eye_pos[1] + look_dir[1],
            eye_pos[2] + look_dir[2]
        ]
        up = [0, 1, 0] if abs(self.cam_pitch) < math.pi/2.1 else [0, -1, 0]

        gluLookAt(eye_pos[0], eye_pos[1], eye_pos[2],
                  center[0], center[1], center[2],
                  up[0], up[1], up[2])

        if not self.vbo_groups:
            glfw.swap_buffers(self.window)
            return

        glColor3f(1.0, 1.0, 1.0)
        glEnable(GL_TEXTURE_2D)

        glEnableClientState(GL_VERTEX_ARRAY)
        glEnableClientState(GL_TEXTURE_COORD_ARRAY)

        stride = 5 * 4
        for tex_id, (vbo_id, vertex_count) in self.vbo_groups.items():
            glBindTexture(GL_TEXTURE_2D, tex_id)
            glBindBuffer(GL_ARRAY_BUFFER, vbo_id)
            glVertexPointer(3, GL_FLOAT, stride, None)
            glTexCoordPointer(2, GL_FLOAT, stride, ctypes.c_void_p(12))
            glDrawArrays(GL_QUADS, 0, vertex_count)

        glDisableClientState(GL_TEXTURE_COORD_ARRAY)
        glDisableClientState(GL_VERTEX_ARRAY)
        glDisable(GL_TEXTURE_2D)

        glfw.swap_buffers(self.window)

    def cleanup(self):
        for tex_id, (vbo_id, _) in self.vbo_groups.items():
            glDeleteBuffers(1, [vbo_id])
        self.vbo_groups.clear()
        if self.window:
            glfw.destroy_window(self.window)

    # ======================== 主循环 ========================

    def main(self):
        if not glfw.init():
            raise RuntimeError("glfw init failed")
        prev_time = glfw.get_time()

        glfw.window_hint(glfw.RESIZABLE, glfw.TRUE)
        self.window = glfw.create_window(self.win_width, self.win_height, "Flantyle - Indev Version 0.1D", None, None)
        if not self.window:
            glfw.terminate()
            raise RuntimeError("window creation failed")

        glfw.make_context_current(self.window)
        glfw.swap_interval(1)

        glfw.set_cursor_pos_callback(self.window, self.mouse_motion_callback)
        glfw.set_key_callback(self.window, self.key_callback)
        glfw.set_framebuffer_size_callback(self.window, self.reshape_callback)

        self.reshape_callback(self.window, self.win_width, self.win_height)

        glfw.set_input_mode(self.window, glfw.CURSOR, glfw.CURSOR_DISABLED)
        glfw.set_input_mode(self.window, glfw.RAW_MOUSE_MOTION, glfw.TRUE)

        glClearColor(86/255.0, 151/255.0, 223/255.0, 1.0)
        glEnable(GL_DEPTH_TEST)

        try:
            for tex_name, filename in TEXTURE_NAME_MAP.items():
                if filename in texture_registry:
                    path = texture_registry[filename]
                    tex_id = self.load_texture(path)
                    self.texture_map[tex_name] = tex_id
                else:
                    print(f"[{get_log_time()}] [Warning]: Texture {filename} not found")
            print(f"[{get_log_time()}] [info]: Textures loaded")
        except Exception as e:
            print(f"[{get_log_time()}] [Error]: {e}")
            self.texture_map = {}

        self.block_upload(True)
        import world_generator
        world_generator.world_generator(self.setblocks_append)
        self.block_upload(False)   # 关闭批量模式，标记需要重建 VBO

        # 修正玩家出生位置
        self.ensure_player_not_stuck()
        print(f"[{get_log_time()}] [world/info]: World generation completed")

        while not glfw.window_should_close(self.window):
            current_time = glfw.get_time()
            dt = current_time - prev_time
            prev_time = current_time
            if dt > 0.05:
                dt = 0.05

            self.draw_frame(dt)
            glfw.poll_events()

        self.cleanup()
        glfw.terminate()

if __name__ == "__main__":
    FlantyleMain = Flantyle()