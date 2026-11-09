#version 150
// Adapted by dishanhai from Link-Lyric (beipuo).
// Source: https://github.com/beipuo/Link-Lyric/tree/1a7cd274c3e7afbb33c08c66414ec0def944643b
// See META-INF/third-party/link-lyric for licenses and upstream notices.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 texCoord0;
out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    texCoord0 = UV0;
    // GUI 文字不吃光照：HUD 上没有世界光照图可采。
    //
    // 原版 rendertype_text.vsh 会乘 texelFetch(Sampler2, UV2/16, 0)，
    // 那是因为它的 CompositeState 配了 lightmap 状态、Sampler2 已绑定。
    // 这里没有那套状态，若照样采样会拿到 0，vertexColor 全零，
    // 字形整片透明——曾经的表现就是「正在着色的字不见了」。
    vertexColor = Color;
}
