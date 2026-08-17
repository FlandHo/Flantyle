class Chunk:
    """
    区块类，负责存储16x16x256的方块数据。
    CHUNK_SIZE: X/Z方向尺寸（16）
    WORLD_HEIGHT: Y方向最大高度（256）
    """
    CHUNK_SIZE = 16
    WORLD_HEIGHT = 256

    def __init__(self, cx, cz):
        self.cx = cx                      # 区块X索引
        self.cz = cz                      # 区块Z索引
        self.blocks = {}                  # (lx, ly, lz) -> tex_ids
        self.vbo_groups = {}              # tex_id -> (vbo_id, vertex_count)
        self.dirty = True                 # 标记是否需要重建VBO

    def set_block(self, lx, ly, lz, tex_ids):
        """在局部坐标放置方块"""
        if 0 <= lx < self.CHUNK_SIZE and 0 <= lz < self.CHUNK_SIZE and 0 <= ly < self.WORLD_HEIGHT:
            self.blocks[(lx, ly, lz)] = tex_ids
            self.dirty = True

    def remove_block(self, lx, ly, lz):
        """移除局部坐标的方块"""
        if (lx, ly, lz) in self.blocks:
            del self.blocks[(lx, ly, lz)]
            self.dirty = True
            return True
        return False

    def get_block(self, lx, ly, lz):
        """获取局部坐标的方块纹理列表"""
        return self.blocks.get((lx, ly, lz))

    def is_block_at(self, lx, ly, lz):
        """检查局部坐标是否有方块"""
        return (lx, ly, lz) in self.blocks

    def get_world_pos(self, lx, ly, lz):
        """将局部坐标转换为世界坐标（已乘以2）"""
        wx = (self.cx * self.CHUNK_SIZE + lx) * 2
        wy = ly * 2
        wz = (self.cz * self.CHUNK_SIZE + lz) * 2
        return wx, wy, wz