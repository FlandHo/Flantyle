#version 330 core

in vec2 vUV;
in vec3 vNormal;
in float vLight;
out vec4 FragColor;

uniform sampler2D textureSampler;
uniform int drawMode;
uniform vec4 solidColor;

void main() {
    if (drawMode == 0) {
        vec3 n = normalize(vNormal);
        float face;
        if (abs(n.y) > 0.5) {
            face = n.y > 0.0 ? 0.9 : 0.5;
        } else if (abs(n.x) > 0.5) {
            face = 0.6;
        } else {
            face = 0.8;
        }

        float ambient = 0.30;
        float light = ambient + vLight * face;
        FragColor = texture(textureSampler, vUV) * clamp(light, 0.0, 1.2);
    } else {
        FragColor = solidColor;
    }
}