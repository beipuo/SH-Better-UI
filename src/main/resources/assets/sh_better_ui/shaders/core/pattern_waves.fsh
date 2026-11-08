#version 150

// 山海：从波面网格绘制图案，最终输出直通 alpha 供 GUI 合成。
uniform sampler2D FieldSampler;
uniform sampler2D GlyphSampler;
uniform vec4 ColorModulator;
uniform vec4 Area;
uniform ivec2 Grid;
uniform int Pattern;
uniform int GlyphCount;
uniform float MarkSize;
uniform vec4 InkColor;
uniform vec4 BackgroundColor;

in vec2 iconUv;
out vec4 fragColor;

float boxDistance(vec2 p, vec2 halfSize) {
    vec2 distance = abs(p) - halfSize;
    return length(max(distance, 0.0)) + min(max(distance.x, distance.y), 0.0);
}

float glyph(vec2 local, float span, float level) {
    vec2 uv = local / span + 0.5;
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) return 0.0;
    int index = min(int(level * float(GlyphCount)), GlyphCount - 1);
    vec2 tile = vec2(index % 8, index / 8);
    return texture(GlyphSampler, (tile * 32.0 + mix(vec2(0.5), vec2(31.5), uv)) / 256.0).a;
}

float mark(vec2 local, float level, float aa) {
    float span = Area.w * MarkSize;
    if (Pattern == 4) return glyph(local, span, level);
    float radius = span * 0.5 * mix(0.12, 1.0, sqrt(level));
    float distance;
    if (Pattern == 0) distance = length(local) - radius;
    else if (Pattern == 1) distance = boxDistance(local, vec2(radius));
    else {
        float stroke = min(radius, max(0.5, Area.w * 0.055));
        distance = min(boxDistance(local, vec2(radius, stroke)), boxDistance(local, vec2(stroke, radius)));
    }
    return 1.0 - smoothstep(-aa, aa, distance);
}

void main() {
    vec2 point = iconUv * Area.xy;
    float aa = max(0.05, 0.5 * max(length(dFdx(point)), length(dFdy(point))));
    vec2 gridPoint = (point - Area.xy * 0.5) / Area.zw + vec2(Grid) * 0.5;
    int column = int(floor(gridPoint.x));
    int row = int(floor(gridPoint.y));
    float alpha = 0.0;
    float glint = 0.0;
    for (int dy = -1; dy <= 1; dy++) {
        int y = row + dy;
        if (y < 0 || y >= Grid.y) continue;
        vec4 cell;
        float coverage;
        if (Pattern == 3) {
            float x = gridPoint.x - 0.5;
            int left = clamp(int(floor(x)), 0, Grid.x - 2);
            vec4 a = texelFetch(FieldSampler, ivec2(left, y), 0);
            vec4 b = texelFetch(FieldSampler, ivec2(left + 1, y), 0);
            cell = mix(a, b, clamp(x - float(left), 0.0, 1.0));
            float slope = (b.a - a.a) * Area.w / Area.z;
            float dist = abs(gridPoint.y - float(y) - cell.a) * Area.w / sqrt(1.0 + slope * slope);
            float halfWidth = 0.5 * max(0.65, Area.w * MarkSize * sqrt(cell.r));
            coverage = 1.0 - smoothstep(halfWidth - aa, halfWidth + aa, dist);
        } else {
            cell = texelFetch(FieldSampler, ivec2(column, y), 0);
            vec2 offset = (gridPoint - vec2(float(column) + 0.5, float(y) + cell.a)) * Area.zw;
            coverage = mark(offset, cell.r, aa);
        }
        float candidate = coverage * cell.g;
        if (candidate > alpha) {
            alpha = candidate;
            glint = cell.b;
        }
    }
    float backgroundLight = BackgroundColor.a > 0.02 ? dot(BackgroundColor.rgb, vec3(0.2126, 0.7152, 0.0722)) : 0.5;
    bool darkInk = dot(InkColor.rgb, vec3(0.2126, 0.7152, 0.0722)) < backgroundLight;
    vec3 ink = mix(InkColor.rgb, vec3(darkInk ? 0.0 : 1.0), glint * 0.55);
    alpha *= InkColor.a;
    float visibleBackground = BackgroundColor.a * (1.0 - alpha);
    float totalAlpha = alpha + visibleBackground;
    vec3 rgb = (ink * alpha + BackgroundColor.rgb * visibleBackground) / max(totalAlpha, 0.00001);
    fragColor = vec4(rgb, totalAlpha) * ColorModulator;
}
