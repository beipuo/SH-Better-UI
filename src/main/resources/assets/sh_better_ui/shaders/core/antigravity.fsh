#version 150
// 山海：根据 React Bits Antigravity 示例移植的四形状 SDF。
uniform vec4 ColorModulator;
uniform vec4 ClipRect;
uniform int ParticleShape;
in vec2 local;
in vec2 areaPosition;
in vec4 particleColor;
out vec4 fragColor;
void main() {
    if (any(lessThan(areaPosition, ClipRect.xy)) || any(greaterThanEqual(areaPosition, ClipRect.zw))) discard;
    vec2 p = local;
    float d;
    if (ParticleShape == 0) {
        // 从导数还原 GUI 半轴比例，胶囊端头保持圆形，GUI 非等比变换仍可正确缩放。
        vec2 uvX = dFdx(local), uvY = dFdy(local);
        vec2 guiX = dFdx(areaPosition), guiY = dFdy(areaPosition);
        float axisX = length(guiX * uvY.y - guiY * uvX.y);
        float axisY = length(guiY * uvX.x - guiX * uvY.x);
        float aspect = max(axisX / max(axisY, 0.000001), 1.0);
        d = length(vec2(max(abs(p.x) * aspect - (aspect - 1.0), 0.0), p.y)) - 1.0;
    } else if (ParticleShape == 1) {
        d = length(p) - 1.0;
    } else if (ParticleShape == 2) {
        vec2 q = abs(p) - vec2(0.72);
        d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - 0.28;
    } else {
        vec2 q = vec2(0.5774 - p.x, abs(p.y));
        d = max(dot(q, vec2(0.5, 0.8660254)) - 0.5, -q.x - 0.5) * 1.2;
    }
    float edge = max(fwidth(d), 0.0001);
    float coverage = clamp(0.5 - d / edge, 0.0, 1.0);
    vec4 color = particleColor * ColorModulator;
    fragColor = vec4(color.rgb, color.a * coverage);
    if (fragColor.a < 0.002) discard;
}
