#version 150

// 山海：弧形亮带沿图标斜向连续流动。
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float IconPhase;

in vec2 iconUv;
out vec4 fragColor;

void main() {
    vec4 pixel = texture(Sampler0, iconUv) * ColorModulator;
    if (pixel.a <= 0.0) discard;

    float coordinate = dot(iconUv, vec2(0.70, 0.46)) + 0.12 * sin(6.2831853 * iconUv.y);
    float wave = pow(0.5 + 0.5 * cos(6.2831853 * (coordinate - IconPhase)), 10.0);
    vec3 base = mix(vec3(0.32, 0.54, 0.84), vec3(0.28, 0.85, 0.72), iconUv.y);
    fragColor = vec4(pixel.rgb * mix(base, vec3(1.0), wave), pixel.a);
}
