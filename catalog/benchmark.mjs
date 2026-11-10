// Author: dishanhai
import { readFileSync } from 'node:fs';
import { performance } from 'node:perf_hooks';
import { resolveMaterial, searchMaterials } from './contract.mjs';

const catalogFile = readFileSync(new URL('./generated/catalog.json', import.meta.url));
const text = catalogFile.toString('utf8');
const catalog = JSON.parse(text);
const queries = ['sh_better_ui:liquid_glass', 'clear_glass', 'pattern waves', '阴影', '透明背景折射高光', 'missing'];

function measure(samples, batchSize, prepare, run) {
  const times = [];
  const cpu = process.cpuUsage();
  for (let i = 0; i < samples; i++) {
    const input = prepare();
    const before = performance.now();
    for (let j = 0; j < batchSize; j++) run(input, j);
    times.push((performance.now() - before) / batchSize);
  }
  times.sort((a, b) => a - b);
  const { user, system } = process.cpuUsage(cpu);
  return { samples, batchSize, medianMs: times[Math.floor(samples / 2)],
    p95Ms: times[Math.ceil(samples * 0.95) - 1], processCpuMs: (user + system) / 1000 };
}

for (let i = 0; i < 2000; i++) {
  resolveMaterial(catalog, queries[i % queries.length]);
  searchMaterials(catalog, queries[i % queries.length]);
}
console.log(JSON.stringify({
  runtime: process.version,
  platform: process.platform,
  entries: catalog.materials.length,
  catalogBytes: catalogFile.length,
  parse: measure(30, 5, () => text, value => JSON.parse(value)),
  index: measure(30, 1, () => JSON.parse(text), value => resolveMaterial(value, queries[0])),
  lookup: measure(30, 1000, () => catalog, (value, i) => resolveMaterial(value, queries[i % queries.length])),
  search: measure(30, 40, () => catalog, (value, i) => searchMaterials(value, queries[i % queries.length])),
}, null, 2));
