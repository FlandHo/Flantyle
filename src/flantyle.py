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
            (3,2,6,7),  # 顶 (Y=1)
            (0,1,5,4)   # 底 (Y=-1)
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

        self.cam_pos = [0.0, 2.0, 6.0]
        self.cam_yaw = 0.0
        self.cam_pitch = 0.0
        self.player_speed = 8.0
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

    def load_texture(self, path):
        img = Image.open(path).convert('RGB')
        width, height = img.size
        tex_id = glGenTextures(1)
        glBindTexture(GL_TEXTURE_2D, tex_id)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, width, height, 0, GL_RGB, GL_UNSIGNED_BYTE, img.tobytes())
        return tex_id

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
                self.vbo_needs_rebuild = True
                print(f"[{get_log_time()}] [info]: Batch mode OFF, {len(self.blocks)} blocks total")
                self.rebuild_vbo()
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

    def rebuild_vbo(self):
        for tex_id, (vbo_id, _) in self.vbo_groups.items():
            glDeleteBuffers(1, [vbo_id])
        self.vbo_groups = {}

        if not self.blocks:
            print(f"[{get_log_time()}] [info]: No blocks to build VBO")
            return

        block_positions = {(bx, by, bz) for (bx, by, bz, _) in self.blocks}
        temp_groups = {}

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
                    tex = self.texture_map.get("error")
                if tex is None:
                    continue

                v0, v1, v2, v3 = face
                uv = self.face_uv[i]
                pts = [
                    (self.vertices[v0][0] + bx, self.vertices[v0][1] + by, self.vertices[v0][2] + bz),
                    (self.vertices[v1][0] + bx, self.vertices[v1][1] + by, self.vertices[v1][2] + bz),
                    (self.vertices[v2][0] + bx, self.vertices[v2][1] + by, self.vertices[v2][2] + bz),
                    (self.vertices[v3][0] + bx, self.vertices[v3][1] + by, self.vertices[v3][2] + bz),
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

    def update_camera(self, dt):
        forward = [-math.sin(self.cam_yaw), 0, -math.cos(self.cam_yaw)]
        right = [math.cos(self.cam_yaw), 0, -math.sin(self.cam_yaw)]

        move_vec = [0.0, 0.0, 0.0]
        if self.keys.get(b'w', False):
            move_vec[0] += forward[0]; move_vec[2] += forward[2]
        if self.keys.get(b's', False):
            move_vec[0] -= forward[0]; move_vec[2] -= forward[2]
        if self.keys.get(b'a', False):
            move_vec[0] -= right[0]; move_vec[2] -= right[2]
        if self.keys.get(b'd', False):
            move_vec[0] += right[0]; move_vec[2] += right[2]

        mag = math.sqrt(move_vec[0]**2 + move_vec[2]**2)
        if mag > 0:
            move_vec[0] /= mag
            move_vec[2] /= mag
            self.cam_pos[0] += move_vec[0] * self.player_speed * dt
            self.cam_pos[2] += move_vec[2] * self.player_speed * dt

        if self.keys.get(b' ', False):
            self.cam_pos[1] += self.player_speed * dt
        if self.keys.get(b'lshift', False):
            self.cam_pos[1] -= self.player_speed * dt

    def draw_frame(self, dt):
        self.update_camera(dt)

        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)
        glEnable(GL_DEPTH_TEST)
        glLoadIdentity()

        look_dir = [
            -math.sin(self.cam_yaw) * math.cos(self.cam_pitch),
            math.sin(self.cam_pitch),
            -math.cos(self.cam_yaw) * math.cos(self.cam_pitch)
        ]
        mag = math.sqrt(look_dir[0]**2 + look_dir[1]**2 + look_dir[2]**2)
        if mag > 0:
            look_dir[0] /= mag; look_dir[1] /= mag; look_dir[2] /= mag

        center = [
            self.cam_pos[0] + look_dir[0],
            self.cam_pos[1] + look_dir[1],
            self.cam_pos[2] + look_dir[2]
        ]
        up = [0, 1, 0] if abs(self.cam_pitch) < math.pi/2.1 else [0, -1, 0]

        gluLookAt(self.cam_pos[0], self.cam_pos[1], self.cam_pos[2],
                  center[0], center[1], center[2],
                  up[0], up[1], up[2])

        if self.vbo_needs_rebuild:
            self.rebuild_vbo()

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
        self.block_upload(False)
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