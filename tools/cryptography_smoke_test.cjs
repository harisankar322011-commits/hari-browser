#!/usr/bin/env node
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { webcrypto } = require('node:crypto');
const assert = require('node:assert/strict');

const htmlPath = path.join(__dirname, '../app/src/main/assets/hari_cryptography.html');
const html = fs.readFileSync(htmlPath, 'utf8');
const match = html.match(/<script>([\s\S]*?)<\/script>/);
assert(match, 'cryptography page must contain its local inline script');

const elements = new Map();
for (const id of [
  'themeBtn', 'password', 'filePassword', 'plain', 'textOutput',
  'encryptText', 'decryptText', 'clearText', 'copyText', 'downloadText',
  'fileInput', 'fileInfo', 'encryptFile', 'decryptFile', 'clearFile',
  'genPass', 'genFilePass', 'togglePass', 'toggleFilePass', 'textStatus',
  'fileStatus', 'filePanel', 'textPanel'
]) {
  elements.set(id, {
    value: '', files: [], type: 'password', textContent: '',
    classList: { toggle() {} }, setAttribute() {}, dataset: {},
    onclick: null, onchange: null
  });
}
const tabs = [
  { dataset: { tab: 'textPanel' }, setAttribute() {} },
  { dataset: { tab: 'filePanel' }, setAttribute() {} }
];
const document = {
  hidden: false,
  getElementById: id => elements.get(id),
  querySelectorAll: selector => selector === '[data-tab]' ? tabs : [],
  addEventListener() {}
};
const context = {
  document, crypto: webcrypto, TextEncoder, TextDecoder, Uint8Array, ArrayBuffer,
  btoa: value => Buffer.from(value, 'binary').toString('base64'),
  atob: value => Buffer.from(value, 'base64').toString('binary'),
  navigator: { clipboard: { writeText: async () => {} } },
  setTimeout, clearTimeout, Blob, URL, addEventListener() {}, console,
  Math, JSON, String, Number, Error, Object, Promise
};
context.window = context;
vm.createContext(context);
vm.runInContext(
  match[1] + '\n;globalThis.__cryptoTest = {encryptBytes,decryptBytes,te,td,MAX_BYTES,MAX_CONTAINER_BYTES};',
  context
);

(async () => {
  const api = context.__cryptoTest;
  const password = 'a-very-strong-test-password-123';
  const plaintext = 'Hari Browser cryptography test — മലയാളം — 🔐';
  const cipher = await api.encryptBytes(api.te.encode(plaintext), password, 'notes.txt', 'text/plain');
  const result = await api.decryptBytes(cipher, password);
  assert.equal(api.td.decode(result.bytes), plaintext, 'Unicode/Malayalam text round-trip');
  const secondCipher = await api.encryptBytes(api.te.encode(plaintext), password, 'notes.txt', 'text/plain');
  const firstObj = JSON.parse(api.td.decode(cipher));
  const secondObj = JSON.parse(api.td.decode(secondCipher));
  assert.notEqual(firstObj.salt, secondObj.salt, 'fresh salt for each encryption');
  assert.notEqual(firstObj.iv, secondObj.iv, 'fresh IV for each encryption');
  console.log('PASS: Unicode and Malayalam text round-trip');
  console.log('PASS: fresh random salt and IV per encryption');

  await assert.rejects(
    () => api.decryptBytes(cipher, 'wrong-password-123'),
    /Decryption failed/,
    'wrong password rejected'
  );
  const tampered = { ...firstObj, ciphertext:
    (firstObj.ciphertext[0] === 'A' ? 'B' : 'A') + firstObj.ciphertext.slice(1) };
  await assert.rejects(
    () => api.decryptBytes(api.te.encode(JSON.stringify(tampered)), password),
    /Decryption failed|Invalid or oversized/,
    'tampered ciphertext rejected'
  );
  const changedMetadata = { ...firstObj, name: 'renamed.txt' };
  await assert.rejects(
    () => api.decryptBytes(api.te.encode(JSON.stringify(changedMetadata)), password),
    /Decryption failed/,
    'authenticated metadata tampering rejected'
  );
  console.log('PASS: wrong password, altered ciphertext and altered metadata rejected');

  const binary = Uint8Array.from([0, 1, 2, 127, 128, 254, 255]);
  const encFile = await api.encryptBytes(binary, password, 'data.bin', 'application/octet-stream');
  const decFile = await api.decryptBytes(encFile, password);
  assert.deepEqual(Array.from(decFile.bytes), Array.from(binary), 'binary data round-trip');
  await assert.rejects(
    () => api.encryptBytes(new Uint8Array(api.MAX_BYTES + 1), password, 'large.bin', 'application/octet-stream'),
    /8 MiB/,
    'oversized input rejected'
  );
  await assert.rejects(
    () => api.decryptBytes(new Uint8Array(api.MAX_CONTAINER_BYTES + 1), password),
    /12 MiB/,
    'oversized encrypted container rejected'
  );
  console.log('PASS: binary file round-trip and input-size limits');
})().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
