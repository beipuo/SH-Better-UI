#version 150
// 山海：粒子沿用 GUI 顶点、外层色调与变换。
in vec3 Position;
in vec2 UV0;
in vec4 Color;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 InversePose;
out vec2 local;
out vec2 areaPosition;
out vec4 particleColor;
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    local = UV0;
    areaPosition = (InversePose * vec4(Position, 1.0)).xy;
    particleColor = Color;
}
