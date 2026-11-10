// Author: dishanhai
import { readFile } from 'node:fs/promises';
import { chromium, eventually } from './chromium.mjs';
import { serve } from '../scripts/serve.mjs';

const files = process.argv.slice(2);
if (files.length !== 2) throw new Error('Usage: node web/tests/compare.mjs <first.png> <second.png>');
const paths = ['/__compare__/a.png', '/__compare__/b.png'];
const assets = new Map([
  ['/__compare__/index.html', { data: '<meta charset="utf-8"><title>RGBA8 image comparison</title>' }],
  ['/__compare__/pixel-metrics.mjs', { data: await readFile(new URL('./pixel-metrics.mjs', import.meta.url)) }],
]);
for (const [index, file] of files.entries()) assets.set(paths[index], { data: await readFile(file) });
const server = await serve({ testAssets: assets });
let browser;
try {
  browser = await chromium();
  await browser.command('Page.navigate', { url: server.url + '__compare__/index.html' });
  await eventually(() => browser.evaluate("document.title === 'RGBA8 image comparison'").catch(() => false), 'comparison document');
  const results = await browser.call(async paths => {
    const { pixelMetrics } = await import('./pixel-metrics.mjs');
    const frames = [];
    for (const path of paths) {
      const bitmap = await createImageBitmap(await (await fetch(path)).blob(), { colorSpaceConversion: 'none', premultiplyAlpha: 'none' });
      try {
        const canvas = new OffscreenCanvas(bitmap.width, bitmap.height);
        const context = canvas.getContext('2d', { colorSpace: 'srgb', willReadFrequently: true });
        context.globalCompositeOperation = 'copy';
        context.drawImage(bitmap, 0, 0);
        frames.push({ width: bitmap.width, height: bitmap.height, bytes: context.getImageData(0, 0, bitmap.width, bitmap.height).data });
      } finally {
        bitmap.close();
      }
    }
    const [a, b] = frames;
    if (a.width !== b.width || a.height !== b.height) throw new Error(`Dimensions differ: ${a.width}×${a.height} vs ${b.width}×${b.height}`);
    return { width: a.width, height: a.height, ...pixelMetrics(a.bytes, b.bytes, a.width, a.height) };
  }, paths);
  console.log(JSON.stringify({ images: files, ...results }, null, 2));
} finally {
  await browser?.close();
  await server.close();
}
