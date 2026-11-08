#version 150

// 山海：解析圆弧覆盖率，圆形、胶囊、描边与焦点环使用同一轮廓。
uniform vec2 Size;
uniform float CornerRadius;
uniform float Stroke;
uniform vec4 EdgeColor;
uniform vec4 FillColor;
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
    float interior = Stroke < min(halfSize.x, halfSize.y)
        ? 1.0 - smoothstep(-0.5 * pixel, 0.5 * pixel, distance + Stroke) : 0.0;
    float edgeAlpha = EdgeColor.a * (coverage - interior);
    float fillAlpha = FillColor.a * interior;
    float alpha = edgeAlpha + fillAlpha;
    if (alpha <= 0.0) discard;
    vec3 rgb = (EdgeColor.rgb * edgeAlpha + FillColor.rgb * fillAlpha) / alpha;
    fragColor = vec4(rgb, alpha) * ColorModulator;
}
