#version 150

// 山海：局部 UV 随控件的 pose 移动、缩放和裁切。
in vec3 Position;
in vec2 UV0;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int EffectMode;
out vec2 localUv;

void main() {
    gl_Position = EffectMode == 2
            ? vec4(Position.x, -Position.y, 0.0, 1.0)
            : ProjMat * ModelViewMat * vec4(Position, 1.0);
    localUv = UV0;
}
