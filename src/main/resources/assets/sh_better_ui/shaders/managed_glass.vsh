// Author: dishanhai
#version 150

uniform vec4 Viewport;
uniform vec4 Quad;

void main() {
    vec2 corner = vec2(float(gl_VertexID & 1), float((gl_VertexID >> 1) & 1));
    vec2 pixel = mix(Quad.xy, Quad.zw, corner);
    gl_Position = vec4((pixel - Viewport.xy) * 2.0 / Viewport.zw - 1.0, 0.0, 1.0);
}
