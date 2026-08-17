from OpenGL.GL import *
from OpenGL.GLU import *
from PIL import Image
from log_time import get_log_time
from textures import texture_registry, TEXTURE_NAME_MAP
import math
import numpy as np
import ctypes
import glfw
from chunk import Chunk

class Flantyle:
    """主游戏类，管理窗口、渲染、物理和区块系统"""

    def __init__(self):
        # 方块几何数据
        self.vertices = [
            (-1,-1,-1), (1,-1,-1), (1,1,-1), (-1,1,-1),
            (-1,-1,1), (1,-1,1), (1,1,1), (-1,1,1)
        ]
        self.faces = [
            (4,5,6,7), (0,1,2,3), (0,4,7,3),
            (1,5,6,2), (3,2,6,7), (0,1,5,4)
        ]
        self.face_uv = [[(0,1),(1,1),(1,0),(0,0)]] * 6

        self.texture_map = {}
        self.chunks = {}          # (cx, cz) -> Chunk
        self.chunk_size = Chunk.CHUNK_SIZE

        # 玩家物理
        self.cam_pos = [0.0, 30.0, 0.0]
        self.velocity = [0.0, 0.0, 0.0]
        self.cam_yaw = 0.0
        self.cam_pitch = 0.0
        self.player_speed = 8.0
        self.gravity = -40.0
        self.jump_speed = 15.0
        self.is_on_ground = False
        self.player_height = 3.6
        self.player_width = 1.0
        self.mouse_sensitivity = 0.005

        # 窗口
        self.win_width = 800
        self.win_height = 600
        self.center_x = self.win_width // 2
        self.center_y = self.win_height // 2

        self.keys = {b'w':False, b'a':False, b's':False, b'd':False, b' ':False, b'q':False}
        self.window = None
        self._last_cursor_pos = None
        self.highlight_block = None

        self.main()

    def load_texture(self, path):
        """加载纹理图片到OpenGL"""
        img = Image.open(path).convert('RGB')
        w, h = img.size
        tex = glGenTextures(1)
        glBindTexture(GL_TEXTURE_2D, tex)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, w, h, 0, GL_RGB, GL_UNSIGNED_BYTE, img.tobytes())
        return tex

    # ========== 区块操作 ==========
    def get_chunk(self, cx, cz):
        """获取或创建区块"""
        key = (cx, cz)
        if key not in self.chunks:
            self.chunks[key] = Chunk(cx, cz)
        return self.chunks[key]

    def world_to_local(self, wx, wy, wz):
        """将世界坐标转换为区块坐标和局部坐标"""
        cx = wx // (self.chunk_size * 2)
        cz = wz // (self.chunk_size * 2)
        lx = (wx // 2) - cx * self.chunk_size
        ly = wy // 2
        lz = (wz // 2) - cz * self.chunk_size
        return cx, cz, lx, ly, lz

    def is_block_at(self, wx, wy, wz):
        """检查世界坐标是否有方块"""
        cx, cz, lx, ly, lz = self.world_to_local(wx, wy, wz)
        chunk = self.chunks.get((cx, cz))
        return chunk.is_block_at(lx, ly, lz) if chunk else False

    def set_block(self, wx, wy, wz, tex_ids):
        cx, cz, lx, ly, lz = self.world_to_local(wx, wy, wz)
        chunk = self.get_chunk(cx, cz)
        chunk.set_block(lx, ly, lz, tex_ids)
        chunk.dirty = True
        self._mark_neighbor_chunks_dirty(cx, cz)

    def remove_block(self, wx, wy, wz):
        cx, cz, lx, ly, lz = self.world_to_local(wx, wy, wz)
        chunk = self.chunks.get((cx, cz))
        if chunk and chunk.remove_block(lx, ly, lz):
            chunk.dirty = True
            self._mark_neighbor_chunks_dirty(cx, cz)
            return True
        return False

    def _mark_neighbor_chunks_dirty(self, cx, cz):
        for dcx, dcz in [(1,0), (-1,0), (0,1), (0,-1)]:
            neighbor = self.chunks.get((cx + dcx, cz + dcz))
            if neighbor:
                neighbor.dirty = True

    # ========== 世界生成 ==========
    def generate_world(self, size_in_chunks=8, height_scale=35, seed=42):
        """按区块生成地形"""
        from opensimplex import OpenSimplex
        noise = OpenSimplex(seed)
        total = 0
        for cx in range(-size_in_chunks//2, size_in_chunks//2):
            for cz in range(-size_in_chunks//2, size_in_chunks//2):
                chunk = self.get_chunk(cx, cz)
                for lx in range(self.chunk_size):
                    for lz in range(self.chunk_size):
                        wx = (cx * self.chunk_size + lx) * 2
                        wz = (cz * self.chunk_size + lz) * 2
                        y = noise.noise2(wx * 0.05, wz * 0.05)
                        top_y = int((y + 1) / 2 * height_scale)
                        if top_y >= Chunk.WORLD_HEIGHT:
                            top_y = Chunk.WORLD_HEIGHT - 1
                        for ly in range(top_y, -1, -1):
                            wy = ly * 2
                            offset = top_y - ly
                            if offset == 0:
                                tex = [self.texture_map["grass"]] * 4 + [self.texture_map["grass_top"], self.texture_map["dirt"]]
                            elif offset <= 3:
                                tex = [self.texture_map["dirt"]] * 6
                            else:
                                tex = [self.texture_map["stone"]] * 6
                            chunk.set_block(lx, ly, lz, tex)
                            total += 1
                chunk.dirty = True
        print(f"[{get_log_time()}] Generated {total} blocks")

    # ========== 区块VBO重建 ==========
    def rebuild_chunk_vbo(self, chunk):
        """重建单个区块的VBO（只处理暴露面）"""
        # 清空旧VBO
        for _, (vbo, _) in chunk.vbo_groups.items():
            glDeleteBuffers(1, [vbo])
        chunk.vbo_groups = {}

        temp = {}
        for (lx, ly, lz), tex_ids in chunk.blocks.items():
            wx, wy, wz = chunk.get_world_pos(lx, ly, lz)
            # 检查6个面是否有邻居
            neighbors = [
                (wx, wy, wz+2), (wx, wy, wz-2),
                (wx-2, wy, wz), (wx+2, wy, wz),
                (wx, wy+2, wz), (wx, wy-2, wz)
            ]
            for i, (nx, ny, nz) in enumerate(neighbors):
                if self.is_block_at(nx, ny, nz):
                    continue
                tex = tex_ids[i]
                if tex is None:
                    continue
                v0, v1, v2, v3 = self.faces[i]
                pts = [
                    (self.vertices[v0][0]+wx, self.vertices[v0][1]+wy, self.vertices[v0][2]+wz),
                    (self.vertices[v1][0]+wx, self.vertices[v1][1]+wy, self.vertices[v1][2]+wz),
                    (self.vertices[v2][0]+wx, self.vertices[v2][1]+wy, self.vertices[v2][2]+wz),
                    (self.vertices[v3][0]+wx, self.vertices[v3][1]+wy, self.vertices[v3][2]+wz)
                ]
                uv = self.face_uv[i]
                data = []
                for j in range(4):
                    data.extend([pts[j][0], pts[j][1], pts[j][2], uv[j][0], uv[j][1]])
                temp.setdefault(tex, []).extend(data)

        # 创建VBO
        for tex, data in temp.items():
            arr = np.array(data, dtype=np.float32)
            vbo = glGenBuffers(1)
            glBindBuffer(GL_ARRAY_BUFFER, vbo)
            glBufferData(GL_ARRAY_BUFFER, arr.nbytes, arr, GL_STATIC_DRAW)
            glBindBuffer(GL_ARRAY_BUFFER, 0)
            chunk.vbo_groups[tex] = (vbo, len(data)//5)
        chunk.dirty = False

    # ========== 渲染 ==========
    def render_chunks(self):
        """渲染所有区块"""
        # 先重建脏区块
        for chunk in self.chunks.values():
            if chunk.dirty:
                self.rebuild_chunk_vbo(chunk)

        glEnable(GL_TEXTURE_2D)
        glEnableClientState(GL_VERTEX_ARRAY)
        glEnableClientState(GL_TEXTURE_COORD_ARRAY)
        stride = 5 * 4

        for chunk in self.chunks.values():
            if not chunk.blocks:
                continue
            for tex, (vbo, count) in chunk.vbo_groups.items():
                glBindTexture(GL_TEXTURE_2D, tex)
                glBindBuffer(GL_ARRAY_BUFFER, vbo)
                glVertexPointer(3, GL_FLOAT, stride, None)
                glTexCoordPointer(2, GL_FLOAT, stride, ctypes.c_void_p(12))
                glDrawArrays(GL_QUADS, 0, count)

        glDisableClientState(GL_TEXTURE_COORD_ARRAY)
        glDisableClientState(GL_VERTEX_ARRAY)
        glDisable(GL_TEXTURE_2D)

    # ========== 回调函数 ==========
    def reshape_callback(self, win, w, h):
        if w == 0 or h == 0:
            w, h = 1, 1
        self.win_width, self.win_height = w, h
        self.center_x, self.center_y = w//2, h//2
        glViewport(0, 0, w, h)
        glMatrixMode(GL_PROJECTION)
        glLoadIdentity()
        gluPerspective(45, w/h, 0.1, 200.0)
        glMatrixMode(GL_MODELVIEW)

    def mouse_motion_callback(self, win, x, y):
        if self._last_cursor_pos is None:
            self._last_cursor_pos = (x, y)
            return
        dx = x - self._last_cursor_pos[0]
        dy = y - self._last_cursor_pos[1]
        self._last_cursor_pos = (x, y)
        self.cam_yaw -= dx * self.mouse_sensitivity
        self.cam_pitch -= dy * self.mouse_sensitivity
        self.cam_pitch = max(-math.pi/2.2, min(math.pi/2.2, self.cam_pitch))

    def mouse_button_callback(self, win, button, action, mods):
        if button == glfw.MOUSE_BUTTON_LEFT and action == glfw.PRESS:
            pos, _ = self.get_target_block()
            if pos:
                self.remove_block(*pos)

    def key_callback(self, win, key, scancode, action, mods):
        km = {glfw.KEY_W:b'w', glfw.KEY_A:b'a', glfw.KEY_S:b's', glfw.KEY_D:b'd',
              glfw.KEY_SPACE:b' ', glfw.KEY_LEFT_SHIFT:b'lshift'}
        if key in km:
            k = km[key]
            self.keys[k] = (action == glfw.PRESS)

    # ========== 物理与碰撞 ==========
    def get_player_aabb(self, pos):
        hw = self.player_width / 2
        return ((pos[0]-hw, pos[1], pos[2]-hw), (pos[0]+hw, pos[1]+self.player_height, pos[2]+hw))

    def aabb_overlap(self, a_min, a_max, b_min, b_max):
        return (a_min[0] < b_max[0] and a_max[0] > b_min[0] and
                a_min[1] < b_max[1] and a_max[1] > b_min[1] and
                a_min[2] < b_max[2] and a_max[2] > b_min[2])

    def is_colliding_with_block(self, pos):
        a_min, a_max = self.get_player_aabb(pos)
        start = (int(math.floor(a_min[0]/2))*2, int(math.floor(a_min[1]/2))*2, int(math.floor(a_min[2]/2))*2)
        end = (int(math.ceil(a_max[0]/2))*2, int(math.ceil(a_max[1]/2))*2, int(math.ceil(a_max[2]/2))*2)
        for wx in range(start[0], end[0]+1, 2):
            for wy in range(start[1], end[1]+1, 2):
                for wz in range(start[2], end[2]+1, 2):
                    if self.is_block_at(wx, wy, wz):
                        b_min = (wx-1, wy-1, wz-1)
                        b_max = (wx+1, wy+1, wz+1)
                        if self.aabb_overlap(a_min, a_max, b_min, b_max):
                            return True
        return False

    def resolve_collision_mc(self, target):
        ox, oy, oz = self.cam_pos
        nx, ny, nz = target

        # X轴
        test = (nx, oy, oz)
        if not self.is_colliding_with_block(test):
            self.cam_pos[0] = nx
        else:
            self.velocity[0] = 0

        # Y轴
        test = (self.cam_pos[0], ny, oz)
        if not self.is_colliding_with_block(test):
            self.cam_pos[1] = ny
            if self.velocity[1] < 0:
                self.is_on_ground = False
        else:
            if self.velocity[1] < 0:
                self.is_on_ground = True
            self.velocity[1] = 0

        # Z轴
        test = (self.cam_pos[0], self.cam_pos[1], nz)
        if not self.is_colliding_with_block(test):
            self.cam_pos[2] = nz
        else:
            self.velocity[2] = 0

    def physics_update(self, dt):
        self.is_on_ground = False
        self.velocity[1] += self.gravity * dt
        if self.velocity[1] < -30:
            self.velocity[1] = -30

        fwd = [-math.sin(self.cam_yaw), 0, -math.cos(self.cam_yaw)]
        rgt = [math.cos(self.cam_yaw), 0, -math.sin(self.cam_yaw)]

        mv = [0.0, 0.0, 0.0]
        if self.keys[b'w']:
            mv[0] += fwd[0]; mv[2] += fwd[2]
        if self.keys[b's']:
            mv[0] -= fwd[0]; mv[2] -= fwd[2]
        if self.keys[b'a']:
            mv[0] -= rgt[0]; mv[2] -= rgt[2]
        if self.keys[b'd']:
            mv[0] += rgt[0]; mv[2] += rgt[2]

        mag = math.sqrt(mv[0]**2 + mv[2]**2)
        if mag > 0:
            mv[0] /= mag; mv[2] /= mag
            self.velocity[0] = mv[0] * self.player_speed
            self.velocity[2] = mv[2] * self.player_speed
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
        self.resolve_collision_mc(new_pos)

        if self.keys[b' '] and self.is_on_ground:
            self.velocity[1] = self.jump_speed
            self.is_on_ground = False

        if self.cam_pos[1] < -10:
            self.cam_pos = [0.0, 60.0, 0.0]
            self.velocity = [0.0, 0.0, 0.0]
            self.ensure_player_not_stuck()

    def ensure_player_not_stuck(self):
        for _ in range(200):
            if not self.is_colliding_with_block(self.cam_pos):
                break
            self.cam_pos[1] += 0.5
        else:
            self.cam_pos = [0.0, 100.0, 0.0]

    # ========== 射线检测 ==========
    def get_face_direction(self, prev, curr):
        step = (curr[0]-prev[0], curr[1]-prev[1], curr[2]-prev[2])
        axis = max(range(3), key=lambda i: abs(step[i]))
        sign = 1 if step[axis] > 0 else -1
        faces = {(0,1):"right", (0,-1):"left", (1,1):"top", (1,-1):"bottom", (2,1):"front", (2,-1):"back"}
        return faces[(axis, sign)]

    def get_target_block(self, max_dist=12.0):
        dx = -math.sin(self.cam_yaw) * math.cos(self.cam_pitch)
        dy = math.sin(self.cam_pitch)
        dz = -math.cos(self.cam_yaw) * math.cos(self.cam_pitch)
        eye = [self.cam_pos[0], self.cam_pos[1] + self.player_height * 0.9, self.cam_pos[2]]
        step = 0.05
        prev = eye.copy()
        for i in range(int(max_dist / step)):
            cur = [eye[0] + dx*i*step, eye[1] + dy*i*step, eye[2] + dz*i*step]
            wx = round(cur[0]/2)*2
            wy = round(cur[1]/2)*2
            wz = round(cur[2]/2)*2
            if self.is_block_at(wx, wy, wz):
                self.highlight_block = (wx, wy, wz)
                return (wx, wy, wz), self.get_face_direction(prev, cur)
            prev = cur
        self.highlight_block = None
        return None, None

    # ========== 辅助绘制 ==========
    def draw_highlight_box(self):
        if not self.highlight_block:
            return
        bx, by, bz = self.highlight_block
        glDisable(GL_TEXTURE_2D)
        glEnable(GL_LINE_SMOOTH)
        glHint(GL_LINE_SMOOTH_HINT, GL_NICEST)
        glPolygonMode(GL_FRONT_AND_BACK, GL_LINE)
        glLineWidth(6.0)
        glColor4f(1.0, 1.0, 1.0, 0.9)
        glBegin(GL_QUADS)
        for face in self.faces:
            for idx in face:
                v = self.vertices[idx]
                glVertex3f(v[0]+bx, v[1]+by, v[2]+bz)
        glEnd()
        glPolygonMode(GL_FRONT_AND_BACK, GL_FILL)
        glDisable(GL_LINE_SMOOTH)
        glEnable(GL_TEXTURE_2D)

    def draw_crosshair(self):
        w, h = self.win_width, self.win_height
        if w == 0 or h == 0:
            return
        glMatrixMode(GL_PROJECTION)
        glPushMatrix()
        glLoadIdentity()
        glOrtho(0, w, 0, h, -1, 1)
        glMatrixMode(GL_MODELVIEW)
        glPushMatrix()
        glLoadIdentity()
        glDisable(GL_DEPTH_TEST)
        glDisable(GL_TEXTURE_2D)
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glColor4f(1.0, 1.0, 1.0, 0.9)
        cx, cy = w//2, h//2
        l, t = 15, 3
        glBegin(GL_QUADS)
        glVertex2f(cx-l, cy-t/2); glVertex2f(cx+l, cy-t/2); glVertex2f(cx+l, cy+t/2); glVertex2f(cx-l, cy+t/2)
        glVertex2f(cx-t/2, cy-l); glVertex2f(cx+t/2, cy-l); glVertex2f(cx+t/2, cy+l); glVertex2f(cx-t/2, cy+l)
        glEnd()
        glDisable(GL_BLEND)
        glEnable(GL_DEPTH_TEST)
        glMatrixMode(GL_PROJECTION)
        glPopMatrix()
        glMatrixMode(GL_MODELVIEW)
        glPopMatrix()

    # ========== 主渲染循环 ==========
    def draw_frame(self, dt):
        self.physics_update(dt)
        self.get_target_block()

        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)
        glEnable(GL_DEPTH_TEST)
        glLoadIdentity()

        eye = [self.cam_pos[0], self.cam_pos[1] + self.player_height * 0.9, self.cam_pos[2]]
        look = [-math.sin(self.cam_yaw)*math.cos(self.cam_pitch),
                math.sin(self.cam_pitch),
                -math.cos(self.cam_yaw)*math.cos(self.cam_pitch)]
        mag = math.sqrt(look[0]**2 + look[1]**2 + look[2]**2)
        if mag > 0:
            look[0]/=mag; look[1]/=mag; look[2]/=mag
        center = [eye[0]+look[0], eye[1]+look[1], eye[2]+look[2]]
        up = [0,1,0] if abs(self.cam_pitch) < math.pi/2.1 else [0,-1,0]
        gluLookAt(eye[0], eye[1], eye[2], center[0], center[1], center[2], up[0], up[1], up[2])

        self.render_chunks()
        self.draw_highlight_box()
        self.draw_crosshair()
        glfw.swap_buffers(self.window)

    def cleanup(self):
        for chunk in self.chunks.values():
            for _, (vbo, _) in chunk.vbo_groups.items():
                glDeleteBuffers(1, [vbo])
        if self.window:
            glfw.destroy_window(self.window)

    # ========== 主入口 ==========
    def main(self):
        if not glfw.init():
            raise RuntimeError("glfw init failed")
        self.window = glfw.create_window(self.win_width, self.win_height, "Flantyle - Chunk System", None, None)
        if not self.window:
            glfw.terminate()
            raise RuntimeError("window creation failed")
        glfw.make_context_current(self.window)
        glfw.swap_interval(1)

        glfw.set_cursor_pos_callback(self.window, self.mouse_motion_callback)
        glfw.set_key_callback(self.window, self.key_callback)
        glfw.set_mouse_button_callback(self.window, self.mouse_button_callback)
        glfw.set_framebuffer_size_callback(self.window, self.reshape_callback)

        self.reshape_callback(self.window, self.win_width, self.win_height)

        glfw.set_input_mode(self.window, glfw.CURSOR, glfw.CURSOR_DISABLED)
        glfw.set_input_mode(self.window, glfw.RAW_MOUSE_MOTION, glfw.TRUE)

        glClearColor(86/255.0, 151/255.0, 223/255.0, 1.0)
        glEnable(GL_DEPTH_TEST)

        try:
            for tex_name, filename in TEXTURE_NAME_MAP.items():
                if filename in texture_registry:
                    tex_id = self.load_texture(texture_registry[filename])
                    self.texture_map[tex_name] = tex_id
                else:
                    print(f"[{get_log_time()}] [Warning]: Texture {filename} not found")
            print(f"[{get_log_time()}] [info]: Textures loaded")
        except Exception as e:
            print(f"[{get_log_time()}] [Error]: {e}")
            self.texture_map = {}

        self.generate_world(size_in_chunks=8, height_scale=35, seed=42)

        # 强制重建所有区块
        for chunk in self.chunks.values():
            if chunk.dirty:
                self.rebuild_chunk_vbo(chunk)

        self.ensure_player_not_stuck()
        print(f"[{get_log_time()}] [world/info]: World generation completed")

        prev_time = glfw.get_time()
        while not glfw.window_should_close(self.window):
            curr_time = glfw.get_time()
            dt = min(curr_time - prev_time, 0.05)
            prev_time = curr_time
            self.draw_frame(dt)
            glfw.poll_events()

        self.cleanup()
        glfw.terminate()

if __name__ == "__main__":
    FlantyleMain = Flantyle()