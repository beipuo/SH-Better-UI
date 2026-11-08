#version 150

// 山海：沿斜向渐层循环色相，保留原图的透明度。
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float IconPhase;

in vec2 iconUv;
out vec4 fragColor;

void main() {
    vec4 pixel = texture(Sampler0, iconUv) * ColorModulator;
    if (pixel.a <= 0.0) discard;

    float hue = IconPhase + dot(iconUv, vec2(0.34, 0.22));
    vec3 channels = 0.5 + 0.5 * cos(6.2831853 * (hue + vec3(0.0, 0.6666667, 0.3333333)));
    fragColor = vec4(pixel.rgb * mix(vec3(0.42), vec3(1.0), channels), pixel.a);
}
