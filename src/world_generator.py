from chunk import Chunk
from log_time import get_log_time

def generate_world(flantyle_instance, size_in_chunks=8, height_scale=35, seed=42):
    from opensimplex import OpenSimplex
    noise = OpenSimplex(seed)
    total = 0

    for cx in range(-size_in_chunks//2, size_in_chunks//2):
        for cz in range(-size_in_chunks//2, size_in_chunks//2):
            chunk = flantyle_instance.get_chunk(cx, cz)
            for lx in range(flantyle_instance.chunk_size):
                for lz in range(flantyle_instance.chunk_size):
                    wx = (cx * flantyle_instance.chunk_size + lx) * 2
                    wz = (cz * flantyle_instance.chunk_size + lz) * 2
                    y = noise.noise2(wx * 0.05, wz * 0.05)
                    top_y = int((y + 1) / 2 * height_scale)
                    if top_y >= Chunk.WORLD_HEIGHT:
                        top_y = Chunk.WORLD_HEIGHT - 1
                    for ly in range(top_y, -1, -1):
                        wy = ly * 2
                        offset = top_y - ly
                        if offset == 0:
                            tex = [flantyle_instance.texture_map["grass"]] * 4 + [flantyle_instance.texture_map["grass_top"], flantyle_instance.texture_map["dirt"]]
                        elif offset <= 3:
                            tex = [flantyle_instance.texture_map["dirt"]] * 6
                        else:
                            tex = [flantyle_instance.texture_map["stone"]] * 6
                        chunk.set_block(lx, ly, lz, tex)
                        total += 1
                chunk.dirty = True
    print(f"[{get_log_time()}] Generated {total} blocks")