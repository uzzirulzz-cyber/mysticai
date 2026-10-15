'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const handler = require('../api/health');

function invoke(method) {
  const headers = {};
  let statusCode;
  let body;
  let ended = false;
  const res = {
    setHeader(name, value) { headers[name.toLowerCase()] = value; },
    status(code) { statusCode = code; return this; },
    json(value) { body = value; ended = true; return this; },
    end() { ended = true; return this; }
  };
  handler({ method }, res);
  return { headers, statusCode, body, ended };
}

test('GET health reports degraded when Gemini key is absent', () => {
  const key = process.env.GEMINI_API_KEY;
  delete process.env.GEMINI_API_KEY;
  try {
    const result = invoke('GET');
    assert.equal(result.statusCode, 503);
    assert.equal(result.body.checks.api, 'ok');
    assert.equal(result.body.checks.gemini_configuration, 'missing');
    assert.equal(result.body.provider_connection, 'not_tested');
    assert.equal(result.headers['cache-control'], 'no-store');
  } finally {
    if (key !== undefined) process.env.GEMINI_API_KEY = key;
  }
});

test('GET health reports configuration without exposing secrets', () => {
  const key = process.env.GEMINI_API_KEY;
  process.env.GEMINI_API_KEY = 'test-private-key';
  try {
    const result = invoke('GET');
    assert.equal(result.statusCode, 200);
    assert.equal(result.body.checks.gemini_configuration, 'configured');
    assert.ok(!JSON.stringify(result.body).includes('test-private-key'));
  } finally {
    if (key === undefined) delete process.env.GEMINI_API_KEY;
    else process.env.GEMINI_API_KEY = key;
  }
});

test('HEAD returns no body; other methods reject', () => {
  assert.equal(invoke('HEAD').body, undefined);
  assert.equal(invoke('POST').statusCode, 405);
});
