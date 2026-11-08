#version 150

// 山海：解析圆弧覆盖率，圆形、胶囊、描边与焦点环使用同一轮廓。
uniform vec2 Size;
uniform float CornerRadius;
uniform float Stroke;
uniform vec4 EdgeColor;
uniform vec4 FillColor;
// x 为描边的旋转圈数，负值使用普通描边。y 为呼吸强度，z 为背光范围，0 表示绘制卡片本体。
uniform vec3 CardLight;
uniform vec4 ColorModulator;
in vec2 localPoint;
out vec4 fragColor;

void main() {
    vec2 halfSize = Size * 0.5;
    float radius = CornerRadius;
    vec2 local = localPoint - halfSize;
    vec2 q = abs(local) - halfSize + radius;
    vec2 arc = max(q, vec2(0.0));
    float arcLength = length(arc);
    float distance = arcLength + min(max(q.x, q.y), 0.0) - radius;
    // 对称的小圆可能在同一个像素四元组内取得相同距离。用解析法线估计覆盖宽度。
    vec2 normal = arcLength > 0.0 ? arc / arcLength
        : (q.x > q.y ? vec2(1.0, 0.0) : vec2(0.0, 1.0));
    normal *= mix(vec2(-1.0), vec2(1.0), step(vec2(0.0), local));
    float pixel = max(length(vec2(dot(normal, dFdx(localPoint)), dot(normal, dFdy(localPoint)))), 0.0001);
    float coverage = 1.0 - smoothstep(-0.5 * pixel, 0.5 * pixel, distance);
    if (CardLight.z > 0.0) {
        float below = localPoint.y - Size.y;
        vec2 delta = vec2(max(abs(local.x) - 0.65 * halfSize.x, 0.0), below + 0.75);
        float spread = length(delta) / CardLight.z;
        float falloff = exp(-4.5 * spread * spread) * (1.0 - smoothstep(0.72, 1.0, spread));
        float alpha = mix(0.24, 0.57, CardLight.y) * falloff
            * smoothstep(-1.5, 0.25, below) * (1.0 - coverage);
        if (alpha <= 0.0) discard;
        float hue = clamp(localPoint.x / Size.x, 0.0, 1.0);
        vec3 glow = mix(vec3(0.40, 0.37, 1.0), vec3(0.24, 0.66, 1.0), hue);
        fragColor = vec4(glow, alpha) * ColorModulator;
        return;
    }
    float interior = Stroke < min(halfSize.x, halfSize.y)
        ? 1.0 - smoothstep(-0.5 * pixel, 0.5 * pixel, distance + Stroke) : 0.0;
    float edgeAlpha = EdgeColor.a * (coverage - interior);
    float fillAlpha = FillColor.a * interior;
    float alpha = edgeAlpha + fillAlpha;
    if (alpha <= 0.0) discard;
    vec3 edge = EdgeColor.rgb;
    if (CardLight.x >= 0.0 && edgeAlpha > 0.0) {
        // 从中心量角，亮带和色相都连续地绕一圈。
        float angle = 6.2831853 * CardLight.x;
        vec2 axis = vec2(cos(angle), sin(angle));
        vec2 ray = local / max(length(local), 0.0001);
        float sweep = 0.5 + 0.5 * dot(axis, ray);
        float hue = 0.5 + 0.5 * dot(vec2(-axis.y, axis.x), ray);
        vec3 gradient = mix(vec3(0.66, 0.50, 0.98), vec3(0.27, 0.82, 1.0), hue);
        edge = mix(edge, gradient, 0.30 + 0.66 * sweep * sweep);
    }
    vec3 rgb = (edge * edgeAlpha + FillColor.rgb * fillAlpha) / alpha;
    fragColor = vec4(rgb, alpha) * ColorModulator;
}
