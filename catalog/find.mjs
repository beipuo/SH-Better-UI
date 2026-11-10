// Author: dishanhai
import { readFileSync } from 'node:fs';
import { searchMaterials } from './contract.mjs';

const catalog = JSON.parse(readFileSync(new URL('./generated/catalog.json', import.meta.url), 'utf8'));
const query = process.argv.slice(2).join(' ');
const results = searchMaterials(catalog, query).slice(0, 10);
console.log(JSON.stringify(results.map(({ entry, reasons, missing }) => ({
  id: entry.id,
  name: entry.name,
  reasons,
  missing,
  nativePreviewId: entry.nativePreviewId,
  webCompatibility: entry.webCompatibility,
  loaderCompatibility: entry.loaderCompatibility,
  publicEntrypoint: entry.publicEntrypoint,
  useCases: entry.useCases,
  sourceFiles: entry.sourceFiles,
})), null, 2));
