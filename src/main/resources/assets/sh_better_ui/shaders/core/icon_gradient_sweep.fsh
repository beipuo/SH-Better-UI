#version 150

// 山海：渐层扫光沿用原图与宿主的颜色、透明度。
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float IconPhase;

in vec2 iconUv;
out vec4 fragColor;

void main() {
    vec4 pixel = texture(Sampler0, iconUv) * ColorModulator;
    if (pixel.a <= 0.0) discard;

    float diagonal = dot(iconUv, vec2(0.68, 0.32));
    float sweep = 1.0 - smoothstep(0.0, 0.16, abs(diagonal - mix(-0.18, 1.18, IconPhase)));
    vec3 gradient = mix(vec3(0.48, 0.74, 1.0), vec3(0.76, 1.0, 0.92), iconUv.y);
    fragColor = vec4(pixel.rgb * mix(gradient, vec3(1.0), sweep * 0.92), pixel.a);
}
