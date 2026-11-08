#version 150

// 山海：每个单元按波段选形，鼠标涟漪同时改变波段与颜色。
uniform sampler2D MaskSampler;
uniform vec4 ColorModulator;
uniform vec4 Area;
uniform vec4 Motion;
uniform vec4 Tone;
uniform vec4 Effect;
uniform vec4 MaskRect;
uniform int ShapeMode;
uniform vec4 InkColor;
uniform vec4 HoverColor;
uniform vec4 BackgroundColor;
uniform vec4 Ripple0;
uniform vec4 Ripple1;
uniform vec4 Ripple2;
uniform vec4 Ripple3;
uniform vec4 Ripple4;
uniform vec4 Ripple5;
uniform vec4 Ripple6;
uniform vec4 Ripple7;

in vec2 iconUv;
out vec4 fragColor;

const float TAU = 6.28318530718;
const float DRIFT_PERIOD = 4096.0;

float maskAlpha(vec2 uv) {
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) return 0.0;
    return textureLod(MaskSampler, uv, 0.0).a;
}

bool masked(vec2 center) {
    if (Effect.z < 0.5) return false;
    vec2 uv = (center - MaskRect.xy) / MaskRect.zw;
    vec2 stepUv = 0.28 * Area.z / MaskRect.zw;
    if (any(lessThan(uv + stepUv, vec2(0.0))) || any(greaterThan(uv - stepUv, vec2(1.0)))) return false;
    float coverage = maskAlpha(uv);
    coverage = max(coverage, maskAlpha(uv + stepUv));
    coverage = max(coverage, maskAlpha(uv - stepUv));
    coverage = max(coverage, maskAlpha(uv + vec2(stepUv.x, -stepUv.y)));
    coverage = max(coverage, maskAlpha(uv + vec2(-stepUv.x, stepUv.y)));
    return coverage > 0.28;
}

float swell(vec2 cell) {
    // 整数谐波与 4096 格的漂移周期对齐，回绕时波形连续。
    float repeats = max(1.0, floor(DRIFT_PERIOD / (25.0 * Motion.y) + 0.5));
    vec2 position = mod(cell - Motion.zw, DRIFT_PERIOD) * (TAU * repeats / DRIFT_PERIOD);
    float phase = Motion.x;
    float bend = 0.8 * sin(position.y + phase) + 0.45 * sin(position.x - 2.0 * phase);
    float fold = sin(2.0 * position.x + position.y + bend + phase);
    float counter = cos(2.0 * position.y - position.x + 2.0 * phase);
    return 0.58 * fold + 0.28 * counter + 0.14 * sin(position.x + 2.0 * position.y - phase);
}

vec2 splash(vec2 center, vec4 source) {
    if (source.w <= 0.0 || source.z >= 1.0) return vec2(0.0);
    float remaining = 1.0 - source.z;
    float distance = length(center - source.xy) / Effect.y;
    float offset = distance - 2.1 * source.z;
    float width = 0.72 + 0.48 * min(1.0, source.w);
    if (abs(offset) >= width) return vec2(0.0);
    float envelope = 1.0 - smoothstep(0.0, width, abs(offset));
    float energy = source.w * remaining * remaining * envelope;
    return vec2(cos(8.0 * offset) * energy, energy);
}

float shapeDistance(vec2 point, int shape, float radius) {
    if (shape == 1) {
        vec2 delta = abs(point) - radius;
        return length(max(delta, 0.0)) + min(max(delta.x, delta.y), 0.0);
    }
    if (shape == 2) return length(point) - radius;
    float sides = abs(point.x) * 0.8660254 - point.y * 0.5;
    return max(point.y, sides) - radius * 0.5;
}

void main() {
    vec2 point = iconUv * Area.xy;
    float aa = clamp(0.5 * max(length(dFdx(point)), length(dFdy(point))) / Area.z, 0.002, 0.35);
    vec2 middle = Area.xy * 0.5;
    vec2 cell = floor((point - middle) / Area.z + 0.5);
    vec2 center = middle + cell * Area.z;
    vec2 local = (point - center) / Area.z;
    float radial = clamp(length(center - middle) / max(length(middle), 1.0), 0.0, 1.0);
    float reveal = smoothstep(0.0, 0.20, Effect.x - 0.8 * radial);
    if (reveal <= 0.0 || masked(center)) {
        fragColor = BackgroundColor * ColorModulator;
        return;
    }

    vec2 charge = splash(center, Ripple0) + splash(center, Ripple1) + splash(center, Ripple2) + splash(center, Ripple3)
        + splash(center, Ripple4) + splash(center, Ripple5) + splash(center, Ripple6) + splash(center, Ripple7);
    float level = clamp(0.5 + (0.5 * swell(cell) + 0.65 * (Tone.y - 0.4)) * Tone.x, 0.0, 0.999);
    int band = int(mod(floor(level * 3.0 + charge.x * 3.5), 3.0));
    int shape = ShapeMode == 0 ? 3 - band : ShapeMode;
    float bandSize = ShapeMode == 0 ? 1.0 : 0.42 + 0.29 * float(band);
    float radius = min(0.495, 0.5 * Area.w * bandSize * reveal);
    float distance = shapeDistance(local, shape, radius);
    float coverage = 1.0 - smoothstep(-aa, aa, distance);

    float fadeStart = max(0.0, 1.0 - max(Tone.z * 2.0, 0.0001));
    float edge = Tone.z > 0.0 ? 1.0 - smoothstep(fadeStart, 1.0, radial) : 1.0;
    float front = 4.0 * reveal * (1.0 - reveal);
    float highlight = clamp(charge.y * 2.6 + front * 0.32, 0.0, 1.0);
    vec4 tint = mix(InkColor, HoverColor, highlight);
    if (Tone.w > 0.0) {
        // 解析光晕止于单元边界，避免为邻格重复计算波形。
        float space = min(0.16, 0.5 - radius);
        float halo = 1.0 - smoothstep(0.0, space, max(0.0, distance));
        float light = smoothstep(0.42, 0.92, dot(tint.rgb, vec3(0.2126, 0.7152, 0.0722)));
        coverage += (1.0 - coverage) * halo * light * Tone.w * 0.7;
    }
    float inkAlpha = coverage * edge * reveal * tint.a;
    float visibleBackground = BackgroundColor.a * (1.0 - inkAlpha);
    float alpha = inkAlpha + visibleBackground;
    vec3 color = (tint.rgb * inkAlpha + BackgroundColor.rgb * visibleBackground) / max(alpha, 0.00001);
    fragColor = vec4(color, alpha) * ColorModulator;
}
