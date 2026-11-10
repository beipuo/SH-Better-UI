// Author: dishanhai
#version 150

uniform sampler2D Backdrop;
uniform vec4 Viewport;
uniform vec4 Geometry;      // framebuffer center and half extents
uniform float Radius;       // framebuffer pixels
uniform vec4 Optics;        // refraction px, lensing, scattering, edge highlight
uniform vec4 Material;      // transmission, thickness px, blur px, light foreground
uniform vec3 Light;         // local light xy (positive up), strength
uniform vec3 FlatColor;
uniform ivec3 Mode;         // geometry, sampling level, high contrast

out vec4 fragColor;

float distanceToGlass(vec2 p) {
    if (Mode.x == 2) {
        vec2 q = p / Geometry.zw;
        float lengthQ = length(q);
        float gradient = length(q / Geometry.zw);
        return gradient > 0.00001 ? (lengthQ - 1.0) * lengthQ / gradient : -min(Geometry.z, Geometry.w);
    }
    vec2 q = abs(p) - Geometry.zw + Radius;
    vec2 corner = max(q, 0.0);
    float arc = length(corner);
    if (Mode.x == 3) {
        vec2 squared = corner * corner;
        arc = sqrt(sqrt(dot(squared, squared)));
    }
    return arc + min(max(q.x, q.y), 0.0) - Radius;
}

vec3 scene(vec2 pixel) {
    vec2 uv = (pixel - Viewport.xy) / Viewport.zw;
    vec2 inset = 0.5 / vec2(textureSize(Backdrop, 0));
    return texture(Backdrop, clamp(uv, inset, 1.0 - inset)).rgb;
}

float luminance(vec3 rgb) {
    return dot(rgb, vec3(0.2126, 0.7152, 0.0722));
}

vec3 linearRgb(vec3 srgb) {
    return mix(srgb / 12.92, pow((srgb + 0.055) / 1.055, vec3(2.4)), step(vec3(0.04045), srgb));
}

vec3 srgb(vec3 linearColor) {
    return mix(linearColor * 12.92, 1.055 * pow(linearColor, vec3(1.0 / 2.4)) - 0.055,
            step(vec3(0.0031308), linearColor));
}

vec3 protectForeground(vec3 color) {
    vec3 linearColor = linearRgb(clamp(color, 0.0, 1.0));
    float level = luminance(linearColor);
    if (Material.w > 0.5) {
        float ceiling = Mode.z != 0 ? 0.10 : 0.18;
        linearColor *= min(1.0, ceiling / max(level, 0.0001));
    } else {
        float floorValue = Mode.z != 0 ? 0.30 : 0.18;
        linearColor = mix(linearColor, vec3(1.0), max(0.0, floorValue - level) / max(1.0 - level, 0.0001));
    }
    return srgb(linearColor);
}

void main() {
    vec2 pixel = gl_FragCoord.xy;
    vec2 local = pixel - Geometry.xy;
    float d = distanceToGlass(local);
    float aa = clamp(fwidth(d) * 0.75, 0.65, 1.5);
    float coverage = 1.0 - smoothstep(-aa, aa, d);
    if (Mode.y == 0) {
        fragColor = vec4(FlatColor, coverage);
        return;
    }

    float halfShort = min(Geometry.z, Geometry.w);
    float thickness = min(Material.y, halfShort);
    float shadowSize = max(1.0, min(18.0, thickness * 0.9 + 1.0));
    float shadow = (1.0 - coverage) * exp(-max(d, 0.0) / shadowSize)
            * (1.0 - smoothstep(shadowSize * 3.0, shadowSize * 4.0, d)) * 0.23;
    if (coverage <= 0.0) {
        fragColor = vec4(0.0, 0.0, 0.0, shadow);
        return;
    }

    float rimWidth = max(1.0, min(halfShort * 0.75, 1.5 + thickness * 1.7));
    float rim = 1.0 - smoothstep(0.0, rimWidth, max(-d, 0.0));
    vec2 normal = vec2(0.0);
    if (rim > 0.0) {
        vec2 gradient = vec2(distanceToGlass(local + vec2(0.7, 0.0)) - distanceToGlass(local - vec2(0.7, 0.0)),
                distanceToGlass(local + vec2(0.0, 0.7)) - distanceToGlass(local - vec2(0.0, 0.7)));
        normal = gradient / max(length(gradient), 0.001);
    }
    vec2 bent = pixel - normal * Optics.x * rim * (0.55 + 0.45 * rim)
            - local * Optics.y * 0.11 * rim * (1.0 - 0.65 * rim);

    vec3 sharp = scene(bent);
    vec3 filtered = sharp;
    float complexity = 0.0;
    if (Mode.y > 1 && Optics.z > 0.0 && Material.z > 0.0) {
        vec2 stepSize = vec2(Material.z * 0.78);
        vec3 axis = (scene(bent + vec2(stepSize.x, 0)) + scene(bent - vec2(stepSize.x, 0))
                + scene(bent + vec2(0, stepSize.y)) + scene(bent - vec2(0, stepSize.y))) * 0.25;
        filtered = mix(sharp, axis, 0.75);
        complexity = length(sharp - axis);
        if (Mode.y > 2) {
            vec2 diagonal = stepSize * 0.7;
            vec3 diagonals = (scene(bent + diagonal) + scene(bent - diagonal)
                    + scene(bent + vec2(diagonal.x, -diagonal.y))
                    + scene(bent + vec2(-diagonal.x, diagonal.y))) * 0.25;
            filtered = mix(filtered, diagonals, 0.30);
        }
    }
    vec3 transmitted = mix(sharp, filtered, Optics.z * mix(0.65, 1.0, smoothstep(0.04, 0.32, complexity)));
    float ambient = luminance(transmitted);
    vec3 neutral = mix(vec3(0.09, 0.11, 0.14), vec3(0.83, 0.86, 0.88), smoothstep(0.08, 0.85, ambient));
    vec3 color = mix(neutral, transmitted, Material.x);
    float direction = dot(normal, vec2(-0.56, 0.83));
    vec2 relative = local / Geometry.zw - Light.xy;
    float spot = exp(-dot(relative, relative) * 2.4) * Light.z;
    float fresnel = rim * rim * (0.15 + 0.85 * max(0.0, direction));
    color += vec3(0.92, 0.97, 1.0) * Optics.w * (fresnel * mix(0.11, 0.045, ambient) + spot * rim * 0.10);
    color *= 1.0 - Optics.w * rim * max(0.0, -direction) * (0.06 + ambient * 0.09);
    color = protectForeground(color);
    float alpha = coverage + shadow;
    fragColor = vec4(color * (coverage / max(alpha, 0.001)), alpha);
}
