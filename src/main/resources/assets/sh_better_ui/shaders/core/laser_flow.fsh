#version 150

// 山海：按 GUI 局部坐标计算落点、光晕和纹理遮罩。
uniform sampler2D RevealSampler;
uniform vec4 ColorModulator;
uniform vec2 Area;
uniform vec3 Beam;
uniform vec4 Surface;
uniform vec4 Tint;
uniform vec4 Shape;
uniform vec4 Material;
uniform vec4 Motion;
uniform vec3 Lens;
uniform vec4 Options;

in vec2 iconUv;
out vec4 fragColor;

float gaussian(float x) {
    return exp(-x * x);
}

float randomCell(vec2 cell) {
    vec3 key = fract(vec3(cell.xyx) * vec3(.1031, .11369, .13787));
    key += dot(key, key.yzx + 19.19);
    return fract((key.x + key.z) * key.y);
}

float mistNoise(vec2 p) {
    vec2 cell = floor(p);
    vec2 uv = smoothstep(vec2(0), vec2(1), fract(p));
    float lower = mix(randomCell(cell), randomCell(cell + vec2(1, 0)), uv.x);
    float upper = mix(randomCell(cell + vec2(0, 1)), randomCell(cell + vec2(1, 1)), uv.x);
    return mix(lower, upper, uv.y);
}

float panelDistance(vec2 p, vec2 halfSize, float radius) {
    vec2 edge = abs(p) - halfSize + radius;
    return length(max(edge, 0.0)) + min(max(edge.x, edge.y), 0.0) - radius;
}

void main() {
    vec2 local = iconUv * Area;
    float unit = clamp(min(Area.x, Area.y) / 360.0, .28, 2.5);
    vec2 p = (local - Beam.xy) / unit;
    float pixel = max(fwidth(p.x), fwidth(p.y));
    float phase = Motion.x;
    float seed = Motion.w * .017;
    float front = mix(-Beam.y / unit - 10.0, 16.0, smoothstep(0.0, .5, Motion.y));
    float beamGate = (1.0 - smoothstep(front - 12.0, front, p.y))
                   * (1.0 - smoothstep(-1.0, 6.0, p.y));
    float surfaceGate = smoothstep(.32, .7, Motion.y);
    float run = smoothstep(.42, 1.0, Motion.y);

    float coreWidth = max(Shape.x * 1.5, pixel * .7);
    float core = gaussian(p.x / coreWidth) * beamGate;
    float glow = (.48 * gaussian(p.x / (coreWidth + 7.0))
                + .16 * gaussian(p.x / (coreWidth + 27.0))) * beamGate;

    float left = (Surface.x - Beam.x) / unit;
    float right = (Surface.z - Beam.x) / unit;
    float top = (Surface.y - Beam.y) / unit;
    float corner = Beam.z / unit;
    float height = Surface.w / unit;
    float onTop = step(left, p.x) * step(p.x, right);
    float curveX = p.x - clamp(p.x, left + corner, right - corner);
    float rimY = top + corner - sqrt(max(0.0, corner * corner - curveX * curveX));
    float aboveRim = p.y - rimY;
    float distanceFromBeam = abs(p.x);
    float reach = Shape.z * 85.0 * run;
    float alongRim = exp(-distanceFromBeam / max(reach, 1.0)) * onTop;
    float runGate = smoothstep(0.0, 1.0, reach);
    float breathe = 1.0 + .22 * sin(phase * 2.3) * (1.0 - exp(-Motion.z * .5));

    float flareRadius = 18.0 * Shape.y * breathe;
    float flare = gaussian(p.x / max(flareRadius, 1.0))
                * gaussian(p.y / max(flareRadius * .46, 1.0))
                * Shape.y * surfaceGate;
    float rim = gaussian(aboveRim / max(1.2, pixel)) * alongRim * runGate * surfaceGate;
    float spread = gaussian(aboveRim / (8.0 + 5.0 * Shape.z))
                 * alongRim * runGate * surfaceGate;
    float streaks = 0.0;
    if (Material.z > 0.0 && reach > 0.0) {
        float lanes = abs(fract(aboveRim / 4.6) - .5);
        float filaments = 1.0 - smoothstep(.045, .18 + pixel * .08, lanes);
        float flow = mistNoise(vec2(distanceFromBeam * .065 - phase * 2.2, floor(aboveRim / 4.6) + seed));
        streaks = filaments * flow * spread * Material.z * .38;
    }

    float spill = 0.0;
    float outsidePanel = 1.0;
    if (height > 0.0) {
        vec2 halfSize = vec2((right - left) * .5, height * .5);
        vec2 panelCenter = vec2(left, top) + halfSize;
        float edge = panelDistance(p - panelCenter, halfSize, corner);
        outsidePanel = smoothstep(-8.0, -1.0, edge);
        if (Shape.w > 0.0 && reach > 0.0) {
            float fall = max(0.0, p.y - top - corner * .45);
            float sideX = p.x < panelCenter.x ? left : right;
            float fromCorner = exp(-abs(sideX) / max(reach, 1.0));
            float nearSide = gaussian((p.x - sideX) / (12.0 + fall * .14));
            float length = (24.0 + Shape.w * 35.0) * max(run, .01);
            float sheet = gaussian(max(edge, 0.0) / (2.5 + fall * .09));
            spill = sheet * nearSide * fromCorner * gaussian(fall / length)
                  * smoothstep(0.0, 5.0, fall) * runGate * Shape.w * surfaceGate;
        }
    }

    float halo = glow + spread + spill + flare * .55;
    float fog = 0.0;
    if (Material.x > 0.0) {
        vec2 drift = p * vec2(.029, .021) + vec2(seed, -phase * .46);
        float mist = .72 * mistNoise(drift) + .28 * mistNoise(drift * 2.36 + 7.0);
        float envelope = gaussian(p.x / (coreWidth + 48.0)) * beamGate * .55 + halo;
        fog = smoothstep(.24, .82, mist) * envelope * Material.x;
    }
    float dust = 0.0;
    if (Material.y > 0.0) {
        vec2 drift = (p - vec2(0, phase * 12.0)) / vec2(13, 19);
        vec2 cell = floor(drift) + seed;
        vec2 center = mix(vec2(.24), vec2(.76),
                          vec2(randomCell(cell), randomCell(cell + 17.0)));
        float point = length((fract(drift) - center) * vec2(13, 19));
        float speck = 1.0 - smoothstep(.22, .9 + pixel * .6, point);
        float sparkle = .48 + .52 * sin(randomCell(cell + 3.0) * 6.283 + phase * 3.0);
        dust = speck * max(0.0, sparkle) * halo * Material.y * .65;
    }
    float dots = 0.0;
    if (Material.w > 0.0) {
        vec2 dotUv = fract(p / 4.6) - .5;
        dots = gaussian(length(dotUv) * (5.0 + pixel)) * halo * Material.w * .15;
    }

    float cursorDistance = distance(local, Lens.xy);
    float cursorLight = 1.0 + .75 * Lens.z * gaussian(cursorDistance / (unit * 62.0));
    float bright = core * 2.0 + flare + rim * 1.3 + dust;
    float soft = glow + spread * .48 + spill * .8 + fog + streaks + dots;
    float energy = (bright + soft) * Options.x * cursorLight * outsidePanel;
    float lightAlpha = (1.0 - exp(-energy)) * Tint.a;
    vec3 darkTint = mix(Tint.rgb, vec3(1.0), (1.0 - exp(-bright)) * .86);
    vec3 lightTint = mix(Tint.rgb * .40, Tint.rgb, (1.0 - exp(-bright)) * .6);
    vec3 lightColor = mix(darkTint, lightTint, Options.y);
    lightAlpha *= mix(1.0, .78, Options.y);

    vec4 image = vec4(0.0);
    if (Options.w > 0.0 && cursorDistance < Options.z) {
        image = texture(RevealSampler, iconUv);
        float lens = 1.0 - smoothstep(Options.z * .28, Options.z, cursorDistance);
        image.a *= lens * Options.w * Tint.a * (1.0 - exp(-energy * 2.0));
    }
    float alpha = lightAlpha + image.a * (1.0 - lightAlpha);
    if (alpha < .001) discard;
    vec3 rgb = (lightColor * lightAlpha + image.rgb * image.a * (1.0 - lightAlpha)) / alpha;
    fragColor = vec4(rgb, alpha) * ColorModulator;
}
