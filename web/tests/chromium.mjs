// Author: dishanhai
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { access, mkdtemp, readFile, realpath, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { basename, dirname, join } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';

const candidates = [
  process.env.CHROME_PATH,
  process.env.PROGRAMFILES && join(process.env.PROGRAMFILES, 'Google/Chrome/Application/chrome.exe'),
  process.env['ProgramFiles(x86)'] && join(process.env['ProgramFiles(x86)'], 'Microsoft/Edge/Application/msedge.exe'),
  process.platform === 'win32' && `${process.env.SystemDrive ?? 'C:'}/Program Files/Google/Chrome/Application/chrome.exe`,
  process.platform === 'win32' && `${process.env.SystemDrive ?? 'C:'}/Program Files (x86)/Microsoft/Edge/Application/msedge.exe`,
  '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', '/usr/bin/google-chrome', '/usr/bin/chromium',
];

async function executable() {
  for (const candidate of candidates.filter(Boolean)) {
    try { await access(candidate); return candidate; } catch { /* try the next installed browser */ }
  }
  throw new Error('Chromium was not found. Set CHROME_PATH to a local Chrome, Edge or Chromium executable.');
}

export async function eventually(fn, label, timeoutMs = 15_000) {
  const until = Date.now() + timeoutMs;
  while (Date.now() < until) {
    const result = await fn();
    if (result) return result;
    await delay(50);
  }
  throw new Error(`Timed out: ${label}`);
}

export async function chromium() {
  const binary = await executable();
  const temp = await realpath(tmpdir());
  const profile = await mkdtemp(join(temp, 'umivp-chromium-'));
  const child = spawn(binary, [
    '--headless=new', '--no-first-run', '--disable-extensions', '--disable-background-networking',
    '--no-proxy-server', '--remote-debugging-port=0', '--remote-debugging-address=127.0.0.1',
    '--disable-component-update', '--window-size=1440,960',
    ...(process.env.UMIVP_HARDWARE_GL === '1' ? [] : ['--use-angle=swiftshader', '--enable-unsafe-swiftshader']),
    `--user-data-dir=${profile}`, 'about:blank',
  ], { windowsHide: true, stdio: 'ignore' });
  let socket;
  let launchError;
  child.on('error', error => { launchError = error; });
  const cleanup = async () => {
    socket?.close();
    if (child.pid && child.exitCode === null && child.signalCode === null) {
      child.kill();
      await Promise.race([once(child, 'exit'), delay(3000)]);
    }
    const target = await realpath(profile);
    if (dirname(target) === temp && basename(target).startsWith('umivp-chromium-')) {
      await rm(target, { recursive: true, force: true, maxRetries: 10, retryDelay: 100 });
    }
  };
  try {
    const port = await eventually(async () => {
      if (launchError) throw launchError;
      if (child.exitCode !== null) throw new Error(`Chromium exited with ${child.exitCode}`);
      return (await readFile(join(profile, 'DevToolsActivePort'), 'utf8').catch(() => '')).split('\n')[0];
    }, 'Chrome DevTools port');
    const tabs = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
    socket = new WebSocket(tabs.find(tab => tab.type === 'page').webSocketDebuggerUrl);
    await new Promise((done, fail) => {
      socket.addEventListener('open', done, { once: true });
      socket.addEventListener('error', fail, { once: true });
    });
    let nextId = 0;
    const pending = new Map();
    socket.addEventListener('message', ({ data }) => {
      const message = JSON.parse(data);
      const task = pending.get(message.id);
      if (!task) return;
      pending.delete(message.id);
      clearTimeout(task.timer);
      if (message.error) task.fail(new Error(JSON.stringify(message.error)));
      else task.done(message.result);
    });
    socket.addEventListener('close', () => {
      for (const task of pending.values()) {
        clearTimeout(task.timer);
        task.fail(new Error('Chromium debugging socket closed'));
      }
      pending.clear();
    });
    const command = (method, params = {}) => new Promise((done, fail) => {
      const id = ++nextId;
      const timer = setTimeout(() => {
        pending.delete(id);
        fail(new Error(`Chromium command timed out: ${method}`));
      }, 25_000);
      pending.set(id, { done, fail, timer });
      socket.send(JSON.stringify({ id, method, params }));
    });
    const evaluate = async expression => {
      const result = await command('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true, userGesture: true });
      if (result.exceptionDetails) throw new Error(result.exceptionDetails.exception?.description ?? result.exceptionDetails.text);
      return result.result.value;
    };
    const call = (fn, ...args) => evaluate(`(${fn.toString()})(...${JSON.stringify(args)})`);
    return { command, evaluate, call, close: cleanup };
  } catch (error) {
    await cleanup();
    throw error;
  }
}
