'use strict';

const MAX_PROMPT_LENGTH = 6000;
const MAX_FILES = 5;
const MAX_FILE_BYTES = 3 * 1024 * 1024;
const MAX_OUTPUT_TOKENS = 8192;
const MODEL = process.env.GEMINI_MODEL || 'gemini-2.5-flash';
const WINDOW_MS = 60 * 1000;
const REQUESTS_PER_WINDOW = 5;
const requestWindows = new Map();

const ALLOWED_MIME_TYPES = new Set([
  'image/png', 'image/jpeg', 'image/webp', 'image/gif', 'application/pdf',
  'text/plain', 'text/markdown', 'text/csv', 'text/html', 'text/css',
  'text/javascript', 'application/json'
]);

function send(res, status, payload) {
  if (typeof res.status === 'function' && typeof res.json === 'function') {
    res.status(status).json(payload);
    return;
  }
  res.writeHead(status, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' });
  res.end(JSON.stringify(payload));
}

function requestOriginAllowed(req) {
  const origin = req.headers?.origin;
  if (!origin) return true;
  try {
    const requestHost = req.headers?.['x-forwarded-host'] || req.headers?.host;
    return new URL(origin).host === requestHost;
  } catch {
    return false;
  }
}

function checkRateLimit(req) {
  const forwarded = req.headers?.['x-forwarded-for'];
  const address = String(forwarded || req.socket?.remoteAddress || 'local').split(',')[0].trim();
  const now = Date.now();
  for (const [key, entry] of requestWindows) {
    if (now - entry.startedAt > WINDOW_MS) requestWindows.delete(key);
  }
  const previous = requestWindows.get(address);
  if (previous && now - previous.startedAt < WINDOW_MS) {
    if (previous.count >= REQUESTS_PER_WINDOW) return false;
    previous.count += 1;
  } else {
    requestWindows.set(address, { startedAt: now, count: 1 });
  }
  return true;
}

function validateInput(body) {
  if (!body || typeof body !== 'object' || Array.isArray(body)) throw new Error('Send a JSON object with a prompt and optional files.');
  if (typeof body.prompt !== 'string' || !body.prompt.trim()) throw new Error('A project description is required.');
  if (body.prompt.length > MAX_PROMPT_LENGTH) throw new Error(`The project description must be ${MAX_PROMPT_LENGTH} characters or fewer.`);
  const files = body.files ?? [];
  if (!Array.isArray(files) || files.length > MAX_FILES) throw new Error(`Attach no more than ${MAX_FILES} files.`);
  let totalBytes = 0;
  const validFiles = files.map(file => {
    if (!file || typeof file.name !== 'string' || typeof file.mimeType !== 'string' || typeof file.data !== 'string') {
      throw new Error('One of the attached files is invalid.');
    }
    if (!ALLOWED_MIME_TYPES.has(file.mimeType)) throw new Error(`Unsupported file type for ${file.name}.`);
    if (!/^[A-Za-z0-9+/]*={0,2}$/.test(file.data) || file.data.length % 4 !== 0) throw new Error(`${file.name} is not valid base64 data.`);
    const size = Math.floor(file.data.length * 3 / 4) - (file.data.endsWith('==') ? 2 : file.data.endsWith('=') ? 1 : 0);
    if (size > MAX_FILE_BYTES || size === 0) throw new Error(`${file.name} is empty or larger than 3 MB.`);
    totalBytes += size;
    return { name: file.name.slice(0, 180), mimeType: file.mimeType, data: file.data };
  });
  if (totalBytes > MAX_FILE_BYTES) throw new Error('The combined attachment size must be 3 MB or less.');
  return { prompt: body.prompt.trim(), files: validFiles };
}

function fileParts(files) {
  const parts = [];
  for (const file of files) {
    if (file.mimeType.startsWith('text/') || file.mimeType === 'application/json') {
      const text = Buffer.from(file.data, 'base64').toString('utf8').slice(0, 16000);
      parts.push({ text: `Reference file "${file.name}":\n${text}` });
    } else {
      parts.push({ inlineData: { mimeType: file.mimeType, data: file.data } });
    }
  }
  return parts;
}

async function gemini(systemInstruction, userText, files, maxOutputTokens, timeoutMs = 12000) {
  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) throw Object.assign(new Error('Gemini is not configured. Set GEMINI_API_KEY in your local environment or Vercel project settings.'), { statusCode: 503 });
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(MODEL)}:generateContent`;
  let response;
  try {
    response = await fetch(url, {
      method: 'POST',
      headers: { 'content-type': 'application/json', 'x-goog-api-key': apiKey },
      signal: AbortSignal.timeout(timeoutMs),
      body: JSON.stringify({
        systemInstruction: { parts: [{ text: systemInstruction }] },
        contents: [{ role: 'user', parts: [{ text: userText }, ...fileParts(files)] }],
        generationConfig: { temperature: 0.25, maxOutputTokens }
      })
    });
  } catch (error) {
    if (error.name === 'TimeoutError' || error.name === 'AbortError') {
      throw Object.assign(new Error('Gemini took too long to respond. Try a smaller request or try again.'), { statusCode: 504 });
    }
    throw Object.assign(new Error('Could not connect to Gemini. Check the server network and try again.'), { statusCode: 502 });
  }
  const payload = await response.json().catch(() => null);
  if (!response.ok) {
    const statusCode = response.status === 429 ? 429 : response.status === 400 ? 400 : response.status === 401 || response.status === 403 ? 502 : 502;
    if (response.status === 429) throw Object.assign(new Error('Gemini rate limit reached. Wait a moment and try again.'), { statusCode });
    if (response.status === 401 || response.status === 403) throw Object.assign(new Error('Gemini rejected the configured API key. Check that GEMINI_API_KEY is valid and has Gemini API access.'), { statusCode });
    if (response.status === 400) throw Object.assign(new Error(payload?.error?.message || 'Gemini could not accept this request. Check the attachments and try again.'), { statusCode });
    throw Object.assign(new Error('Gemini could not complete the request. Try again later.'), { statusCode });
  }
  const text = payload?.candidates?.[0]?.content?.parts?.map(part => part.text || '').join('').trim();
  if (!text) throw Object.assign(new Error('Gemini returned no text. Try revising the project description.'), { statusCode: 502 });
  return text;
}

function normalizeHtml(text) {
  const cleaned = text.trim()
    .replace(/^```(?:html)?\s*/i, '')
    .replace(/\s*```$/, '')
    .trim();
  const start = cleaned.toLowerCase().indexOf('<!doctype html');
  const htmlStart = cleaned.toLowerCase().indexOf('<html');
  const offset = start >= 0 ? start : htmlStart;
  if (offset < 0 || !cleaned.toLowerCase().includes('</html>')) {
    throw Object.assign(new Error('The Builder did not return a complete HTML document. Please try again with a more specific brief.'), { statusCode: 502 });
  }
  const html = cleaned.slice(offset);
  if (html.length > 250000) throw Object.assign(new Error('The generated HTML is too large to preview. Ask for a smaller first version.'), { statusCode: 502 });
  return html;
}

async function build(body) {
  const { prompt, files } = validateInput(body);
  const common = `Project brief:\n${prompt}`;
  const agents = {};
  const planningRoles = [
    ['planner', 'Product Planner', 'Return a concise, practical requirements checklist for the requested web app. Include key user flows and edge cases. Maximum 180 words.'],
    ['designer', 'UI/UX Designer', 'Propose an accessible, responsive visual direction and page layout tailored to this request. Maximum 150 words.'],
    ['architect', 'Frontend Architect', 'Specify a self-contained HTML/CSS/JavaScript implementation plan and essential behavior. Avoid frameworks, build tools, and backend features unless explicitly requested. Maximum 150 words.']
  ];
  const plans = await Promise.all(planningRoles.map(async ([key, role, instructions]) => {
    const referenceFiles = key === 'planner' ? files : [];
    const output = await gemini(`You are the ${role} on a fast, careful AI product team. Be specific, concise, and honest about scope. ${instructions}`, common, referenceFiles, 1000, 12000);
    agents[key] = { status: 'Complete', output: output.slice(0, 4000) };
    return `${role}:\n${output}`;
  }));
  const reference = files.length ? `\nUser attached ${files.length} reference file(s). Respect relevant details from their contents.` : '';
  const generated = await gemini(
    `You are the Builder agent. Implement the requested website as a polished, usable, responsive single-file web app. Use semantic HTML and inline CSS and JavaScript, with no external dependencies or external network requests. Make every visible control work. Meet the request and this specialist guidance:\n${plans.join('\n\n')}\nReturn only one complete HTML document beginning with <!doctype html>. Do not use Markdown fences, explanations, placeholders, or claim unsupported integrations.${reference}`,
    `${common}\nGenerate the complete app now.`,
    files,
    MAX_OUTPUT_TOKENS,
    35000
  );
  const html = normalizeHtml(generated);
  const review = await gemini(
    'You are the QA Reviewer. Review the generated HTML against the user brief. Give a short, actionable review (maximum 180 words); inspect completeness, likely browser issues, responsive layout, and accessibility. Do not claim you executed the code. Start with PASS or NEEDS ATTENTION.',
    `User brief:\n${prompt}\n\nGenerated HTML to review:\n${html.length > 24000 ? `${html.slice(0, 16000)}\n[...middle omitted for review...]\n${html.slice(-8000)}` : html}`,
    [],
    1000,
    8000
  );
  agents.builder = { status: 'Complete', output: `Generated ${html.length.toLocaleString()} characters of HTML.` };
  agents.reviewer = { status: 'Complete', output: review.slice(0, 4000) };
  return { html, agents, model: MODEL };
}

async function handler(req, res) {
  if (req.method !== 'POST') {
    res.setHeader?.('allow', 'POST');
    return send(res, 405, { error: 'Use POST to start a build.' });
  }
  if (!requestOriginAllowed(req)) return send(res, 403, { error: 'Requests must come from this website.' });
  if (!checkRateLimit(req)) return send(res, 429, { error: 'This address has started too many builds. Please wait a minute and try again.' });
  try {
    return send(res, 200, await build(req.body));
  } catch (error) {
    const status = Number.isInteger(error.statusCode) ? error.statusCode : 400;
    if (status >= 500) console.error('Build request failed:', error.message);
    return send(res, status, { error: error.message || 'The build failed.' });
  }
}

module.exports = handler;
