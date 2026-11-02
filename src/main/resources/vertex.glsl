#version 330 core

layout (location = 0) in vec3 aPos;
layout (location = 1) in vec2 aUV;
layout (location = 2) in vec3 aNormal;
layout (location = 3) in float aLight;

out vec2 vUV;
out vec3 vNormal;
out float vLight;

uniform mat4 model;
uniform mat4 view;
uniform mat4 projection;

void main() {
    vUV = aUV;
    vNormal = normalize(mat3(model) * aNormal);
    vLight = aLight;
    gl_Position = projection * view * model * vec4(aPos, 1.0);
}