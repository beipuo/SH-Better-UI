import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { test } from 'node:test';

test('ES adaptation preserves the native shader body and starts with its version directive', async () => {
  const { adaptShader } = await import('../scripts/shader-adapter.mjs');
  const shaders = new URL('../../src/main/resources/assets/sh_better_ui/shaders/', import.meta.url);
  for (const name of ['managed_glass.vsh', 'managed_glass.fsh']) {
    const source = (await readFile(new URL(name, shaders), 'utf8')).replaceAll('\r\n', '\n');
    const adapted = adaptShader(source);
    const header = '#version 300 es\nprecision highp float;\nprecision highp int;\n';
    assert.ok(adapted.startsWith(header));
    assert.equal(adapted.slice(header.length), source.replace('#version 150\n', ''));
  }
  assert.throws(() => adaptShader('#version 330\nvoid main() {}'), /GLSL 150/);
});

test('clock pauses, steps by one simulation frame, and ignores hidden elapsed time', async () => {
  const { PreviewClock } = await import('../src/preview-clock.mjs');
  const clock = new PreviewClock(2400, 60);
  clock.resume();
  clock.tick(10);
  assert.equal(clock.tick(60), 50);
  clock.pause();
  assert.equal(clock.tick(5000), 50);
  assert.ok(Math.abs(clock.stepFrame() - (50 + 1000 / 60)) < 1e-10);
  assert.equal(clock.seek(3100), 2400);
  clock.resetTime();
  clock.setPlaybackSpeed(2);
  clock.resume();
  clock.tick(200);
  assert.equal(clock.tick(250), 100);
  clock.suspend();
  assert.equal(clock.tick(900), 100);
});

test('replay maps normalized down-positive pointer input to local light coordinates', async () => {
  const { replayFrame } = await import('../src/preview-clock.mjs');
  const fixture = JSON.parse(await readFile(new URL('../../catalog/fixtures/liquid-glass.json', import.meta.url)));
  const frame = replayFrame(fixture, 400);
  assert.equal(frame.light.y, -1);
  assert.equal(frame.light.strength, 0.75);
  assert.equal(replayFrame(fixture, 2400).light.strength, 0);
  const { x, y, width, height } = fixture.surface;
  const input = { x: (x + width / 2) / fixture.width, y: (y + height * 0.75) / fixture.height };
  assert.equal(replayFrame(fixture, 0, input).light.y, 0.5);
  assert.deepEqual(replayFrame(fixture, 800), replayFrame(fixture, 800));
});

test('share fragments round-trip unicode and reject modified or oversized payloads', async () => {
  const { decodeShare, encodeShare } = await import('../src/share.mjs');
  const text = '{"materialId":"示例:材质","parameters":{}}';
  const fragment = encodeShare(text);
  assert.equal(decodeShare(fragment), text);
  assert.throws(() => decodeShare(fragment.replace(/\.[0-9a-f]{8}$/, '.00000000')), /校验/);
  assert.throws(() => decodeShare('#umivp1.' + 'A'.repeat(10000)), /长度/);
  assert.throws(() => encodeShare('a'.repeat(10000)), /大小/);
});

test('pixel metrics retain the worst RGB and alpha channel errors', async () => {
  const { pixelMetrics } = await import('./pixel-metrics.mjs');
  const a = Uint8Array.of(11, 20, 30, 128, 35, 41, 82, 255);
  const b = Uint8Array.of(12, 18, 30, 120, 35, 45, 82, 255);
  const result = pixelMetrics(a, b, 2, 1);
  assert.equal(result.maxChannelError, 4);
  assert.equal(result.maxAlphaError, 8);
});
