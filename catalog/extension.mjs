// Author: dishanhai
import { createHash } from 'node:crypto';
import { readFileSync, realpathSync, statSync } from 'node:fs';
import { isAbsolute, join, relative } from 'node:path';
import { canonicalJson, isRelativeFile } from './build.mjs';
import { compareVersions, isResource, normalize, resolveMaterial, validateParameters } from './contract.mjs';
import { validateSchema } from './schema.mjs';

const digest = data => 'sha256:' + createHash('sha256').update(data).digest('hex');
const fileLimit = 8 * 1024 * 1024;
const bundleLimit = 32 * 1024 * 1024;

function bundleFile(root, path, limit) {
  if (!isRelativeFile(path)) throw new Error('Invalid extension path: ' + path);
  const file = realpathSync(join(root, path));
  const fromRoot = relative(root, file);
  if (isAbsolute(fromRoot) || fromRoot === '..' || fromRoot.startsWith('..\\') || fromRoot.startsWith('../')) {
    throw new Error('Extension file escapes the bundle: ' + path);
  }
  const stat = statSync(file);
  if (!stat.isFile() || stat.size > limit) throw new Error('Invalid extension file size: ' + path);
  return readFileSync(file);
}

function readBundle(catalog, directory) {
  const root = realpathSync(directory);
  const bundle = JSON.parse(bundleFile(root, 'extension.json', 1024 * 1024));
  validateSchema('extension.schema.json', bundle, 'extension.json');
  const owner = bundle.namespace;
  if (['.', '..', 'minecraft', 'sh_better_ui'].includes(owner)) throw new Error('Reserved extension namespace: ' + owner);
  if (compareVersions(catalog.catalogVersion, bundle.minCatalogVersion) < 0
      || compareVersions(catalog.catalogVersion, bundle.maxCatalogVersion) >= 0) {
    throw new Error('Incompatible catalog release for ' + owner);
  }
  let size = 0;
  for (const [path, expected] of Object.entries(bundle.files)) {
    const bytes = bundleFile(root, path, Math.min(fileLimit, bundleLimit - size));
    size += bytes.length;
    if (digest(bytes) !== expected) throw new Error('Checksum mismatch: ' + path);
  }
  return {
    owner,
    entries: bundle.materials.map(entry => {
      const owned = id => id.startsWith(owner + ':');
      if (!owned(entry.id) || entry.nativePreviewId && !owned(entry.nativePreviewId)
          || entry.aliases.map(normalize).some(alias => alias.includes(':') && !owned(alias))) {
        throw new Error('Foreign material identity in ' + entry.id);
      }
      if (entry.webRendererId !== null || entry.rendererVersion !== null || entry.previewScene !== null
          || !['native-only', 'web-unsupported'].includes(entry.webCompatibility)) {
        throw new Error('Unreviewed Web renderer: ' + entry.id);
      }
      const aliases = entry.aliases.map(normalize);
      if (aliases.includes('') || aliases.length !== new Set(aliases).size || entry.replacementId && !entry.deprecated) {
        throw new Error('Invalid aliases or replacement: ' + entry.id);
      }
      if (entry.sourceFiles.some(path => !isRelativeFile(path))
          || !entry.publicSymbols.includes(entry.publicEntrypoint)) {
        throw new Error('Invalid provenance: ' + entry.id);
      }
      for (const resource of entry.requiredResources) {
        const path = 'assets/' + resource.replace(':', '/');
        if (!isResource(resource) || !Object.hasOwn(bundle.files, path)) {
          throw new Error('Missing checksummed resource: ' + resource);
        }
      }
      const defaultValues = validateParameters(entry);
      if (entry.defaultValues && canonicalJson(entry.defaultValues) !== canonicalJson(defaultValues)) {
        throw new Error('Divergent extension parameter defaults: ' + entry.id);
      }
      return {
        ...entry,
        defaultValues,
        validationStatus: 'unverified',
        loaderCompatibility: Object.fromEntries(Object.entries(entry.loaderCompatibility)
          .map(([loader, status]) => [loader, status === 'unsupported' ? status : 'unverified'])),
      };
    }),
  };
}

/** Build-time only. Verifies local data packages without loading their executable contents. */
export function mergeExtensions(baseCatalog, bundleDirectories) {
  const { contentHash, ...base } = baseCatalog;
  const materials = [...base.materials];
  const owners = new Set();
  for (const directory of bundleDirectories) {
    const { owner, entries } = readBundle(base, directory);
    if (owners.has(owner)) throw new Error('Duplicate extension namespace: ' + owner);
    owners.add(owner);
    materials.push(...entries);
  }
  materials.sort((a, b) => a.id < b.id ? -1 : a.id > b.id ? 1 : 0);
  const data = { ...base, materials };
  const merged = { ...data, contentHash: digest(canonicalJson(data)) };
  for (const entry of materials) {
    if (!resolveMaterial(merged, entry.id)) throw new Error('Missing extension replacement: ' + entry.id);
  }
  return merged;
}
