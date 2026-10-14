'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const handler = require('../api/build');

function invoke(method, body, headers = {}) {
  const response = {
    statusCode: 200,
    headers: {},
    body: null,
    setHeader(name, value) { this.headers[name] = value; },
    writeHead(code, values) { this.statusCode = code; this.headers = { ...this.headers, ...values }; },
    end(value) { this.body = value ? JSON.parse(value) : null; },
    status(code) {
      this.statusCode = code;
      return { json: payload => { this.body = payload; } };
    },
    json(payload) { this.body = payload; }
  };
  return handler({ method, body, headers, socket: { remoteAddress: '127.0.0.1' } }, response).then(() => response);
}

test('rejects unsupported methods', async () => {
  const response = await invoke('GET');
  assert.equal(response.statusCode, 405);
});

test('validates empty project descriptions', async () => {
  const response = await invoke('POST', { prompt: '  ' });
  assert.equal(response.statusCode, 400);
  assert.match(response.body.error, /description is required/i);
});

test('rejects files with unsupported MIME types', async () => {
  const response = await invoke('POST', { prompt: 'Create a website', files: [{ name: 'archive.zip', mimeType: 'application/zip', data: 'ZmlsZQ==' }] });
  assert.equal(response.statusCode, 400);
  assert.match(response.body.error, /unsupported file type/i);
});

test('requires the Gemini API key without calling a provider', async () => {
  const previousKey = process.env.GEMINI_API_KEY;
  delete process.env.GEMINI_API_KEY;
  try {
    const response = await invoke('POST', { prompt: 'Create a website' });
    assert.equal(response.statusCode, 503);
    assert.match(response.body.error, /GEMINI_API_KEY/);
  } finally {
    if (previousKey !== undefined) process.env.GEMINI_API_KEY = previousKey;
  }
});
