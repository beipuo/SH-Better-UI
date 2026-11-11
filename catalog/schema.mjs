// Author: dishanhai
// Evaluates the vocabulary used by the checked-in Draft 2020-12 schemas.
import { readFileSync } from 'node:fs';
import { isRecord } from './contract.mjs';

const schemaDir = new URL('./schema/', import.meta.url);
const documents = new Map();
const supported = new Set([
  '$schema', '$id', '$defs', '$ref', 'type', 'const', 'enum', 'anyOf', 'oneOf',
  'minLength', 'pattern', 'exclusiveMinimum', 'minItems', 'uniqueItems', 'items',
  'required', 'properties', 'additionalProperties', 'propertyNames',
]);

function load(file) {
  if (!/^[a-z]+\.schema\.json$/.test(file)) throw new Error('Unexpected schema reference: ' + file);
  if (!documents.has(file)) documents.set(file, JSON.parse(readFileSync(new URL(file, schemaDir), 'utf8')));
  return documents.get(file);
}

function typeIs(value, type) {
  if (type === 'null') return value === null;
  if (type === 'object') return isRecord(value);
  if (type === 'array') return Array.isArray(value);
  if (type === 'integer') return Number.isSafeInteger(value);
  if (type === 'number') return Number.isFinite(value);
  return typeof value === type;
}

function check(value, schema, file, path) {
  const fail = message => { throw new Error(path + ' ' + message); };
  for (const keyword of Object.keys(schema)) {
    if (!supported.has(keyword)) throw new Error('Unsupported schema vocabulary: ' + keyword);
  }
  if (schema.$ref) {
    const [external, pointer] = schema.$ref.split('#');
    const source = external || file;
    const root = load(source);
    const target = pointer ? pointer.slice(1).split('/').reduce((node, key) => node[key], root) : root;
    check(value, target, source, path);
  }
  if (schema.type && ![schema.type].flat().some(type => typeIs(value, type))) fail('has the wrong type');
  if (Object.hasOwn(schema, 'const') && value !== schema.const) fail('must equal ' + schema.const);
  if (schema.enum && !schema.enum.includes(value)) fail('must be one of ' + schema.enum.join(', '));
  for (const keyword of ['anyOf', 'oneOf']) {
    if (!schema[keyword]) continue;
    const matches = schema[keyword].filter(option => {
      try { check(value, option, file, path); return true; } catch { return false; }
    }).length;
    if (keyword === 'oneOf' ? matches !== 1 : !matches) fail('does not satisfy ' + keyword);
  }
  if (typeof value === 'string') {
    if (value.length < schema.minLength) fail('is too short');
    if (schema.pattern && !new RegExp(schema.pattern, 'u').test(value)) fail('does not match ' + schema.pattern);
  }
  if (typeof value === 'number' && value <= schema.exclusiveMinimum) fail('is below the exclusive minimum');
  if (Array.isArray(value)) {
    if (value.length < schema.minItems) fail('has too few items');
    if (schema.uniqueItems && new Set(value.map(v => JSON.stringify(v))).size !== value.length) fail('contains duplicates');
    if (schema.items) value.forEach((item, i) => check(item, schema.items, file, path + '[' + i + ']'));
  }
  if (!isRecord(value)) return;
  for (const key of schema.required ?? []) {
    if (!Object.hasOwn(value, key)) fail('is missing ' + key);
  }
  for (const [key, item] of Object.entries(value)) {
    const child = path + '.' + key;
    if (schema.propertyNames) check(key, schema.propertyNames, file, child);
    if (schema.properties && Object.hasOwn(schema.properties, key)) check(item, schema.properties[key], file, child);
    else if (schema.additionalProperties === false) fail('contains unknown property ' + key);
    else if (schema.additionalProperties) check(item, schema.additionalProperties, file, child);
  }
}

export function validateSchema(file, value, path = file) {
  check(value, load(file), file, path);
}
