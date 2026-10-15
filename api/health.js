'use strict';

function healthStatus() {
  const configured = Boolean(process.env.GEMINI_API_KEY && process.env.GEMINI_API_KEY.trim());
  return {
    status: configured ? 'ready' : 'degraded',
    service: 'mystic-ai',
    timestamp: new Date().toISOString(),
    checks: {
      api: 'ok',
      gemini_configuration: configured ? 'configured' : 'missing'
    },
    // A configured key does not prove upstream connectivity.
    provider_connection: 'not_tested'
  };
}

function handler(req, res) {
  res.setHeader('cache-control', 'no-store');
  res.setHeader('x-content-type-options', 'nosniff');
  if (req.method !== 'GET' && req.method !== 'HEAD') {
    res.setHeader('allow', 'GET, HEAD');
    return res.status(405).json({ error: 'Method not allowed' });
  }
  const result = healthStatus();
  res.status(result.status === 'ready' ? 200 : 503);
  if (req.method === 'HEAD') return res.end();
  return res.json(result);
}

module.exports = handler;
module.exports.healthStatus = healthStatus;
