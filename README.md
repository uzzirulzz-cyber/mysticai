# Mystic AI

Five Gemini-powered specialists plan, design, architect, build, and review a self-contained HTML app. The app is rendered in a sandboxed preview and can be downloaded.

## Run locally

Requires Node.js 20 or newer.

1. Copy `.env.example` to `.env`.
2. Add your Gemini API key as `GEMINI_API_KEY` in `.env`. Never commit `.env`.
3. Run `npm run dev` and open `http://localhost:3000`.

The Gemini key stays on the server. Attachments are sent to Gemini with the build request. Build history and generated HTML are stored only in the current browser's local storage.

## Deploy to Vercel

Import this repository in Vercel, keep the project root as the Root Directory, and add `GEMINI_API_KEY` in Project Settings → Environment Variables for Production and Preview. Redeploy after adding the key. The `api/build.js` function powers `/api/build`.

Set `GEMINI_MODEL` only if you need to select another supported Gemini model. Its default is `gemini-2.5-flash`.

## Android app

The native Kotlin and Jetpack Compose app is in [`android/`](./android/README.md). Open that folder in Android Studio with JDK 17 and Android SDK 35. It connects to the same server-side `/api/build` endpoint; do not put a Gemini API key in the Android app. The Android APK is published to the GitHub release linked from the site footer after the Android build workflow succeeds.

## Pricing

Displayed prices ($19/month Pro and $49/month Team) are informational examples selected for this landing page. Payment processing, accounts, and subscription/plan enforcement are not implemented.

## Limits

The API accepts up to five reference files with a combined size of 3 MB and limits build requests to five per minute per observed client address. The rate limit is in-memory and is a basic safeguard, not durable abuse prevention or authentication. Configure stronger edge rate limits and user authentication before broad public promotion.
