#version 150

// 山海：图标随 GUI 的 pose 移动和缩放。
in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 iconUv;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    iconUv = UV0;
}
