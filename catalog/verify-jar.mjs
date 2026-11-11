// Author: dishanhai
import { spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';

const snapshot = JSON.parse(readFileSync(new URL('./generated/catalog.json', import.meta.url), 'utf8'));
const jdk = name => process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', name) : name;
const targets = process.argv.slice(2);
const assetRoot = 'assets/sh_better_ui/material_catalog/';
const resources = {
  [assetRoot + 'catalog.json']: './generated/catalog.json',
  [assetRoot + 'fixtures/liquid-glass.json']: './fixtures/liquid-glass.json',
  [assetRoot + 'fixtures/liquid-glass.png']: './fixtures/liquid-glass.png',
};
const symbols = new Set(snapshot.materials.flatMap(entry => entry.publicSymbols));
const owners = new Map();
for (const symbol of symbols) {
  const [name, member] = symbol.split('#');
  const parts = name.split('.');
  const classIndex = parts.findIndex(part => /^[A-Z]/.test(part));
  if (classIndex < 0) throw new Error('Invalid public symbol owner: ' + symbol);
  const binary = [...parts.slice(0, classIndex), parts.slice(classIndex).join('$')].join('.');
  if (!owners.has(binary)) owners.set(binary, []);
  if (member) owners.get(binary).push(member);
}

function command(tool, args, cwd) {
  const result = spawnSync(jdk(tool), args, { cwd, encoding: 'utf8', maxBuffer: 16 * 1024 * 1024 });
  if (result.error || result.status !== 0) throw new Error(result.error?.message ?? result.stderr);
  return result.stdout;
}

function verify(archive) {
  const members = command('jar', ['--list', '--file', archive]).split(/\r?\n/);
  for (const asset of Object.keys(resources)) {
    if (members.filter(member => member === asset).length !== 1) throw new Error(archive + ' expected one copy of ' + asset);
  }
  const temporary = mkdtempSync(join(tmpdir(), 'shbui-material-jar-'));
  try {
    command('jar', ['--extract', '--file', resolve(archive), ...Object.keys(resources)], temporary);
    for (const [asset, source] of Object.entries(resources)) {
      if (!readFileSync(join(temporary, asset)).equals(readFileSync(new URL(source, import.meta.url)))) {
        throw new Error(archive + ' asset differs from the canonical file: ' + asset);
      }
    }
  } finally {
    rmSync(temporary, { recursive: true });
  }
  const symbolsText = command('javap', ['-public', '-classpath', archive, ...owners.keys()]);
  const declarations = new Map();
  let type;
  for (const line of symbolsText.split(/\r?\n/)) {
    const owner = line.match(/^public .*?\b(?:class|interface|enum|record) ([\w.$]+)/);
    if (owner) {
      type = [];
      declarations.set(owner[1], type);
    } else if (line === '}') {
      type = undefined;
    } else if (line.startsWith('  public ') && type) {
      const member = line.match(/\b([\w$]+)(?:\(|;$)/);
      if (member) type.push(member[1]);
    }
  }
  const missing = [];
  for (const [name, members] of owners) {
    if (!declarations.has(name)) missing.push(name);
    else for (const member of members) if (!declarations.get(name).includes(member)) missing.push(name + '#' + member);
  }
  if (missing.length) throw new Error('Missing public symbols in ' + archive + '\n' + missing.join('\n'));
  console.log(archive + ' verified ' + symbols.size + ' public symbols in ' + owners.size + ' classes and canonical catalog/fixtures');
}

try {
  if (!targets.length) throw new Error('Usage: node catalog/verify-jar.mjs <release.jar> [...]');
  targets.forEach(verify);
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
