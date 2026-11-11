// Author: dishanhai
import { createServer } from 'node:http';
import { readFile, realpath } from 'node:fs/promises';
import { extname, isAbsolute, relative, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const MIME = {
  '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8', '.json': 'application/json; charset=utf-8',
  '.png': 'image/png', '.vert': 'text/plain; charset=utf-8', '.frag': 'text/plain; charset=utf-8',
};

export async function serve({ root = new URL('../dist/', import.meta.url), port = 0, testAssets = new Map() } = {}) {
  const base = await realpath(root);
  const server = createServer(async (request, response) => {
    try {
      if (!['GET', 'HEAD'].includes(request.method)) return response.writeHead(405).end();
      const pathname = decodeURIComponent(new URL(request.url, 'http://127.0.0.1').pathname);
      const asset = pathname === '/' ? 'index.html' : pathname.slice(1);
      const headers = { 'Content-Type': MIME[extname(asset)] ?? 'application/octet-stream', 'Cache-Control': 'no-store' };
      const fixture = testAssets.get(pathname);
      if (fixture) return response.writeHead(fixture.status ?? 200, headers).end(fixture.data);
      const target = await realpath(resolve(base, asset));
      const local = relative(base, target);
      if (!local || local.startsWith('..') || isAbsolute(local)) return response.writeHead(404).end();
      const data = await readFile(target);
      response.writeHead(200, headers).end(request.method === 'GET' ? data : undefined);
    } catch {
      response.writeHead(404).end();
    }
  });
  await new Promise((done, fail) => server.once('error', fail).listen(port, '127.0.0.1', done));
  return {
    url: `http://127.0.0.1:${server.address().port}/`,
    close: () => new Promise((done, fail) => server.close(error => error ? fail(error) : done())),
  };
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const server = await serve({ port: Number(process.argv[2] ?? 4173) });
  console.log(`Material Catalog at ${server.url}`);
}
