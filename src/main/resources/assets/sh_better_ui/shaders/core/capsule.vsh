#version 150

// 山海：局部坐标独立于 pose，缩放后的像素宽度由片元导数计算。
in vec3 Position;
in vec2 UV0;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec2 localPoint;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    localPoint = UV0;
}
