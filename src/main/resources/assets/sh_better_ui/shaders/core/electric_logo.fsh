#version 150

// 山海：从图片距离场绘制电丝，在 GUI 坐标中计算光晕、指针波纹和电弧。
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform vec4 Field;    // 距离贴图宽高、GUI 单位 / texel、距离编码范围
uniform vec4 FromField; // 过渡来源宽高、GUI 单位 / texel、过渡进度
uniform vec2 AtlasSize;
uniform vec4 Region;   // 绘制区域宽高、线宽、电丝数
uniform vec4 Effect;   // 时间、弯曲、毛刺、闪烁
uniform vec4 Light;    // 亮度、辉光、填充、浅色背景模式
uniform vec4 CoreColor;
uniform vec4 GlowColor;
uniform vec4 Pointer;  // 坐标、半径、充电强度
uniform vec4 Pulse0;
uniform vec4 Pulse1;
uniform vec4 Pulse2;
uniform vec4 Arc0;
uniform vec4 Arc1;
uniform vec4 ArcMotion; // 两条电弧各自的弯曲量与强度

in vec2 iconUv;
out vec4 fragColor;

float signedDistance(vec2 p, vec3 field, float offset) {
    vec2 center = field.xy * 0.5;
    vec2 pixel = p / field.z + center;
    vec2 clamped = clamp(pixel, vec2(0.5), field.xy - 0.5);
    vec2 sampleRg = texture(Sampler0, (clamped + vec2(offset, 0.0)) / AtlasSize).rg;
    float encoded = dot(sampleRg, vec2(65280.0, 255.0)) / 65535.0;
    return (encoded * 2.0 - 1.0) * Field.w * field.z + length(p - (clamped - center) * field.z);
}

float hash(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * vec3(0.1031, 0.1099, 0.1379));
    q += dot(q, q.yzx + 19.19);
    return fract((q.x + q.z) * q.y);
}

float noise(vec2 p) {
    vec2 cell = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = mix(hash(cell), hash(cell + vec2(1.0, 0.0)), u.x);
    float b = mix(hash(cell + vec2(0.0, 1.0)), hash(cell + 1.0), u.x);
    return mix(a, b, u.y) * 2.0 - 1.0;
}

float ripple(vec2 p, vec4 pulse, inout vec2 displacement) {
    if (pulse.w == 0.0) return 0.0;
    vec2 delta = p - pulse.xy;
    float distance = length(delta);
    float front = pulse.z * max(Region.x, Region.y) * 0.85;
    float band = (distance - front) / max(5.0, min(Region.x, Region.y) * 0.045);
    float ring = exp(-band * band) * (1.0 - pulse.z);
    displacement += delta / max(distance, 1.0) * (ring * sin(band * 2.8) * 5.0);
    return ring;
}

float stroke(float distance, float radius, float aa) {
    return 1.0 - smoothstep(max(0.0, radius - aa), radius + aa, distance);
}

vec2 arcLight(vec2 p, vec4 ends, vec2 style, float seed, float aa) {
    if (style.y <= 0.0) return vec2(0.0);
    vec2 chord = ends.zw - ends.xy;
    float span = length(chord);
    if (span < 1.0) return vec2(0.0);
    vec2 axis = chord / span;
    vec2 normal = vec2(-axis.y, axis.x);
    vec2 local = p - ends.xy;
    float along = dot(local, axis);
    float side = dot(local, normal);
    if (along < -5.0 || along > span + 5.0 ||
        abs(side) > abs(style.x) + Effect.z * 2.0 + Region.z * 4.0 + 4.0) return vec2(0.0);

    float fraction = clamp(along / span, 0.0, 1.0);
    float arch = 4.0 * fraction * (1.0 - fraction);
    float roughness = noise(vec2(along * 0.22 + seed, Effect.x * 9.0));
    float path = arch * (style.x + roughness * Effect.z * 1.9);
    float beyond = along - clamp(along, 0.0, span);
    float distance = length(vec2(beyond, side - path));
    float radius = Region.z * (0.29 + arch * 0.10);
    return vec2(stroke(distance, radius, aa),
        exp(-distance / max(0.7, radius * 3.0)) * 0.65) * style.y;
}

void main() {
    vec2 p = (iconUv - 0.5) * Region.xy;
    float aa = max(0.18, 0.55 * max(fwidth(p.x), fwidth(p.y)));
    vec2 displacement = vec2(0.0);
    float wave = ripple(p, Pulse0, displacement);
    wave += ripple(p, Pulse1, displacement);
    wave += ripple(p, Pulse2, displacement);
    p -= displacement;

    vec2 hoverDelta = (p - Pointer.xy) / Pointer.z;
    float heat = min(2.5, Pointer.w * exp(-dot(hoverDelta, hoverDelta)) + wave);
    float base = signedDistance(p, Field.xyz, 0.0);
    if (FromField.w < 1.0) {
        base = mix(signedDistance(p, FromField.xyz, Field.x), base, smoothstep(0.0, 1.0, FromField.w));
    }
    float edge = abs(base);
    float reach = 11.0 * Effect.y + 5.0 * Effect.z + 7.0 * Region.z + aa;
    vec2 energy = vec2(0.0);
    if (edge < reach) {
        for (int i = 0; i < 6; i++) {
            float strand = float(i);
            if (strand >= Region.w) break;
            bool mainStrand = i == 0;
            float seed = strand * 29.0;
            float t = Effect.x * (1.0 + 0.13 * strand);
            float flow = noise(p * 0.032 + vec2(seed + t * 0.75, -t * 0.45));
            float jag = noise(p * 0.27 + vec2(-t * 3.6, seed + t * 1.9));
            float spread = mainStrand ? 1.6 : 4.0 + strand * 1.0;
            float distance = abs(base - flow * Effect.y * spread - jag * Effect.z);
            float radius = Region.z * (mainStrand ? 0.49 : 0.25) * (1.0 + 0.35 * flow);
            float presence = mainStrand ? 1.0 : 0.16 + 0.61 * smoothstep(-0.52, 0.56, flow);
            float shimmer = 1.0 + Effect.w * 0.36 * noise(vec2(seed + t * 7.0, 3.5));
            float halo = exp(-distance / (Region.z * 1.45)) * 0.48;
            energy += vec2(stroke(distance, radius, aa), halo) * presence * shimmer;
        }
    }

    energy += arcLight(p, Arc0, ArcMotion.xy, 47.0, aa);
    energy += arcLight(p, Arc1, ArcMotion.zw, 91.0, aa);
    float bloomRadius = clamp(min(Region.x, Region.y) * 0.09, 3.0, 25.0);
    float bloom = exp(-edge / bloomRadius) * 0.32;
    float fill = (1.0 - smoothstep(-aa, aa, base)) * Light.z * (0.24 + bloom);

    float core = energy.x * CoreColor.a;
    float halo = (energy.y + bloom) * Light.y * mix(1.0, 0.28, Light.w) * GlowColor.a;
    float body = fill * GlowColor.a;
    float total = core + halo + body;
    float brightness = Light.x * (1.0 + 0.7 * heat);
    float alpha = (1.0 - exp(-total * brightness * 1.65)) * ColorModulator.a;
    if (alpha <= 0.003) discard;

    vec3 ink = mix(GlowColor.rgb, GlowColor.rgb * 0.62, Light.w);
    vec3 whiteHot = mix(CoreColor.rgb, vec3(1.0), Light.w * smoothstep(0.52, 1.0, core));
    vec3 color = (whiteHot * core + ink * (halo + body)) / max(total, 0.0001);
    fragColor = vec4(color * ColorModulator.rgb, alpha);
}
