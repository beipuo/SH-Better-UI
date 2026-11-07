#version 150

// 山海：解析圆弧覆盖率，圆形、胶囊、描边与焦点环使用同一轮廓。
uniform vec2 Size;
uniform float Stroke;
uniform vec4 EdgeColor;
uniform vec4 FillColor;
uniform vec4 ColorModulator;
in vec2 localPoint;
out vec4 fragColor;

void main() {
    vec2 halfSize = Size * 0.5;
    float radius = min(halfSize.x, halfSize.y);
    vec2 q = abs(localPoint - halfSize) - halfSize + radius;
    float distance = length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - radius;
    float pixel = max(length(vec2(dFdx(distance), dFdy(distance))), 0.0001);
    float coverage = 1.0 - smoothstep(-0.5 * pixel, 0.5 * pixel, distance);
    float interior = 1.0 - smoothstep(-0.5 * pixel, 0.5 * pixel, distance + Stroke);
    float edgeAlpha = EdgeColor.a * (coverage - interior);
    float fillAlpha = FillColor.a * interior;
    float alpha = edgeAlpha + fillAlpha;
    if (alpha <= 0.0) discard;
    vec3 rgb = (EdgeColor.rgb * edgeAlpha + FillColor.rgb * fillAlpha) / alpha;
    fragColor = vec4(rgb, alpha) * ColorModulator;
}
