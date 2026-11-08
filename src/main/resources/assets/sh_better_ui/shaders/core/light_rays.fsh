#version 150

// 山海：按 GUI 区域的局部坐标绘制光束，使用直通 alpha 与原生 GUI 合成。
in vec2 iconUv;
out vec4 fragColor;

uniform vec4 ColorModulator;
uniform vec2 Area;
uniform vec4 Beam;       // source xy, direction xy
uniform vec4 Cone;       // length, fade distance, spread, density
uniform vec4 Material;   // sharpness, glow, haze, atmosphere
uniform vec4 Tint;
uniform vec4 Tone;       // intensity, saturation, grain, light mode
uniform vec4 Motion;     // time, opening progress, distortion, breathing
uniform vec3 Shadow;     // pointer xy, strength
uniform vec4 PulseAges;  // seconds; negative marks an empty slot

const float PULSE_SECONDS = 2.0;

float grain(vec2 p) {
    vec3 n = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    n += dot(n, n.yzx + 33.33);
    return fract((n.x + n.y) * n.z);
}

float pulse(float depth, float age) {
    if (age < 0.0 || age >= PULSE_SECONDS) return 0.0;
    float front = age * 1.1;
    float band = (depth - front) / (0.09 + age * 0.16);
    float strength = smoothstep(0.0, 0.16, age) * (1.0 - smoothstep(0.45, PULSE_SECONDS, age));
    return exp(-band * band) * strength;
}

void main() {
    vec2 delta = iconUv * Area - Beam.xy;
    float distanceToSource = length(delta);
    float depth = distanceToSource / max(Area.x, Area.y);
    vec2 direction = delta / max(distanceToSource, 0.0001);
    float forward = dot(direction, Beam.zw);
    float sideways = dot(direction, vec2(-Beam.w, Beam.z));
    float angle = atan(sideways, max(0.001, forward));
    float flow = Motion.x;
    angle += Motion.z * 0.035 * sin(depth * 13.0 - flow * 1.7);

    float frequency = 45.0 * Cone.w;
    float pattern = angle * frequency;
    float shafts = 0.52 + 0.26 * cos(pattern + flow * 1.25)
                       + 0.22 * cos(pattern * 1.73 - flow * 0.9 + 0.8);
    float edge = clamp(fwidth(pattern) * 0.25, mix(0.3, 0.045, Material.x), 0.5);
    float beam = mix(shafts, smoothstep(0.52 - edge, 0.52 + edge, shafts), Material.x);
    beam *= pow(clamp(forward, 0.0, 1.0), 1.0 / Cone.z);
    beam *= (1.0 - smoothstep(0.0, Cone.x, depth)) * (1.0 - 0.5 * smoothstep(0.0, Cone.y, depth));

    float spreadGlow = smoothstep(-0.2, 0.9, forward) * exp(-depth * 4.5);
    float opening = 1.0;
    if (Motion.y < 1.0) {
        float progress = 1.0 - pow(1.0 - Motion.y, 3.0);
        opening = smoothstep(0.0, 0.5, Motion.y) * (1.0 - smoothstep(-0.24, 0.0, depth - progress * 2.2));
    }
    if ((beam + spreadGlow * Material.y) * opening < 0.001) {
        fragColor = vec4(0.0);
        return;
    }

    if (Material.z > 0.0) {
        float cloud = sin(angle * 8.0 + depth * 11.0 + flow * 0.33);
        cloud *= cos(angle * 19.0 - depth * 8.0 + flow * 0.21);
        beam *= mix(1.0, 0.83 + 0.33 * cloud, Material.z);
    }
    if (Shadow.z > 0.001) {
        vec2 toPointer = Shadow.xy - Beam.xy;
        float pointerDistance = max(length(toPointer), 0.0001);
        vec2 shadowAxis = toPointer / pointerDistance;
        float beyondPointer = dot(delta, shadowAxis) / pointerDistance - 1.0;
        float offset = abs(dot(delta, vec2(-shadowAxis.y, shadowAxis.x)));
        float width = 12.0 + max(beyondPointer, 0.0) * pointerDistance * 0.19;
        float trail = smoothstep(-0.02, 0.24, beyondPointer) * exp(-max(beyondPointer, 0.0));
        beam *= 1.0 - 0.65 * Shadow.z * trail * (1.0 - smoothstep(width * 0.3, width, offset));
    }
    float waves = pulse(depth, PulseAges.x) + pulse(depth, PulseAges.y)
                + pulse(depth, PulseAges.z) + pulse(depth, PulseAges.w);
    float breathe = 1.0 - Motion.w * (0.15 + 0.15 * sin(flow * 3.4));
    float energy = (beam * breathe * (1.0 + waves) + spreadGlow * Material.y) * Tone.x * opening;
    if (Tone.z > 0.0) energy *= mix(1.0, grain(floor(gl_FragCoord.xy)), Tone.z);

    vec3 cool = mix(vec3(0.29, 0.48, 0.79), vec3(1.0, 0.96, 0.91), exp(-depth * 2.7));
    vec3 hue = mix(vec3(1.0), cool, Material.w) * Tint.rgb;
    hue = clamp(mix(vec3(dot(hue, vec3(0.299, 0.587, 0.114))), hue, Tone.y), 0.0, 1.0);
    float level = 1.0 - exp(-max(energy, 0.0));
    float alpha = level * Tint.a;
    if (Tone.w > 0.5) {
        hue *= 0.74;
        alpha *= 0.78 * level;
    } else {
        hue = mix(hue * 0.7, hue, smoothstep(0.2, 0.85, level));
    }
    fragColor = vec4(hue, alpha) * ColorModulator;
}
