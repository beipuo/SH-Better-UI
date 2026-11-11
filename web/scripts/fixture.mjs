// Author: dishanhai
import { readFile, writeFile } from 'node:fs/promises';
import { deflateSync, inflateSync } from 'node:zlib';

const fixtureDirectory = new URL('../../catalog/fixtures/', import.meta.url);
const scene = JSON.parse(await readFile(new URL('liquid-glass.json', fixtureDirectory), 'utf8'));
const target = new URL(scene.texture, fixtureDirectory);
const { width, height } = scene;
const stride = width * 4 + 1;
const raster = Buffer.alloc(stride * height);
let seed = scene.seed >>> 0;

function fill(x, y, w, h, color, alpha = 255) {
  const rgb = [color >> 16 & 255, color >> 8 & 255, color & 255];
  for (let row = Math.max(0, y); row < Math.min(height, y + h); row++) {
    for (let col = Math.max(0, x); col < Math.min(width, x + w); col++) {
      const offset = row * stride + 1 + col * 4;
      for (let component = 0; component < 3; component++) {
        raster[offset + component] = Math.round((rgb[component] * alpha + raster[offset + component] * (255 - alpha)) / 255);
      }
      raster[offset + 3] = 255;
    }
  }
}

function random(limit) {
  seed ^= seed << 13;
  seed ^= seed >>> 17;
  seed ^= seed << 5;
  return (seed >>> 0) % limit;
}

const glyphs = {
  A: [14, 17, 17, 31, 17, 17, 17], B: [30, 17, 17, 30, 17, 17, 30],
  C: [14, 17, 16, 16, 16, 17, 14],
  D: [30, 17, 17, 17, 17, 17, 30], E: [31, 16, 16, 30, 16, 16, 31],
  F: [31, 16, 16, 30, 16, 16, 16], G: [14, 17, 16, 23, 17, 17, 15],
  H: [17, 17, 17, 31, 17, 17, 17], I: [31, 4, 4, 4, 4, 4, 31],
  K: [17, 18, 20, 24, 20, 18, 17], L: [16, 16, 16, 16, 16, 16, 31],
  N: [17, 25, 25, 21, 19, 19, 17], O: [14, 17, 17, 17, 17, 17, 14],
  P: [30, 17, 17, 30, 16, 16, 16], R: [30, 17, 17, 30, 20, 18, 17],
  S: [15, 16, 16, 14, 1, 1, 30], T: [31, 4, 4, 4, 4, 4, 4],
  X: [17, 17, 10, 4, 10, 17, 17],
  0: [14, 17, 19, 21, 25, 17, 14], 1: [4, 12, 4, 4, 4, 4, 14],
  2: [14, 17, 1, 2, 4, 8, 31], ' ': [0, 0, 0, 0, 0, 0, 0],
};

function label(text, x, y, scale, color) {
  for (const character of text) {
    glyphs[character].forEach((bits, row) => {
      for (let col = 0; col < 5; col++) {
        if (bits & (1 << (4 - col))) fill(x + col * scale, y + row * scale, scale, scale, color);
      }
    });
    x += 6 * scale;
  }
}

// Calibration atlas: broad luminance differences, sharp edges, fine detail and layered shapes.
for (let x = 0; x < width; x++) {
  const left = x < width / 2;
  const t = (x % (width / 2)) / (width / 2);
  const rgb = left ? [19 + 25 * t, 39 + 40 * t, 73 + 36 * t] : [251 - 35 * t, 233 - 74 * t, 174 - 79 * t];
  fill(x, 0, 1, height, Math.round(rgb[0]) << 16 | Math.round(rgb[1]) << 8 | Math.round(rgb[2]));
}
for (let row = 0; row < 7; row++) {
  for (let col = 0; col < 8; col++) fill(40 + col * 30, 72 + row * 30, 30, 30, (row + col) % 2 ? 0x225a76 : 0x10283d);
}
for (let x = 344; x < 594; x += 6) fill(x, 78, 2, 199, 0x5b3a39, 150);
for (let y = 85; y < 279; y += 17) fill(336, y, 288, 1, 0xfffff0, 170);
fill(306, 91, 209, 124, 0x74cef3, 82);
fill(244, 153, 182, 83, 0xf55a43, 150);
fill(420, 181, 187, 87, 0xfff3dc, 178);
fill(530, 113, 89, 35, 0x203b53);
fill(45, 108, 154, 42, 0x0e2235, 229);
label('DARK', 57, 120, 3, 0xe7faf9);
label('LIGHT', 454, 206, 3, 0x32232d);
label('01', 106, 192, 8, 0xe6f8e9);
label('02', 332, 100, 8, 0x452e2e);
label('OPTICS', 40, 34, 3, 0xb3f0ee);
label('CALIBRATION', 420, 38, 2, 0x452e2e);
fill(40, 58, 564, 1, 0xe0e6d8, 135);
fill(0, 299, width, 85, 0x161e29, 210);

// Blocky inventory strip, independent of fonts or external game assets.
label('PIXEL GRID', 42, 329, 2, 0xe4d5b0);
for (let slot = 0; slot < 9; slot++) {
  const x = 272 + slot * 35;
  fill(x, 318, 32, 32, slot === 3 ? 0xf5d687 : 0x788182);
  fill(x + 2, 320, 28, 28, 0x202b32);
  if (slot === 8) continue;
  const colors = [0x2a4c40, 0x47634b, 0x737351, 0x9d8f59, 0xcea06e, 0x445866];
  for (let row = 0; row < 5; row++) {
    for (let col = 0; col < 5; col++) fill(x + 6 + col * 4, 324 + row * 4, 4, 4, colors[random(colors.length)]);
  }
}

function chunk(name, data) {
  const tag = Buffer.from(name);
  const result = Buffer.alloc(12 + data.length);
  result.writeUInt32BE(data.length, 0);
  tag.copy(result, 4);
  data.copy(result, 8);
  let crc = 0xffffffff;
  for (const byte of result.subarray(4, -4)) {
    crc ^= byte;
    for (let bit = 0; bit < 8; bit++) crc = (crc >>> 1) ^ (-(crc & 1) & 0xedb88320);
  }
  result.writeUInt32BE((crc ^ 0xffffffff) >>> 0, result.length - 4);
  return result;
}

const signature = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
const header = Buffer.alloc(13);
header.writeUInt32BE(width, 0);
header.writeUInt32BE(height, 4);
header.set([8, 6], 8);

if (process.argv.includes('--check')) {
  const png = await readFile(target);
  if (!png.subarray(0, 8).equals(signature) || !png.subarray(16, 29).equals(header)) {
    throw new Error('Unexpected fixture PNG format');
  }
  const compressed = [];
  for (let offset = 8; offset < png.length;) {
    const length = png.readUInt32BE(offset);
    if (png.toString('ascii', offset + 4, offset + 8) === 'IDAT') compressed.push(png.subarray(offset + 8, offset + 8 + length));
    offset += 12 + length;
  }
  if (!inflateSync(Buffer.concat(compressed)).equals(raster)) throw new Error('Fixture pixels changed. Run npm run fixture.');
} else {
  await writeFile(target, Buffer.concat([
    signature, chunk('IHDR', header), chunk('sRGB', Buffer.from([0])),
    chunk('IDAT', deflateSync(raster, { level: 9 })), chunk('IEND', Buffer.alloc(0)),
  ]));
}
