#version 150

// 山海：每个网格单元计算一次亮度、可见度与波峰位移。
uniform vec4 Area;
uniform ivec2 Grid;
uniform int Shape;
uniform int Fade;
uniform vec4 Surface;
uniform vec4 Lighting;
uniform vec4 Finish;
uniform vec4 Ripple0;
uniform vec4 Ripple1;
uniform vec4 Ripple2;
uniform vec4 Ripple3;
uniform vec4 Ripple4;
uniform vec4 Ripple5;
uniform vec4 Ripple6;
uniform vec4 Ripple7;

out vec4 fragColor;

float wave(vec2 p, float time) {
    if (Shape == 1) {
        float crest = 3.0 * p.x + 0.7 * sin(p.y * 0.95 + time) - time * 2.0;
        return (cos(crest) + 0.25 * cos(2.0 * crest - 0.5)) * 0.78;
    }
    if (Shape == 2) {
        float distance = length(p + vec2(3.4, 0.4));
        return cos(distance * 2.7 - time * 2.0) / (1.0 + 0.12 * distance);
    }
    float drift = 1.45 * sin(p.y * 0.9 + time) + 0.55 * sin(p.y * 1.8 - p.x * 0.72 - time);
    float fold = p.x * 2.7 + drift - time;
    return (sin(fold) + 0.28 * cos(2.0 * fold)) * (0.7 + 0.24 * cos(p.y + p.x * 0.4 - time));
}

float ripple(vec2 point, vec4 source) {
    if (source.w <= 0.0) return 0.0;
    float radius = length(point - source.xy) / Finish.w;
    float front = radius - 2.4 * source.z;
    if (abs(front) > 1.1) return 0.0;
    float envelope = 1.0 - smoothstep(0.0, 1.1, abs(front));
    float decay = 1.0 - source.z;
    return -source.w * cos(front * 7.0) * envelope * decay * decay;
}

float heightAt(vec2 point, vec2 heading, float rise) {
    vec2 p = (point - Area.xy * 0.5) / (140.0 * Surface.z);
    vec2 oriented = vec2(dot(p, heading), dot(p, vec2(-heading.y, heading.x)));
    return rise * wave(oriented, Surface.x)
        + ripple(point, Ripple0) + ripple(point, Ripple1) + ripple(point, Ripple2) + ripple(point, Ripple3)
        + ripple(point, Ripple4) + ripple(point, Ripple5) + ripple(point, Ripple6) + ripple(point, Ripple7);
}

float edgeFade(vec2 uv) {
    vec2 center = uv * 2.0 - 1.0;
    if (Fade == 1) return 1.0 - smoothstep(1.0 - Finish.y, 1.05, length(center));
    if (Fade == 2) return mix(0.05, 1.0, smoothstep(0.0, Finish.y, length(center)));
    if (Fade == 3) return 1.0 - smoothstep(1.0 - Finish.y, 1.0, uv.y);
    if (Fade == 4) return smoothstep(0.0, Finish.y, uv.y);
    return 1.0;
}

void main() {
    vec2 point = (gl_FragCoord.xy - vec2(Grid) * 0.5) * Area.zw + Area.xy * 0.5;
    vec2 uv = point / Area.xy;
    float direction = radians(Lighting.w);
    vec2 heading = vec2(cos(direction), sin(direction));
    float rise = smoothstep(0.1, 1.0, Surface.w);
    float h = heightAt(point, heading, rise);
    float stepSize = clamp(Area.w * 0.25, 1.0, 6.0);
    vec2 slope = vec2(heightAt(point + vec2(stepSize, 0.0), heading, rise) - h,
        heightAt(point + vec2(0.0, stepSize), heading, rise) - h) * (65.0 * Surface.y / stepSize);
    vec3 normal = normalize(vec3(-slope, 1.0));
    float lightAngle = direction + radians(180.0 + Lighting.x + (1.0 - rise) * 70.0);
    vec3 light = normalize(vec3(cos(lightAngle), sin(lightAngle), 0.78));
    float diffuse = max(dot(normal, light), 0.0);
    float glint = Lighting.y * pow(max(dot(normal, normalize(light + vec3(0.0, 0.0, 1.0))), 0.0), 56.0);
    float tone = clamp(0.42 + (0.8 * diffuse + glint - 0.42) * Lighting.z, 0.0, 1.0);
    float level = Finish.z > 0.5 ? pow(1.0 - tone, 2.0) : pow(tone, 2.0);
    float reach = clamp(length(point - Area.xy * 0.5) / (length(Area.xy) * 0.5), 0.0, 1.0);
    float appear = smoothstep(reach, reach + 0.24, 1.4 * Surface.w);
    float alpha = Finish.x * edgeFade(uv) * appear * (0.18 + 0.82 * level);
    float lift = clamp(-h * Surface.y * 0.37, -0.46, 0.46);
    fragColor = vec4(level, alpha, min(glint, 1.0), lift + 0.5);
}
