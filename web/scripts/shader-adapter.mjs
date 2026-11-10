// Author: dishanhai
export function adaptShader(source) {
  const normalized = source.replaceAll('\r\n', '\n');
  if (!/^(?:\/\/[^\n]*\n)*#version 150\n/.test(normalized)) {
    throw new Error('Expected the native GLSL 150 header');
  }
  return '#version 300 es\nprecision highp float;\nprecision highp int;\n'
    + normalized.replace(/^#version 150\n/m, '');
}
