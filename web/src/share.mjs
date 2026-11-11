// Author: dishanhai
const PREFIX = '#umivp1.';
const MAX_BYTES = 6_000;
const MAX_LENGTH = PREFIX.length + Math.ceil(MAX_BYTES * 4 / 3) + 9;

function checksum(bytes) {
  let hash = 0x811c9dc5;
  for (const byte of bytes) hash = Math.imul(hash ^ byte, 0x01000193);
  return (hash >>> 0).toString(16).padStart(8, '0');
}

export function encodeShare(json) {
  if (json.length > MAX_BYTES) throw new Error('分享预设超出大小限制');
  const bytes = new TextEncoder().encode(json);
  if (bytes.length > MAX_BYTES) throw new Error('分享预设超出大小限制');
  const data = btoa(String.fromCharCode(...bytes)).replaceAll('+', '-').replaceAll('/', '_').replace(/=+$/, '');
  return `${PREFIX}${data}.${checksum(bytes)}`;
}

export function decodeShare(fragment) {
  if (fragment.length > MAX_LENGTH) throw new Error('分享链接超出长度限制');
  const match = /^#umivp1\.([A-Za-z0-9_-]+)\.([0-9a-f]{8})$/.exec(fragment);
  if (!match) throw new Error('分享链接版本或编码无效');
  const data = atob(match[1].replaceAll('-', '+').replaceAll('_', '/'));
  const bytes = Uint8Array.from(data, character => character.charCodeAt(0));
  if (bytes.length > MAX_BYTES || checksum(bytes) !== match[2]) throw new Error('分享预设校验失败');
  return new TextDecoder('utf-8', { fatal: true }).decode(bytes);
}
