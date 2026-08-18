class Chunk:
    CHUNK_SIZE = 16
    WORLD_HEIGHT = 256

    def __init__(self, cx, cz):
        self.cx = cx
        self.cz = cz
        self.blocks = {}
        self.vbo_groups = {}
        self.dirty = True
        self.dirty_blocks = set()          # 记录重建方块局部坐标

    def set_block(self, lx, ly, lz, tex_ids):
        if 0 <= lx < self.CHUNK_SIZE and 0 <= lz < self.CHUNK_SIZE and 0 <= ly < self.WORLD_HEIGHT:
            self.blocks[(lx, ly, lz)] = tex_ids
            self.dirty = True
            self.dirty_blocks.add((lx, ly, lz))

    def remove_block(self, lx, ly, lz):
        if (lx, ly, lz) in self.blocks:
            del self.blocks[(lx, ly, lz)]
            self.dirty = True
            self.dirty_blocks.add((lx, ly, lz))
            return True
        return False

    def get_block(self, lx, ly, lz):
        return self.blocks.get((lx, ly, lz))

    def is_block_at(self, lx, ly, lz):
        return (lx, ly, lz) in self.blocks

    def get_world_pos(self, lx, ly, lz):
        wx = (self.cx * self.CHUNK_SIZE + lx) * 2
        wy = ly * 2
        wz = (self.cz * self.CHUNK_SIZE + lz) * 2
        return wx, wy, wz

    def add_dirty_block(self, lx, ly, lz):
        """添加一个需要重建的方块（局部坐标）"""
        if 0 <= lx < self.CHUNK_SIZE and 0 <= ly < self.WORLD_HEIGHT and 0 <= lz < self.CHUNK_SIZE:
            self.dirty_blocks.add((lx, ly, lz))
            self.dirty = True