// Author: dishanhai
// Shared by the offline catalog, static browser app and build tools.
export const SCHEMA_VERSION = '1.0.0';
export const PRESET_LIMIT = 64 * 1024;
export const PARAMETER_TYPES = new Set([
  'boolean', 'integer', 'float', 'enum', 'color', 'vector2', 'vector3',
  'vector4', 'gradient', 'texture', 'duration', 'curve', 'object',
]);
const idPattern = /^[a-z0-9_.-]+:[a-z0-9_./-]+$/;
const keyPattern = /^[a-zA-Z][a-zA-Z0-9_]{0,63}$/;
const versionPattern = /^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$/;
const colorPattern = /^#[a-fA-F0-9]{6}(?:[a-fA-F0-9]{2})?$/;
const reserved = new Set(['__proto__', 'constructor', 'prototype']);
const catalogIndexes = new WeakMap();

export const normalize = (value) => value.normalize('NFKC').trim().toLowerCase();
export const isId = (value) => typeof value === 'string' && idPattern.test(value);
export const isParameterKey = (value) => keyPattern.test(value) && !reserved.has(value);
export const isRecord = (value) => value !== null && typeof value === 'object' && !Array.isArray(value);
export const isResource = (value) => isId(value) && value.split(/[:/]/).every(s => s && s !== '.' && s !== '..');
const ordinal = (a, b) => a < b ? -1 : a > b ? 1 : 0;

export function parseVersion(value) {
  if (typeof value !== 'string' || !versionPattern.test(value)) throw new Error('Invalid version: ' + value);
  const numbers = value.split('.').map(Number);
  if (numbers.some(n => n > 2147483647)) throw new Error('Version component outside supported range: ' + value);
  return numbers;
}

export function compareVersions(a, b) {
  const left = parseVersion(a), right = parseVersion(b);
  return left[0] - right[0] || left[1] - right[1] || left[2] - right[2];
}

function boundedNumber(value, parameter, integer = false) {
  return typeof value === 'number' && Number.isFinite(value) && (!integer || Number.isSafeInteger(value))
    && (parameter.minimum === null || value >= parameter.minimum)
    && (parameter.maximum === null || value <= parameter.maximum);
}

function safeJson(value, depth = 0) {
  if (depth > 16) throw new Error('Parameter nesting exceeds 16 levels');
  if (value === null || typeof value === 'string' || typeof value === 'boolean' || Number.isFinite(value)) return value;
  if (Array.isArray(value)) return value.map(item => safeJson(item, depth + 1));
  if (!isRecord(value)) throw new Error('Invalid JSON parameter value');
  return Object.fromEntries(Object.entries(value).map(([key, item]) => {
    if (reserved.has(key)) throw new Error('Reserved object key: ' + key);
    return [key, safeJson(item, depth + 1)];
  }));
}

function orderedPoints(value, coordinate, component, validComponent) {
  return Array.isArray(value) && value.length >= 2 && value.length <= 256 && value.every((point, index) =>
    isRecord(point) && Object.keys(point).length === 2 && Number.isFinite(point[coordinate])
    && point[coordinate] >= 0 && point[coordinate] <= 1 && (!index || value[index - 1][coordinate] < point[coordinate])
    && validComponent(point[component]));
}

export function validateValue(name, spec, value, resources) {
  let valid;
  switch (spec.type) {
    case 'boolean': valid = typeof value === 'boolean'; break;
    case 'integer':
    case 'duration': valid = boundedNumber(value, spec, true); break;
    case 'float': valid = boundedNumber(value, spec); break;
    case 'enum': valid = spec.choices.includes(value); break;
    case 'color': valid = typeof value === 'string' && colorPattern.test(value); break;
    case 'vector2':
    case 'vector3':
    case 'vector4':
      valid = Array.isArray(value) && value.length === Number(spec.type.at(-1))
        && value.every(item => boundedNumber(item, spec));
      break;
    case 'gradient':
      valid = (Array.isArray(value) && (value.length > 0 || spec.defaultValue.length === 0) && value.length <= 256
        && value.every(item => typeof item === 'string' && colorPattern.test(item)))
        || orderedPoints(value, 'offset', 'color', item => typeof item === 'string' && colorPattern.test(item));
      break;
    case 'texture': valid = isResource(value) && resources.includes(value); break;
    case 'curve': valid = orderedPoints(value, 'time', 'value', item => boundedNumber(item, spec)); break;
    case 'object': valid = isRecord(value); break;
    default: valid = false;
  }
  if (!valid) throw new Error('Invalid parameter: ' + name);
  return safeJson(value);
}

function satisfies(op, left, right) {
  if (op === 'eq' || op === 'neq') {
    if (!['string', 'number', 'boolean'].includes(typeof left) || typeof left !== typeof right) return false;
    return op === 'eq' ? left === right : left !== right;
  }
  if (!Number.isFinite(left) || !Number.isFinite(right)) return false;
  switch (op) {
    case 'lt': return left < right;
    case 'lte': return left <= right;
    case 'gt': return left > right;
    case 'gte': return left >= right;
    default: return false;
  }
}

function sameJson(left, right) {
  if (left === right) return true;
  if (Array.isArray(left) && Array.isArray(right)) {
    return left.length === right.length && left.every((item, index) => sameJson(item, right[index]));
  }
  if (!isRecord(left) || !isRecord(right)) return false;
  const keys = Object.keys(left);
  return keys.length === Object.keys(right).length
    && keys.every(key => Object.hasOwn(right, key) && sameJson(left[key], right[key]));
}

export function validateParameters(entry, values = {}) {
  if (!isRecord(values)) throw new Error('Expected a parameters object');
  for (const key of Object.keys(values)) {
    if (!Object.hasOwn(entry.parameters, key)) throw new Error('Unknown parameter: ' + key);
  }
  const result = Object.fromEntries(Object.entries(entry.parameters).map(([name, spec]) => {
    const value = Object.hasOwn(values, name) ? values[name] : spec.defaultValue;
    const checked = validateValue(name, spec, value, entry.requiredResources);
    if (!spec.editable && !sameJson(checked, spec.defaultValue)) throw new Error('Read-only parameter: ' + name);
    return [name, checked];
  }));
  for (const [name, spec] of Object.entries(entry.parameters)) {
    for (const constraint of spec.constraints) {
      const right = constraint.parameter ? result[constraint.parameter] : constraint.value;
      if (!satisfies(constraint.op, result[name], right)) throw new Error('Constraint failed: ' + name + ' ' + constraint.op);
    }
  }
  return result;
}

function index(catalog) {
  const cached = catalogIndexes.get(catalog);
  if (cached) return cached;
  const ids = new Map();
  const aliases = new Map();
  for (const entry of catalog.materials) {
    if (ids.has(entry.id)) throw new Error('Duplicate material ID: ' + entry.id);
    ids.set(entry.id, entry);
  }
  for (const entry of catalog.materials) {
    for (const alias of entry.aliases) {
      const key = normalize(alias);
      const existing = ids.get(key) ?? aliases.get(key);
      if (!key || existing && existing !== entry) throw new Error('Conflicting alias: ' + alias);
      aliases.set(key, entry);
    }
  }
  const documents = catalog.materials.map(entry => ({
    entry,
    fields: [
      ['name', [entry.name, ...Object.values(entry.localizedNames)], 1000],
      ['api', entry.publicSymbols.flatMap(symbol => [symbol, symbol.split(/[.#]/).at(-1)]), 400],
      ['tag', [entry.category, ...entry.tags, ...entry.requiredCapabilities, ...Object.keys(entry.parameters)], 90],
      ['visual', [entry.description, ...entry.visualCharacteristics, ...entry.useCases], 35],
    ].map(([kind, values, weight]) => [kind, values.map(value => [value, normalize(value)]), weight]),
  }));
  const value = { ids, aliases, documents };
  catalogIndexes.set(catalog, value);
  return value;
}

export function resolveMaterial(catalog, id) {
  const { ids, aliases } = index(catalog);
  const key = normalize(id);
  let entry = ids.get(key) ?? aliases.get(key);
  const visited = new Set();
  while (entry?.deprecated && entry.replacementId) {
    if (visited.has(entry.id)) throw new Error('Cyclic replacement: ' + entry.id);
    visited.add(entry.id);
    entry = ids.get(entry.replacementId);
  }
  return entry;
}

// Phrases are generic visual/usage facets rather than aliases for invented materials.
const facets = [
  [/折射|refraction|refract/u, 'refraction'],
  [/模糊|blur|frosted/u, 'blur'],
  [/透明|透射|transparent|translucent|transmission/u, 'transparent'],
  [/高光|高亮|highlight|rim light/u, 'edge highlight'],
  [/动态|動態|动画|動畫|animated|motion|dynamic/u, 'dynamic'],
  [/光线|光線|光束|lighting|rays|beam/u, 'lighting'],
  [/背景|background|backdrop/u, 'background'],
  [/玻璃|glass/u, 'glass'],
  [/颗粒|粒子|particles/u, 'particle'],
  [/阴影|陰影|shadow/u, 'shadow'],
  [/遮罩|mask/u, 'mask'],
];

function supportsFilter(entry, filters) {
  if (filters.category && entry.category !== filters.category) return false;
  if (filters.tags?.some(tag => !entry.tags.includes(tag))) return false;
  if (filters.capability && !entry.requiredCapabilities.includes(filters.capability)) return false;
  if (filters.dynamic && !entry.tags.includes('dynamic')) return false;
  if (filters.webOnly && !['web-approximation', 'web-verified'].includes(entry.webCompatibility)) return false;
  return !filters.loader || ['source-verified', 'client-verified'].includes(entry.loaderCompatibility[filters.loader]);
}

export function searchMaterials(catalog, query = '', filters = {}) {
  const text = normalize(query).slice(0, 1024);
  const { ids, aliases, documents } = index(catalog);
  const exactId = ids.get(text);
  const exactAlias = aliases.get(text);
  const tokens = [...new Set(text.match(/[a-z0-9][a-z0-9_.:-]{1,}|\p{Script=Han}{2,}/gu) ?? [])].slice(0, 40);
  const requested = facets.filter(([pattern]) => pattern.test(text)).map(([, label]) => label);
  const results = [];
  for (const { entry, fields } of documents) {
    if (!supportsFilter(entry, filters) || entry.deprecated && entry !== exactId && entry !== exactAlias) continue;
    const reasons = [];
    const exact = entry === exactId ? 'id' : entry === exactAlias ? 'alias' : null;
    let score = exact === 'id' ? 100000 : exact === 'alias' ? 50000 : 0;
    if (exact) reasons.push({ kind: exact, value: text });
    const searchable = fields.flatMap(([, values]) => values.map(([, normalized]) => normalized));
    const missing = requested.filter(facet => !searchable.some(value => value.includes(facet)));
    score += (requested.length - missing.length) * 1400;
    if (text) for (const [kind, values, weight] of fields) {
      const hits = values.filter(([, normalized]) => {
        return normalized.includes(text) || normalized.length >= 2 && text.includes(normalized)
          || tokens.some(token => normalized.includes(token))
          || requested.some(facet => normalized.includes(facet));
      });
      if (hits.length) {
        score += weight + Math.min(hits.length, 10);
        if (kind === 'name' || kind === 'api') {
          const exact = hits.some(([, normalized]) => normalized === text);
          const phrase = hits.some(([, normalized]) => normalized.length >= 3 && text.includes(normalized));
          score += exact ? (kind === 'name' ? 24000 : 19000) : phrase && kind === 'name' ? 2400 : 0;
        }
        reasons.push({ kind, value: hits.slice(0, 3).map(([value]) => value).join(' · ') });
      }
    }
    results.push({ entry, score, reasons, missing });
  }
  results.sort((a, b) => b.score - a.score || ordinal(a.entry.id, b.entry.id));
  if (!text) return results;
  const matched = results.filter(result => result.score > 0);
  return matched.length ? matched : results.slice(0, 3).map(result => ({
    ...result, reasons: [{ kind: 'fallback', value: text }], missing: result.missing.length ? result.missing : [text],
  }));
}

export function createPreset(catalog, materialId, values = {}) {
  const entry = resolveMaterial(catalog, materialId);
  if (!entry || entry.deprecated) throw new Error('Unknown or retired material: ' + materialId);
  const preset = {
    schemaVersion: catalog.presetSchemaVersion,
    catalogVersion: catalog.catalogVersion,
    materialId: entry.id,
    materialRevision: entry.materialRevision,
    parameters: validateParameters(entry, values),
  };
  checkPresetSize(JSON.stringify(preset));
  return preset;
}

function checkPresetSize(text) {
  if (typeof text !== 'string' || text.length > PRESET_LIMIT || new TextEncoder().encode(text).length > PRESET_LIMIT) {
    throw new Error('Preset exceeds 64 KiB');
  }
}

export function importPreset(catalog, text) {
  checkPresetSize(text);
  const raw = JSON.parse(text);
  if (!isRecord(raw) || Object.keys(raw).length !== 5
      || !['schemaVersion', 'catalogVersion', 'materialId', 'materialRevision', 'parameters'].every(k => Object.hasOwn(raw, k))
      || typeof raw.materialId !== 'string' || !isRecord(raw.parameters)) throw new Error('Invalid preset document');
  const entry = resolveMaterial(catalog, raw.materialId);
  if (!entry || entry.deprecated) throw new Error('Unknown or retired material: ' + raw.materialId);
  let schema = raw.schemaVersion, revision = raw.materialRevision;
  parseVersion(schema);
  parseVersion(revision);
  let values = safeJson(raw.parameters);
  const visited = new Set();
  while (schema !== catalog.presetSchemaVersion || revision !== entry.materialRevision) {
    const key = schema + '/' + revision;
    if (visited.has(key)) throw new Error('Cyclic preset migration');
    visited.add(key);
    const step = catalog.migrations.find(m => m.materialId === entry.id && m.fromRevision === revision && m.fromSchemaVersion === schema);
    if (!step) throw new Error('Incompatible preset schema or material revision: ' + key);
    values = Object.fromEntries(Object.entries(values).map(([name, value]) => {
      const next = Object.hasOwn(step.rename, name) ? step.rename[name] : name;
      if (next !== name && Object.hasOwn(values, next)) throw new Error('Conflicting migrated parameter: ' + next);
      return [next, value];
    }));
    schema = step.toSchemaVersion;
    revision = step.toRevision;
  }
  if (compareVersions(raw.catalogVersion, catalog.catalogVersion) > 0
      || !visited.size && parseVersion(raw.catalogVersion)[0] !== parseVersion(catalog.catalogVersion)[0]) {
    throw new Error('Incompatible catalog release: ' + raw.catalogVersion);
  }
  return createPreset(catalog, entry.id, values);
}
