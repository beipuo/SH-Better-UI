#version 150

// 山海：平滑调节图标亮度，呼吸时不改变透明度。
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float IconPhase;

in vec2 iconUv;
out vec4 fragColor;

void main() {
    vec4 pixel = texture(Sampler0, iconUv) * ColorModulator;
    if (pixel.a <= 0.0) discard;

    float pulse = 0.5 - 0.5 * cos(6.2831853 * IconPhase);
    fragColor = vec4(pixel.rgb * mix(0.58, 1.0, pulse), pixel.a);
}
