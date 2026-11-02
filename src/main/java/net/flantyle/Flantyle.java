package net.flantyle;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.stb.STBEasyFont;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.glfw.GLFWFramebufferSizeCallback;
import org.lwjgl.glfw.GLFWCursorPosCallback;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.InputStream;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.*;

public class Flantyle {
    public static final int WORLD_MAX_HEIGHT = 64;
    public static final int WORLD_MIN_HEIGHT = 0;

    public static final float SPAWN_X = 0.0f;
    public static final float SPAWN_Y = 100.0f;
    public static final float SPAWN_Z = 0.0f;

    public static final float[] VERTICES = {
            -1, -1, -1,
            1, -1, -1,
            1, 1, -1,
            -1, 1, -1,
            -1, -1, 1,
            1, -1, 1,
            1, 1, 1,
            -1, 1, 1
    };

    public static final int[][] FACES = {
            {4, 5, 6, 7},
            {0, 1, 2, 3},
            {0, 4, 7, 3},
            {1, 5, 6, 2},
            {3, 2, 6, 7},
            {0, 1, 5, 4}
    };

    public static final float[][] FACE_UV = {
            {0,0, 1,0, 1,1, 0,1},
            {0,0, 1,0, 1,1, 0,1},
            {0,0, 1,0, 1,1, 0,1},
            {0,0, 1,0, 1,1, 0,1},
            {0,0, 1,0, 1,1, 0,1},
            {0,0, 1,0, 1,1, 0,1}
    };

    private static int lightLoc;

    private static final String FALLBACK_TEX = "__fallback__";
    private static final float SENSITIVITY = 0.1f;
    private static final float REACH = 12.0f;
    private static final float PLAYER_WIDTH = 1.2f;
    private static final float PLAYER_HEIGHT = 3.6f;
    private static final float EYE_HEIGHT = PLAYER_HEIGHT * 0.9f;
    private static final float GRAVITY = -60.0f;
    private static final float JUMP_SPEED = 18.0f;
    private static final float PLAYER_SPEED = 10.0f;
    private static final float MAX_FALL_SPEED = -80.0f;
    private static final float SLIDE_DECAY_PER_SECOND = 0.001f;
    private static final float SLIDE_STOP_THRESHOLD = 0.5f;
    private static final float WIRE_THICKNESS = 0.015f;

    private static long window_root;

    private static float camX = SPAWN_X;
    private static float camY = SPAWN_Y;
    private static float camZ = SPAWN_Z;
    private static float yaw = -90.0f;
    private static float pitch = 0.0f;
    private static float velX = 0, velY = 0, velZ = 0;
    private static boolean onGround = false;

    private static int chunkRenderSize = 6;

    private static boolean firstMouse = true;
    private static double lastMouseX = 0;
    private static double lastMouseY = 0;
    private static double lastFrameTime = 0;

    private static int shaderProgram;
    private static int modelLoc;
    private static int viewLoc;
    private static int projLoc;
    private static int texLoc;
    private static int modeLoc;
    private static int colorLoc;

    private static int textVao;
    private static int textVbo;
    private static ByteBuffer textVertexBuffer;

    private static Matrix4f projection;
    private static Matrix4f view;

    private static int fallbackTextureId = 0;

    private static final Map<String, Integer> textureMap = new HashMap<>();

    private static GLFWFramebufferSizeCallback framebufferSizeCallback;
    private static GLFWCursorPosCallback cursorPosCallback;

    private static int wireframeVao;
    private static int crosshairVao;

    private static int[] highlightBlock = null;
    private static int[] highlightFace = null;

    private static int winWidth = 800;
    private static int winHeight = 600;

    private static void setupMatrices() {
        projection = new Matrix4f().perspective(
                (float) Math.toRadians(70.0),
                (float) winWidth / winHeight,
                0.1f, 1000f);
    }

    private static void updateView() {
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);

        float dirX = (float) (Math.cos(yawRad) * Math.cos(pitchRad));
        float dirY = (float) Math.sin(pitchRad);
        float dirZ = (float) (Math.sin(yawRad) * Math.cos(pitchRad));

        float eyeX = camX;
        float eyeY = camY + EYE_HEIGHT;
        float eyeZ = camZ;

        view = new Matrix4f().lookAt(
                new Vector3f(eyeX, eyeY, eyeZ),
                new Vector3f(eyeX + dirX, eyeY + dirY, eyeZ + dirZ),
                new Vector3f(0, 1, 0));
    }

    private static String loadResource(String path) {
        try (var is = Flantyle.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new RuntimeException("Resource not found: " + path);
            }
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Read resource failed: " + path, e);
        }
    }

    private static void setupShaders() {
        String vertexSrc = loadResource("/vertex.glsl");
        String fragmentSrc = loadResource("/fragment.glsl");

        int vs = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vs, vertexSrc);
        glCompileShader(vs);
        if (glGetShaderi(vs, GL_COMPILE_STATUS) == 0) {
            throw new RuntimeException("Vertex shader compilation failed:\n" + glGetShaderInfoLog(vs));
        }

        int fs = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fs, fragmentSrc);
        glCompileShader(fs);
        if (glGetShaderi(fs, GL_COMPILE_STATUS) == 0) {
            throw new RuntimeException("Fragment shader compilation failed:\n" + glGetShaderInfoLog(fs));
        }

        shaderProgram = glCreateProgram();
        glAttachShader(shaderProgram, vs);
        glAttachShader(shaderProgram, fs);
        glLinkProgram(shaderProgram);
        if (glGetProgrami(shaderProgram, GL_LINK_STATUS) == 0) {
            throw new RuntimeException("Shader linking failed:\n" + glGetProgramInfoLog(shaderProgram));
        }

        glDeleteShader(vs);
        glDeleteShader(fs);

        modelLoc = glGetUniformLocation(shaderProgram, "model");
        viewLoc  = glGetUniformLocation(shaderProgram, "view");
        projLoc  = glGetUniformLocation(shaderProgram, "projection");
        texLoc   = glGetUniformLocation(shaderProgram, "textureSampler");
        modeLoc  = glGetUniformLocation(shaderProgram, "drawMode");
        colorLoc = glGetUniformLocation(shaderProgram, "solidColor");
        lightLoc  = glGetUniformLocation(shaderProgram, "lightDir");
    }

    private static void initializeGLFW() {
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
    }

    private static void changeSize(int w, int h) {
        if (h == 0) h = 1;
        winWidth = w;
        winHeight = h;
        glViewport(0, 0, w, h);

        projection = new Matrix4f().perspective(
                (float) Math.toRadians(70.0),
                (float) w / h,
                0.1f, 1000f);

        rebuildCrosshairVao();
    }

    private static int createFallbackTexture() {
        final int SIZE = 16;
        final int TILE = 8;

        ByteBuffer buffer = BufferUtils.createByteBuffer(SIZE * SIZE * 4);

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                boolean magenta = ((x / TILE) + (y / TILE)) % 2 == 0;
                if (magenta) {
                    buffer.put((byte) 255);
                    buffer.put((byte) 0);
                    buffer.put((byte) 255);
                    buffer.put((byte) 255);
                } else {
                    buffer.put((byte) 0);
                    buffer.put((byte) 0);
                    buffer.put((byte) 0);
                    buffer.put((byte) 255);
                }
            }
        }
        buffer.flip();

        int textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);

        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, SIZE, SIZE, 0,
                GL_RGBA, GL_UNSIGNED_BYTE, buffer);

        return textureId;
    }

    private static int loadTexture(String classpathPath) {
        byte[] bytes;
        try (InputStream is = Flantyle.class.getResourceAsStream(classpathPath)) {
            if (is == null) {
                System.out.println("[WARN-RESOURCE] Resource not found: " + classpathPath);
                return fallbackTextureId;
            }
            bytes = is.readAllBytes();
        } catch (Exception e) {
            System.out.println("[WARN-RESOURCE] Read failed: " + classpathPath + " -> " + e);
            return fallbackTextureId;
        }

        ByteBuffer imageBuffer = BufferUtils.createByteBuffer(bytes.length);
        imageBuffer.put(bytes);
        imageBuffer.flip();

        IntBuffer w = BufferUtils.createIntBuffer(1);
        IntBuffer h = BufferUtils.createIntBuffer(1);
        IntBuffer ch = BufferUtils.createIntBuffer(1);

        STBImage.stbi_set_flip_vertically_on_load(true);

        ByteBuffer image = STBImage.stbi_load_from_memory(imageBuffer, w, h, ch, 4);
        if (image == null) {
            System.out.println("[WARN-RESOURCE] STB load failed: " + classpathPath
                    + " -> " + STBImage.stbi_failure_reason());
            return fallbackTextureId;
        }

        int width = w.get(0);
        int height = h.get(0);

        int textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);

        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0,
                GL_RGBA, GL_UNSIGNED_BYTE, image);

        STBImage.stbi_image_free(image);

        return textureId;
    }

    private static void loadAllTextures() {
        for (String name : ResourceRegistry.getAllNames()) {
            String path = ResourceRegistry.getPath(name);
            int texId = loadTexture(path);
            textureMap.put(name, texId);
            System.out.println("[INFO-RESOURCE] " + name + " -> " + path + " -> id " + texId);
        }
    }

    private static int makeOverlayVao(float[] verts) {
        int vao = glGenVertexArrays();
        glBindVertexArray(vao);

        int vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        FloatBuffer fb = BufferUtils.createFloatBuffer(verts.length);
        fb.put(verts).flip();
        glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);

        glVertexAttribPointer(0, 3, GL_FLOAT, false, 5 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 5 * Float.BYTES, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);

        glBindVertexArray(0);
        return vao;
    }

    private static void addQuadTo(List<Float> out,
                                  float x0, float y0, float z0,
                                  float x1, float y1, float z1,
                                  float x2, float y2, float z2,
                                  float x3, float y3, float z3) {
        out.add(x0); out.add(y0); out.add(z0); out.add(0f); out.add(0f);
        out.add(x1); out.add(y1); out.add(z1); out.add(0f); out.add(0f);
        out.add(x2); out.add(y2); out.add(z2); out.add(0f); out.add(0f);

        out.add(x0); out.add(y0); out.add(z0); out.add(0f); out.add(0f);
        out.add(x2); out.add(y2); out.add(z2); out.add(0f); out.add(0f);
        out.add(x3); out.add(y3); out.add(z3); out.add(0f); out.add(0f);
    }

    private static int buildWireframeVao() {
        float t = WIRE_THICKNESS;

        float[][] V = {
                {-1,-1,-1}, { 1,-1,-1}, { 1, 1,-1}, {-1, 1,-1},
                {-1,-1, 1}, { 1,-1, 1}, { 1, 1, 1}, {-1, 1, 1}
        };
        int[][] E = {
                {0,1}, {1,2}, {2,3}, {3,0},
                {4,5}, {5,6}, {6,7}, {7,4},
                {0,4}, {1,5}, {2,6}, {3,7}
        };

        List<Float> data = new ArrayList<>();

        for (int[] e : E) {
            float[] a = V[e[0]];
            float[] b = V[e[1]];

            float dx = b[0] - a[0];
            float dy = b[1] - a[1];
            float dz = b[2] - a[2];

            float p1x = 0, p1y = 0, p1z = 0;
            float p2x = 0, p2y = 0, p2z = 0;

            if (dx != 0) {
                p1y = t;
                p2z = t;
            } else if (dy != 0) {
                p1x = t;
                p2z = t;
            } else {
                p1x = t;
                p2y = t;
            }

            addQuadTo(data,
                    a[0]-p1x, a[1]-p1y, a[2]-p1z,
                    b[0]-p1x, b[1]-p1y, b[2]-p1z,
                    b[0]+p1x, b[1]+p1y, b[2]+p1z,
                    a[0]+p1x, a[1]+p1y, a[2]+p1z);

            addQuadTo(data,
                    a[0]-p2x, a[1]-p2y, a[2]-p2z,
                    b[0]-p2x, b[1]-p2y, b[2]-p2z,
                    b[0]+p2x, b[1]+p2y, b[2]+p2z,
                    a[0]+p2x, a[1]+p2y, a[2]+p2z);
        }

        float[] verts = new float[data.size()];
        for (int i = 0; i < verts.length; i++) verts[i] = data.get(i);

        return makeOverlayVao(verts);
    }

    private static void initOverlayMeshes() {
        wireframeVao = buildWireframeVao();
        rebuildCrosshairVao();
        initTextRenderer();
    }

    private static void initTextRenderer() {
        textVao = glGenVertexArrays();
        glBindVertexArray(textVao);

        textVbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, textVbo);

        glVertexAttribPointer(0, 3, GL_FLOAT, false, 5 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 5 * Float.BYTES, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);

        glBindVertexArray(0);

        textVertexBuffer = BufferUtils.createByteBuffer(200000);
    }

    private static void rebuildCrosshairVao() {
        if (crosshairVao != 0) {
            glDeleteVertexArrays(crosshairVao);
        }

        float px = 2f / winWidth;
        float py = 2f / winHeight;
        float lx = 20f * px;
        float ly = 20f * py;
        float tx = 2f * px;
        float ty = 2f * py;

        float[] crossVerts = {
                -lx,-ty,0, 0,0,   lx,-ty,0, 0,0,   lx, ty,0, 0,0,
                -lx,-ty,0, 0,0,   lx, ty,0, 0,0,  -lx, ty,0, 0,0,

                -tx,-ly,0, 0,0,   tx,-ly,0, 0,0,   tx, ly,0, 0,0,
                -tx,-ly,0, 0,0,   tx, ly,0, 0,0,  -tx, ly,0, 0,0
        };
        crosshairVao = makeOverlayVao(crossVerts);
    }

    private static boolean isBlockAt(int wx, int wy, int wz) {
        return ChunkManager.getBlock(wx, wy, wz) != null;
    }

    private static boolean aabbOverlap(float[] aMin, float[] aMax, float[] bMin, float[] bMax) {
        return aMin[0] < bMax[0] && aMax[0] > bMin[0]
                && aMin[1] < bMax[1] && aMax[1] > bMin[1]
                && aMin[2] < bMax[2] && aMax[2] > bMin[2];
    }

    private static boolean isCollidingWithBlock(float px, float py, float pz) {
        float hw = PLAYER_WIDTH / 2;

        float aMinX = px - hw, aMinY = py, aMinZ = pz - hw;
        float aMaxX = px + hw, aMaxY = py + PLAYER_HEIGHT, aMaxZ = pz + hw;

        int startX = ((int) Math.floor(aMinX / 2)) * 2;
        int startY = ((int) Math.floor(aMinY / 2)) * 2;
        int startZ = ((int) Math.floor(aMinZ / 2)) * 2;
        int endX = ((int) Math.ceil(aMaxX / 2)) * 2;
        int endY = ((int) Math.ceil(aMaxY / 2)) * 2;
        int endZ = ((int) Math.ceil(aMaxZ / 2)) * 2;

        float[] aMin = {aMinX, aMinY, aMinZ};
        float[] aMax = {aMaxX, aMaxY, aMaxZ};
        float[] bMin = new float[3];
        float[] bMax = new float[3];

        for (int wx = startX; wx <= endX; wx += 2) {
            for (int wy = startY; wy <= endY; wy += 2) {
                for (int wz = startZ; wz <= endZ; wz += 2) {
                    if (isBlockAt(wx, wy, wz)) {
                        bMin[0] = wx - 1; bMin[1] = wy - 1; bMin[2] = wz - 1;
                        bMax[0] = wx + 1; bMax[1] = wy + 1; bMax[2] = wz + 1;
                        if (aabbOverlap(aMin, aMax, bMin, bMax)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static void resolveCollision(float nx, float ny, float nz) {
        float ox = camX, oy = camY, oz = camZ;

        if (!isCollidingWithBlock(nx, oy, oz)) {
            camX = nx;
        } else {
            camX = ox;
            velX = 0;
        }

        if (!isCollidingWithBlock(camX, ny, oz)) {
            camY = ny;
            if (velY < 0) onGround = false;
        } else {
            if (velY < 0) onGround = true;
            camY = oy;
            velY = 0;
        }

        if (!isCollidingWithBlock(camX, camY, nz)) {
            camZ = nz;
        } else {
            camZ = oz;
            velZ = 0;
        }

        for (int i = 0; i < 10; i++) {
            if (!isCollidingWithBlock(camX, camY, camZ)) break;

            if (velX != 0) {
                camX -= velX > 0 ? 0.001f : -0.001f;
            } else if (velY != 0) {
                camY -= velY > 0 ? 0.001f : -0.001f;
            } else if (velZ != 0) {
                camZ -= velZ > 0 ? 0.001f : -0.001f;
            } else {
                camY += 0.01f;
            }
        }
    }

    private static void ensurePlayerNotStuck() {
        for (int i = 0; i < 200; i++) {
            if (!isCollidingWithBlock(camX, camY, camZ)) return;
            camY += 0.5f;
        }
        camX = SPAWN_X; camY = SPAWN_Y; camZ = SPAWN_Z;
        velX = velY = velZ = 0;
    }

    private static void respawn() {
        camX = SPAWN_X;
        camY = SPAWN_Y;
        camZ = SPAWN_Z;
        velX = velY = velZ = 0;
        ensurePlayerNotStuck();
    }

    private static void physicsUpdate(float dt) {
        if (dt > 0.02f) dt = 0.02f;
        if (dt < 0.001f) return;

        onGround = false;
        velY += GRAVITY * dt;
        if (velY < MAX_FALL_SPEED) velY = MAX_FALL_SPEED;

        float yawRad = (float) Math.toRadians(yaw);
        float fwdX = (float) Math.cos(yawRad);
        float fwdZ = (float) Math.sin(yawRad);
        float rgtX = -(float) Math.sin(yawRad);
        float rgtZ = (float) Math.cos(yawRad);

        float mvX = 0, mvZ = 0;

        if (glfwGetKey(window_root, GLFW_KEY_W) == GLFW_PRESS) { mvX += fwdX; mvZ += fwdZ; }
        if (glfwGetKey(window_root, GLFW_KEY_S) == GLFW_PRESS) { mvX -= fwdX; mvZ -= fwdZ; }
        if (glfwGetKey(window_root, GLFW_KEY_A) == GLFW_PRESS) { mvX -= rgtX; mvZ -= rgtZ; }
        if (glfwGetKey(window_root, GLFW_KEY_D) == GLFW_PRESS) { mvX += rgtX; mvZ += rgtZ; }
        if (glfwGetMouseButton(window_root, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS) {
            int[] target = Flantyle.getHighlightBlock();
            if (target != null) {
                ChunkManager.worldRemoveBlock(target[0], target[1], target[2]);
            }
        }

        float mag = (float) Math.sqrt(mvX * mvX + mvZ * mvZ);
        if (mag > 0) {
            mvX /= mag;
            mvZ /= mag;
            velX = mvX * PLAYER_SPEED;
            velZ = mvZ * PLAYER_SPEED;
        } else {
            float slideDecay = (float) Math.pow(SLIDE_DECAY_PER_SECOND, dt);
            velX *= slideDecay;
            velZ *= slideDecay;
            if (Math.abs(velX) < SLIDE_STOP_THRESHOLD) velX = 0;
            if (Math.abs(velZ) < SLIDE_STOP_THRESHOLD) velZ = 0;
        }

        int steps = 8;
        float subDt = dt / steps;
        for (int i = 0; i < steps; i++) {
            float nx = camX + velX * subDt;
            float ny = camY + velY * subDt;
            float nz = camZ + velZ * subDt;
            resolveCollision(nx, ny, nz);
        }

        if (glfwGetKey(window_root, GLFW_KEY_SPACE) == GLFW_PRESS && onGround) {
            velY = JUMP_SPEED;
            onGround = false;
        }

        if (camY < -10) {
            respawn();
        }

        updateView();
    }

    private static void updateDrawPlayerPosition() {
        int px = (int) Math.floor(camX);
        int py = (int) Math.floor(camY);
        int pz = (int) Math.floor(camZ);

        float yawNorm = ((yaw % 360) + 360) % 360;

        drawText("XYZ: " + px + " / " + py + " / " + pz,
                10, 40, 2.0f, 1f, 1f, 1f);

        drawText("Facing: " + String.format("%.1f", yawNorm) + " (yaw) / "
                        + String.format("%.1f", pitch) + " (pitch)",
                10, 60, 2.0f, 1f, 1f, 1f);
    }

    private static void updateTargetBlock() {
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);

        float dx = (float) (Math.cos(yawRad) * Math.cos(pitchRad));
        float dy = (float) Math.sin(pitchRad);
        float dz = (float) (Math.sin(yawRad) * Math.cos(pitchRad));

        float ex = camX;
        float ey = camY + EYE_HEIGHT;
        float ez = camZ;

        int lastWx = Integer.MIN_VALUE;
        int lastWy = Integer.MIN_VALUE;
        int lastWz = Integer.MIN_VALUE;

        float step = 0.1f;
        int steps = (int) (REACH / step);

        for (int i = 0; i <= steps; i++) {
            float t = i * step;
            float cx = ex + dx * t;
            float cy = ey + dy * t;
            float cz = ez + dz * t;

            int wx = Math.floorDiv((int) Math.floor(cx + 1), 2) * 2;
            int wy = Math.floorDiv((int) Math.floor(cy + 1), 2) * 2;
            int wz = Math.floorDiv((int) Math.floor(cz + 1), 2) * 2;

            if (wx == lastWx && wy == lastWy && wz == lastWz) continue;
            lastWx = wx;
            lastWy = wy;
            lastWz = wz;

            if (ChunkManager.getBlock(wx, wy, wz) != null) {
                highlightBlock = new int[]{wx, wy, wz};
                highlightFace = computeFace(cx, cy, cz, wx, wy, wz);
                return;
            }
        }

        highlightBlock = null;
        highlightFace = null;
    }

    private static int[] computeFace(float px, float py, float pz, int wx, int wy, int wz) {
        float lx = px - wx;
        float ly = py - wy;
        float lz = pz - wz;

        float ax = Math.abs(lx);
        float ay = Math.abs(ly);
        float az = Math.abs(lz);

        if (ax >= ay && ax >= az) {
            return new int[]{lx > 0 ? 1 : -1, 0, 0};
        } else if (ay >= az) {
            return new int[]{0, ly > 0 ? 1 : -1, 0};
        } else {
            return new int[]{0, 0, lz > 0 ? 1 : -1};
        }
    }

    private static void drawHighlightBox() {
        if (highlightBlock == null) return;

        glUseProgram(shaderProgram);
        glUniform1i(modeLoc, 1);
        glUniform4f(colorLoc, 1.0f, 1.0f, 1.0f, 1.0f);

        FloatBuffer matBuf = BufferUtils.createFloatBuffer(16);

        Matrix4f model = new Matrix4f()
                .translate(highlightBlock[0], highlightBlock[1], highlightBlock[2])
                .scale(1.02f);

        glUniformMatrix4fv(modelLoc, false, model.get(matBuf));
        glUniformMatrix4fv(viewLoc, false, view.get(matBuf));
        glUniformMatrix4fv(projLoc, false, projection.get(matBuf));

        glBindVertexArray(wireframeVao);
        glDrawArrays(GL_TRIANGLES, 0, 144);
        glBindVertexArray(0);
    }

    private static void drawCrosshair() {
        glUseProgram(shaderProgram);
        glUniform1i(modeLoc, 1);
        glUniform4f(colorLoc, 1.0f, 1.0f, 1.0f, 1.0f);

        FloatBuffer matBuf = BufferUtils.createFloatBuffer(16);
        Matrix4f identity = new Matrix4f();
        glUniformMatrix4fv(modelLoc, false, identity.get(matBuf));
        glUniformMatrix4fv(viewLoc, false, identity.get(matBuf));
        glUniformMatrix4fv(projLoc, false, identity.get(matBuf));

        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glBindVertexArray(crosshairVao);
        glDrawArrays(GL_TRIANGLES, 0, 12);
        glBindVertexArray(0);

        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
    }

    public static void worldAddBlock(int x, int y, int z, String blockType) {
        ChunkManager.worldAddBlock(x * 2, y * 2, z * 2, blockType);
    }

    public static int[] getHighlightBlock() {
        return highlightBlock;
    }

    public static int[] getHighlightFace() {
        return highlightFace;
    }

    public static void drawText(String text, float pixelX, float pixelY, float scale,
                                float r, float g, float b) {
        if (text == null || text.isEmpty()) return;
        if (textVao == 0) return;

        textVertexBuffer.clear();

        int quads = STBEasyFont.stb_easy_font_print(0, 0, text, null, textVertexBuffer);
        if (quads == 0) return;

        float[] triData = new float[quads * 6 * 5];
        int outIdx = 0;

        for (int q = 0; q < quads; q++) {
            float[] qv = new float[8];
            for (int v = 0; v < 4; v++) {
                int offset = q * 4 * 16 + v * 16;
                qv[v * 2] = textVertexBuffer.getFloat(offset);
                qv[v * 2 + 1] = textVertexBuffer.getFloat(offset + 4);
            }

            int[] triOrder = {0, 1, 2, 0, 2, 3};
            for (int t : triOrder) {
                float px = qv[t * 2] * scale + pixelX;
                float py = qv[t * 2 + 1] * scale + pixelY;

                float ndcX = (px / winWidth) * 2f - 1f;
                float ndcY = 1f - (py / winHeight) * 2f;

                triData[outIdx++] = ndcX;
                triData[outIdx++] = ndcY;
                triData[outIdx++] = 0f;
                triData[outIdx++] = 0f;
                triData[outIdx++] = 0f;
            }
        }

        glUseProgram(shaderProgram);
        glUniform1i(modeLoc, 1);
        glUniform4f(colorLoc, r, g, b, 1f);

        FloatBuffer matBuf = BufferUtils.createFloatBuffer(16);
        Matrix4f identity = new Matrix4f();
        glUniformMatrix4fv(modelLoc, false, identity.get(matBuf));
        glUniformMatrix4fv(viewLoc, false, identity.get(matBuf));
        glUniformMatrix4fv(projLoc, false, identity.get(matBuf));

        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glBindVertexArray(textVao);
        glBindBuffer(GL_ARRAY_BUFFER, textVbo);
        FloatBuffer fb = BufferUtils.createFloatBuffer(triData.length);
        fb.put(triData).flip();
        glBufferData(GL_ARRAY_BUFFER, fb, GL_DYNAMIC_DRAW);
        glDrawArrays(GL_TRIANGLES, 0, triData.length / 5);
        glBindVertexArray(0);

        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
    }

    public static void Render() {
        glUseProgram(shaderProgram);
        glUniform1i(modeLoc, 0);

        glUseProgram(shaderProgram);
        glUniform1i(modeLoc, 0);
        glUniform3f(lightLoc, 0.5f, 1.0f, 0.3f);

        FloatBuffer matBuf = BufferUtils.createFloatBuffer(16);

        Matrix4f identity = new Matrix4f();
        glUniformMatrix4fv(modelLoc, false, identity.get(matBuf));
        glUniformMatrix4fv(viewLoc, false, view.get(matBuf));
        glUniformMatrix4fv(projLoc, false, projection.get(matBuf));

        int pcx = ChunkSystem.worldToChunkCoord((int) Math.floor(camX));
        int pcz = ChunkSystem.worldToChunkCoord((int) Math.floor(camZ));

        for (ChunkSystem chunkSystem : ChunkManager.getAllChunks().values()) {
            int dx = chunkSystem.getCx() - pcx;
            int dz = chunkSystem.getCz() - pcz;

            if (Math.abs(dx) > chunkRenderSize || Math.abs(dz) > chunkRenderSize) {
                continue;
            }

            for (Map.Entry<String, Integer> e : chunkSystem.getVaoMap().entrySet()) {
                String texName = e.getKey();
                int texId;

                if (texName.equals(FALLBACK_TEX)) {
                    texId = fallbackTextureId;
                } else {
                    Integer id = textureMap.get(texName);
                    texId = (id == null || id == 0) ? fallbackTextureId : id;
                }

                glActiveTexture(GL_TEXTURE0);
                glBindTexture(GL_TEXTURE_2D, texId);
                glUniform1i(texLoc, 0);

                glBindVertexArray(e.getValue());
                glDrawArrays(GL_TRIANGLES, 0, chunkSystem.getVertexCountMap().get(texName));
            }
        }

        drawHighlightBox();
        drawCrosshair();
        updateDrawPlayerPosition();

        drawText("Flantyle Indev 1.0.0", 13f, 13f, 3.0f, 0.25f, 0.25f, 0.25f);
        drawText("Flantyle Indev 1.0.0", 10, 10, 3.0f, 1f, 1f, 1f);
    }

    public static void main(String[] args) {
        ProgramKey.getProgramRuntimeKey(args);

        initializeGLFW();
        System.out.println("[INFO-MAIN] Okay... Initialize GLFW");

        window_root = glfwCreateWindow(800, 600, "Flantyle 1.0.0 Alpha Java Edition", 0, 0);
        if (window_root == MemoryUtil.NULL) {
            glfwTerminate();
            throw new RuntimeException("[ERROR-MAIN] Fail... Create window failed!");
        }

        try {
            glfwShowWindow(window_root);
            glfwMakeContextCurrent(window_root);
            glfwSwapInterval(1);
            GL.createCapabilities();
            glEnable(GL_DEPTH_TEST);

            setupShaders();
            initOverlayMeshes();
            setupMatrices();
            updateView();

            fallbackTextureId = createFallbackTexture();
            System.out.println("[INFO-RESOURCE] Fallback texture id: " + fallbackTextureId);

            ResourceRegistry.registerAll();
            loadAllTextures();

            BlockType.registerAll();
            StructureManager.registerAll();

            WorldGenerator.setSeed(12345L);
            ChunkManager.preloadChunks(camX, camZ);

            System.out.println("[INFO-MESH] Total chunks: " + ChunkManager.getAllChunks().size());

            framebufferSizeCallback = GLFWFramebufferSizeCallback.create((win, w, h) -> changeSize(w, h));
            glfwSetFramebufferSizeCallback(window_root, framebufferSizeCallback);

            cursorPosCallback = GLFWCursorPosCallback.create((win, xpos, ypos) -> {
                if (firstMouse) {
                    lastMouseX = xpos;
                    lastMouseY = ypos;
                    firstMouse = false;
                }
                float dx = (float) (xpos - lastMouseX);
                float dy = (float) (lastMouseY - ypos);
                lastMouseX = xpos;
                lastMouseY = ypos;

                yaw += dx * SENSITIVITY;
                pitch += dy * SENSITIVITY;
                pitch = Math.max(-89.0f, Math.min(89.0f, pitch));
            });
            glfwSetCursorPosCallback(window_root, cursorPosCallback);
            glfwSetInputMode(window_root, GLFW_CURSOR, GLFW_CURSOR_DISABLED);

            lastFrameTime = glfwGetTime();
            while (!glfwWindowShouldClose(window_root)) {
                double now = glfwGetTime();
                float dt = (float) (now - lastFrameTime);
                lastFrameTime = now;

                if (dt > 0.1f) dt = 0.1f;

                GL11.glClearColor(0.5f, 0.7f, 1.0f, 1.0f);
                GL11.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

                physicsUpdate(dt);

                ChunkManager.updateLoadedChunks(camX, camZ);
                ChunkManager.updateAllDirtyChunks();

                updateTargetBlock();

                Render();

                glfwSwapBuffers(window_root);
                glfwPollEvents();
            }
        } catch (Exception e) {
            throw new RuntimeException("[ERROR-CRASH] Main loop error!", e);
        } finally {
            if (framebufferSizeCallback != null) framebufferSizeCallback.free();
            if (cursorPosCallback != null) cursorPosCallback.free();
            glfwTerminate();
        }
    }
}