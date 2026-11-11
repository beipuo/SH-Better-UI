// Author: dishanhai
import { execFileSync } from 'node:child_process';
import { existsSync, mkdtempSync, readFileSync, realpathSync, rmdirSync, unlinkSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, isAbsolute, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs } from 'node:util';
import { assembleCatalog } from './build.mjs';
import { compareVersions, importPreset, resolveMaterial, validateParameters } from './contract.mjs';

const materialId = 'sh_better_ui:liquid_glass';
const api = 'com.dishanhai.sh_better_ui.kernel.api';
const glass = api + '.glass';
const targets = JSON.parse(readFileSync(new URL('./compatibility/loaders.json', import.meta.url), 'utf8'));
const identifier = '[a-zA-Z_$][a-zA-Z0-9_$]*';
const applied = '// shbui-material-applied: ' + materialId;
const materialLocal = 'shbuiCatalogMaterial';
const opticalParameters = ['transmission', 'refraction', 'lensing', 'scattering', 'edgeHighlight', 'thickness', 'blurRadius'];

function ensure(condition, message) {
  if (!condition) throw new Error(message);
}

function relativePath(path) {
  ensure(typeof path === 'string' && /^[a-zA-Z0-9_./-]+$/.test(path)
    && path.split('/').every(part => part && part !== '..' && part !== '.'), 'Expected a project-relative path');
  return path;
}

function inside(root, path) {
  const file = realpathSync(resolve(root, path));
  const relativeFile = relative(root, file);
  ensure(relativeFile !== '..' && !relativeFile.startsWith('..' + sep) && !isAbsolute(relativeFile),
    'File escapes the project root: ' + path);
  return file;
}

function table(text, name) {
  return text.split(/^\s*\[\[/m).slice(1).filter(chunk => chunk.startsWith(name + ']]')).map(chunk =>
    Object.fromEntries([...chunk.matchAll(/^\s*(\w+)\s*=\s*(?:"([^"\r\n]*)"|(true|false))\s*$/gm)]
      .map(([, key, quoted, bool]) => [key, quoted ?? bool])));
}

function modInfo(text) {
  const mods = table(text, 'mods');
  ensure(mods.length === 1 && /^[a-z][a-z0-9_]*$/.test(mods[0].modId ?? ''), 'Expected one mod descriptor');
  return { ...mods[0], dependencies: table(text, 'dependencies.' + mods[0].modId) };
}

function admits(range, actual) {
  const exact = /^\[(\d+\.\d+\.\d+)\]$/.exec(range ?? '');
  if (exact) return compareVersions(actual, exact[1]) === 0;
  const bounded = /^(\[|\()(\d+\.\d+\.\d+),(\d+\.\d+\.\d+)?(\]|\))$/.exec(range ?? '');
  if (!bounded || !bounded[3] && bounded[4] !== ')') return false;
  const [, open, min, max, close] = bounded;
  return compareVersions(actual, min) >= (open === '[' ? 0 : 1)
    && (!max || compareVersions(actual, max) <= (close === ']' ? 0 : -1));
}

function jdk(name, args, cwd) {
  try {
    return execFileSync(name, args, { cwd, encoding: 'utf8', windowsHide: true, timeout: 12000,
      maxBuffer: 2 * 1024 * 1024, stdio: ['ignore', 'pipe', 'pipe'] });
  } catch (error) {
    throw new Error(`${name} could not verify the external library. Use a JDK with jar and javap on PATH.`,
      { cause: error });
  }
}

function jarMetadata(library, path) {
  const temporary = mkdtempSync(join(tmpdir(), 'shbui-jar-audit-'));
  const file = join(temporary, path);
  try {
    jdk('jar', ['--extract', '--file', library, path], temporary);
    return readFileSync(file, 'utf8');
  } finally {
    if (existsSync(file)) unlinkSync(file);
    if (existsSync(dirname(file))) rmdirSync(dirname(file));
    rmdirSync(temporary);
  }
}

function verifyJar(library, loader, platform, loaderVersion, dependency) {
  const files = jdk('jar', ['--list', '--file', library]).trim().split(/\r?\n/);
  const metadata = 'META-INF/' + platform.metadata;
  ensure(files.filter(file => file === metadata).length === 1
    && !files.some(file => file.startsWith(metadata + '/'))
    && !files.includes('META-INF/' + (platform.metadata === 'mods.toml' ? 'neoforge.mods.toml' : 'mods.toml')),
  'External JAR Loader metadata differs from the target');
  const info = modInfo(jarMetadata(library, metadata));
  ensure(info.modId === 'sh_better_ui' && /^\d+\.\d+\.\d+$/.test(info.version)
    && info.dependencies.some(item => item.modId === 'minecraft' && item.versionRange === '[' + platform.minecraft + ']')
    && admits(dependency, info.version) && compareVersions(info.version, '1.0.6') >= 0,
  'External SH-Better-UI JAR version or Minecraft dependency is incompatible');
  ensure(info.dependencies.some(item => item.modId === loader && admits(item.versionRange, loaderVersion)),
    'External SH-Better-UI JAR requires a different Loader version');
  const names = [api + '.RenderService$Context', api + '.RenderService', api + '.UIApis',
    glass + '.GlassDraw', glass + '.GlassMaterial', glass + '.GlassRegion',
    glass + '.GlassOptics', glass + '.GlassQuality', glass + '.GlassVariant', glass + '.GlassMaterial$Foreground'];
  const symbols = jdk('javap', ['-public', '-classpath', library, ...names]);
  const constructors = [
    `${glass}.GlassDraw(${glass}.GlassRegion, ${glass}.GlassMaterial);`,
    `${glass}.GlassMaterial(${glass}.GlassVariant, ${glass}.GlassOptics, ${glass}.GlassQuality, ${glass}.GlassMaterial$Foreground);`,
    `${glass}.GlassOptics(float, float, float, float, float, float, float);`,
  ];
  ensure(symbols.includes(` glass(java.util.List<${glass}.GlassDraw>);`)
    && symbols.includes(` submit(${api}.ModuleContext, ${api}.ScopeHandle,`)
    && symbols.includes(`ServiceKey<${api}.RenderService> RENDER;`)
    && constructors.every(signature => symbols.includes('public ' + signature)),
  'External JAR is missing the reviewed public Liquid Glass signatures');
  const bytecode = jdk('javap', ['-verbose', '-classpath', library, names[0]]).match(/major version:\s*(\d+)/);
  ensure(bytecode && Number(bytecode[1]) <= platform.java + 44, 'External JAR bytecode exceeds the Java toolchain');
  return { version: info.version, signatures: symbols };
}

function javaCode(java) {
  ensure(!/\\u+[\da-fA-F]{4}/.test(java), 'Java Unicode escape syntax requires manual review');
  return java.replace(/\/\*[\s\S]*?\*\/|\/\/[^\n]*|"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'/g,
    token => token.replace(/[^\n]/g, ' '));
}

function findAnchor(java, code) {
  ensure(!java.includes(applied), 'This source is already integrated');
  const pattern = new RegExp(`^([ \\t]*)// shbui-material-anchor: liquid_glass region=(${identifier}) foreground=(LIGHT|DARK)[ \\t]*$`, 'gm');
  const anchors = [...java.matchAll(pattern)];
  ensure(anchors.length === 1, 'Expected exactly one anchor for liquid_glass');
  const anchor = anchors[0];
  const submit = new RegExp(`\\.require\\(UIApis\\.RENDER\\)\\.submit\\(\\s*(${identifier}),\\s*(${identifier}),\\s*`
    + `RenderService\\.Surface\\.SCREEN,\\s*RenderService\\.Layer\\.OVERLAY,\\s*-?\\d+,\\s*(${identifier})\\s*->\\s*\\{\\n$`);
  const before = java.slice(0, anchor.index);
  const call = code.slice(0, anchor.index).match(submit);
  ensure(call, 'Anchor must start the reviewed managed render callback');
  const [, module, scope, pass] = call;
  const [, indent, region, foreground] = anchor;
  // Only identifiers originating in the constrained Java anchor and call are used for matching.
  const matches = (tokens) => code.includes(tokens);
  ensure(matches('.requestPermission(UIPermission.REGISTER_RENDER_PASS)')
    && matches(`${scope} = screens.mount(${module},`) && matches(`${scope}.close()`),
  'Missing render permission, mounted screen scope, or scope cleanup');
  ensure(matches(`var ${region} = GlassRegion.`) || matches(`GlassRegion ${region} = GlassRegion.`),
    'Anchor region must refer to an existing GlassRegion');
  ensure(!matches(`${pass}.glass(`), 'This source is already integrated or has an existing glass call');
  ensure(!matches(materialLocal), 'The reviewed patch local name is already used');
  const callback = code.slice(anchor.index + anchor[0].length).split('});', 1)[0];
  const color = foreground === 'LIGHT' ? '0xffffffff' : '0xff000000';
  ensure(callback.includes(`${pass}.text(`) && (callback.toLowerCase().includes(color)
    || callback.includes(`GlassMaterial.Foreground.${foreground}.argb()`)),
  'Anchor foreground must match the existing text draw');
  return { indent, region, foreground, pass, line: before.split('\n').length - 1 };
}

function targetAudit(root, module, source, override, values) {
  const moduleRoot = module === '.' ? root : inside(root, relativePath(module));
  const read = (folder, path) => readFileSync(inside(folder, path), 'utf8');
  const build = read(moduleRoot, 'build.gradle');
  const settings = read(moduleRoot, 'settings.gradle');
  const properties = read(moduleRoot, 'gradle.properties');
  const candidates = Object.entries(targets).filter(([, entry]) => build.includes(`id '${entry.plugin}'`)
    || build.includes(`id "${entry.plugin}"`));
  ensure(candidates.length === 1, 'Expected one recognized Forge or NeoForge Gradle plugin');
  const [loader, platform] = candidates[0];
  ensure(/\brootProject\.name\s*=\s*['"][a-zA-Z0-9_.-]+['"]/.test(settings),
    'Expected a literal Gradle root project name');
  const toolchains = [...build.matchAll(/JavaLanguageVersion\.of\(\s*(\d+)\s*\)/g)].map(match => Number(match[1]));
  ensure(toolchains.length === 1 && toolchains[0] === platform.java, `Expected the Loader Java ${platform.java} toolchain`);
  const game = loader === 'forge' ? /minecraft\s+['"]net\.minecraftforge:forge:1\.20\.1-(47\.\d+\.\d+)['"]/
    : /\bneoForge\s*\{\s*version\s*=\s*['"](21\.1\.\d+)['"]/;
  const loaderVersion = build.match(game)?.[1];
  ensure(loaderVersion, 'Expected a literal supported Loader dependency in build.gradle');
  ensure(/\bcompileOnly\s+files\(\s*shBetterUiJar\s*\)/.test(build)
    && /\bdef\s+shBetterUiJar\s*=\s*file\(providers\.gradleProperty\(['"]shBetterUiJar['"]\)\.get\(\)\)/.test(build),
  'Expected an external compileOnly JAR declared with the shBetterUiJar Gradle property');
  const declared = [...properties.matchAll(/^\s*shBetterUiJar\s*=\s*([^\r\n]+)$/gm)];
  ensure(declared.length === 1, 'Expected one shBetterUiJar Gradle property');
  const info = modInfo(read(moduleRoot, 'src/main/resources/META-INF/' + platform.metadata));
  ensure(info.dependencies.some(item => item.modId === 'minecraft' && item.versionRange === '[' + platform.minecraft + ']'),
    'Target Minecraft metadata differs from its build configuration');
  const dependencies = info.dependencies.filter(item => item.modId === 'sh_better_ui' && item.side === 'CLIENT'
    && (loader === 'forge' ? item.mandatory === 'true' : item.type === 'required'));
  ensure(dependencies.length === 1, 'Expected one required client SH-Better-UI dependency in Loader metadata');

  const file = relativePath(source);
  ensure(file.endsWith('.java'), 'Expected an existing Java render source');
  const sourceRoots = ['src/main/java/'];
  const modPrefix = module === '.' ? '' : module + '/';
  if (build.includes("java.srcDir('../common/src/main/java')")) sourceRoots.push('common/src/main/java/');
  ensure(file.startsWith(modPrefix + sourceRoots[0]) || sourceRoots.length === 2 && file.startsWith(sourceRoots[1]),
    'Source is outside the declared Java source sets');
  const original = read(root, file);
  const crlf = original.includes('\r\n');
  const java = original.replaceAll('\r\n', '\n');
  ensure(!java.includes('\r') && (!crlf || !original.replaceAll('\r\n', '').includes('\n')),
    'Expected consistent Java line endings');
  const code = javaCode(java);
  const packageName = code.match(/^package ([\w.]+);[ \t]*$/m)?.[1];
  const className = code.match(/\bclass\s+(\w+)\s+implements\s+UIModule\b/)?.[1];
  const sharedModule = 'common/src/main/resources/META-INF/sh_better_ui.modules';
  const manifest = sourceRoots.length === 2 && build.includes("resources.srcDir('../common/src/main/resources')")
    && existsSync(resolve(root, sharedModule)) ? read(root, sharedModule)
    : read(moduleRoot, 'src/main/resources/META-INF/sh_better_ui.modules');
  ensure(packageName && className && manifest.split(/\r?\n/).some(line =>
    line.trim().split(/\s+/).join(' ') === `${info.modId} ${packageName}.${className}`),
  'Target source is not the Loader-discovered UUK client module');
  ensure([api + '.UIApis', api + '.RenderService', api + '.UIModule', api + '.UIPermission', glass + '.GlassRegion']
    .every(name => code.includes(`import ${name};`)), 'Target must import the reviewed public UUK and GlassRegion types');
  const anchor = findAnchor(java, code);
  ensure(values.foreground === anchor.foreground, 'Preset foreground differs from the host foreground');

  const jar = realpathSync(override ? resolve(root, override) : resolve(moduleRoot, declared[0][1].trim()));
  const verified = verifyJar(jar, loader, platform, loaderVersion, dependencies[0].versionRange);
  ensure([values.quality, values.foreground, values.variant ?? 'REGULAR'].every(name =>
    verified.signatures.includes(' ' + name + ';')), 'External JAR is missing a required enum constant');
  return { java, file, anchor, eol: crlf ? '\r\n' : '\n', details: { loader, loaderVersion,
    minecraftVersion: platform.minecraft, javaVersion: platform.java, modId: info.modId, dependencyVersion: verified.version,
    dependency: jar, module, source: file, scope: 'mounted screen overlay',
    compileArgs: ['-p', moduleRoot, 'compileJava', ...(override ? ['-PshBetterUiJar=' + jar] : [])] } };
}

function integrationDiff({ java, file, anchor, eol }, values) {
  const { indent, pass, region, line } = anchor;
  const optics = opticalParameters.map(parameter => String(values[parameter]) + 'f');
  const inserted = [
    `${indent}${applied}`,
    `${indent}var ${materialLocal} = new ${glass}.GlassMaterial(`,
    `${indent}        ${glass}.GlassVariant.${values.variant ?? 'REGULAR'},`,
    `${indent}        new ${glass}.GlassOptics(${optics.join(', ')}),`,
    `${indent}        ${glass}.GlassQuality.${values.quality},`,
    `${indent}        ${glass}.GlassMaterial.Foreground.${values.foreground});`,
    `${indent}${pass}.glass(java.util.List.of(new ${glass}.GlassDraw(${region}, ${materialLocal})));`,
  ];
  // One insertion immediately after the marker and before the existing foreground.
  const lines = java.split('\n');
  return `diff --git a/${file} b/${file}\n--- a/${file}\n+++ b/${file}\n`
    + `@@ -${line},3 +${line},${3 + inserted.length} @@\n`
    + [' ' + lines[line - 1], ' ' + lines[line], ...inserted.map(text => '+' + text), ' ' + lines[line + 1]].join(eol) + eol;
}

/** Returns an audit and a reviewable diff. The target Gradle script and patched code are never executed here. */
export function proposeIntegration({ catalog, target, module = '.', source, materialId: requested, values, library }) {
  const entry = resolveMaterial(catalog, requested);
  ensure(entry?.id === materialId, 'No reviewed integration template for this material');
  const parameters = validateParameters(entry, values);
  ensure(Object.keys(parameters).every(key => opticalParameters.includes(key) || ['quality', 'foreground', 'variant'].includes(key))
    && opticalParameters.every(key => Number.isFinite(parameters[key])), 'Catalog parameters differ from the reviewed optics adapter');
  const inspected = targetAudit(realpathSync(target), module, source, library, parameters);
  return { audit: inspected.details, diff: integrationDiff(inspected, parameters) };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const { values: args } = parseArgs({ options: { target: { type: 'string' }, module: { type: 'string' },
      source: { type: 'string' }, material: { type: 'string' }, library: { type: 'string' },
      preset: { type: 'string' }, audit: { type: 'boolean' } } });
    ensure(args.target && args.source && args.material,
      'Usage: node catalog/integration.mjs --target DIR --source FILE --material ID [--module DIR] [--library JAR] [--preset JSON] [--audit]');
    const catalog = assembleCatalog();
    const preset = args.preset ? importPreset(catalog, readFileSync(args.preset, 'utf8')) : null;
    ensure(!preset || preset.materialId === resolveMaterial(catalog, args.material)?.id,
      'Preset material differs from the requested material');
    const result = proposeIntegration({ catalog, target: args.target, module: args.module, source: args.source,
      materialId: args.material, library: args.library, values: preset?.parameters });
    process.stdout.write(args.audit ? JSON.stringify(result.audit, null, 2) + '\n' : result.diff);
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
