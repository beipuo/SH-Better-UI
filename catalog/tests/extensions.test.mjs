// Author: dishanhai
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { assembleCatalog } from '../build.mjs';
import { mergeExtensions } from '../extension.mjs';

test('build-time extensions verify namespace, checksums and the renderer trust boundary', () => {
  const base = assembleCatalog();
  const folder = mkdtempSync(join(tmpdir(), 'shb-material-extension-'));
  try {
    const resource = 'assets/example/shaders/example.fsh';
    mkdirSync(join(folder, 'assets/example/shaders'), { recursive: true });
    const shader = '// example fixture\n';
    writeFileSync(join(folder, resource), shader);
    const entry = {
      ...base.materials.find(material => material.id === 'sh_better_ui:liquid_glass'),
      id: 'example:glass', aliases: ['example_glass'], requiredResources: ['example:shaders/example.fsh'],
      nativePreviewId: 'example:glass', webRendererId: null, rendererVersion: null, previewScene: null,
      webCompatibility: 'native-only', validationStatus: 'client-verified',
    };
    const pack = {
      schemaVersion: '1.0.0', namespace: 'example', minCatalogVersion: '1.0.0', maxCatalogVersion: '2.0.0',
      materials: [entry],
      files: { [resource]: 'sha256:' + createHash('sha256').update(shader).digest('hex') },
    };
    const save = () => writeFileSync(join(folder, 'extension.json'), JSON.stringify(pack));
    save();
    const merged = mergeExtensions(base, [folder]);
    assert.notEqual(merged.contentHash, base.contentHash);
    assert.equal(merged.materials.length, base.materials.length + 1);
    assert.equal(merged.materials.find(material => material.id === entry.id).validationStatus, 'unverified');
    assert.throws(() => mergeExtensions(base, [folder, folder]), /Duplicate extension namespace/);
    entry.webRendererId = 'arbitrary-code';
    save();
    assert.throws(() => mergeExtensions(base, [folder]), /Unreviewed Web renderer/);
    entry.webRendererId = null;
    pack.files['../outside.txt'] = pack.files[resource];
    save();
    assert.throws(() => mergeExtensions(base, [folder]), /Invalid extension path/);
    delete pack.files['../outside.txt'];
    entry.aliases = ['liquid_glass'];
    save();
    assert.throws(() => mergeExtensions(base, [folder]), /Conflicting alias/);
    writeFileSync(join(folder, resource), 'changed');
    assert.throws(() => mergeExtensions(base, [folder]), /Checksum mismatch/);
  } finally {
    rmSync(folder, { recursive: true });
  }
});
