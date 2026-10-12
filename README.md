# Pocket Studio

An open, non-profit AI studio that runs entirely on a phone — so people without a computer can still write, edit and publish.

- Source version: `0.3.0-community-dev` — a development build, still in testing. Not a finished product.
- Android app, package `com.creationai.community`, minimum Android 8.0 (API 26).
- Built for the Chinese version of Toutiao `15.4.0`.

## Why this exists

Plenty of people want to create content for a little extra income, but the only device they own is a phone. Every tool that would help them — rewriting an article, trimming a video, getting it published — quietly assumes they have a desktop.

Pocket Studio moves that whole workflow onto the phone. AI does the heavy lifting on writing and editing; the person using it stays in control of what actually gets published.

**This is a non-profit project.** No fee, no license key, no subscription, no referral cut. There is no business model behind it.

It is still under active development and is not finished. The installable build is a development APK, not a release.

## What it does today

- Native Android UI with preflight checks for the Toutiao install/version, network and accessibility service.
- Two separate modes: *original news editing* and *quick processing of licensed footage*.
- Reads user-configured HTTPS RSS/Atom feeds and de-duplicates, filters and ranks items by topic, tag and recency.
- Calls OpenAI-compatible chat APIs (SiliconFlow, DeepSeek, …) to produce sourced article drafts, 60–120 second voiceover scripts, cover prompts, storyboards and tags.
- Saves each content package as structured JSON in app-private storage, editable and copyable from the UI.
- Filters candidate videos by title tags/keywords, view count, likes and publish time — and never substitutes likes for views when views are not readable.
- Accepts Toutiao share text and extracts the short or long link.
- Reads the public Toutiao page, calls the official play-info endpoint used by the web player, and picks the best 720p MP4.
- Downloads into `MediaStore` under `Movies/CreationAI`. It does not wipe the gallery.
- 512 MB download cap, response-type checks, and cleanup of partial files after a failure.
- The user supplies their own model API key; it is encrypted with Android Keystore and kept on the device.
- Endpoint and model name are both configurable.
- AI title rewriting, plus local-only title cleanup when no key is set.
- Trims leading/trailing frames using the video's real frame rate and exports to the gallery.
- Selects the media, fills in the title and stops at the final publish review screen. It never taps publish.
- 5 unit tests covering filtering and frame math; the debug APK builds.
- Free: no license key, no subscription, no account.

## What is not done yet

- Page selectors, channel sweep and search mode for the pinned Toutiao version.
- Stable reading of author follower counts and view counts across different page layouts (needs on-device calibration).
- Expanding from a generic web-search API to full-web retrieval with source evidence and conflict checking.
- Automatic assembly of licensed/AI visuals, TTS and subtitles into a preview video.
- Multi-account queues, retries, audit logs and distribution adapters that stay inside platform rules.
- Full device regression, a signed release APK and a final user manual.

Until those land, this should not be described as a fully automatic publishing tool.

## Repository layout

| Path | What it is |
| --- | --- |
| `community-app/` | Native Android source (Java 17, Gradle 8.13, `compileSdk 36`) |
| `docs/user-manual.md` | End-user manual for the test build (Chinese — that is who the app is for) |
| `docs/original-editing-plan.md` | Staged plan for the news-to-original-content pipeline (Chinese) |
| `tools/generate_user_manual_pdf.py` | Renders the manual to a phone-friendly PDF |

Build outputs, signing material and machine-specific config are not committed — see [.gitignore](.gitignore).

## Build locally

Requires JDK 17, Android SDK 36, Build Tools 35/36 and Gradle 8.13.

```bash
cd community-app
./gradlew :app:assembleDebug
```

The APK lands at:

```text
community-app/app/build/outputs/apk/debug/app-debug.apk
```

`community-app/local.properties` must point at your local Android SDK; it is not committed. To run the unit tests:

```bash
cd community-app
./gradlew :app:testDebugUnitTest
```

## Ground rules

- Only handle material you own, are licensed to use, or that the platform explicitly allows to be reused.
- An AI-rewritten title does not turn unlicensed reposting into original work, and does not replace fact-checking.
- Never auto-declares content provenance, and never bypasses platform review, risk control or account limits.
- Always stops at the publish review screen — the person decides.
- When the platform's pages or endpoints change, update the selectors/parsers and retest. Never silently perform uncertain taps.
