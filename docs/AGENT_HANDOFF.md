# AI Coder Handoff: PocketLab v0.1

You are improving PocketLab, an Android app that runs pasted Python tools locally on a phone and packages results for ChatGPT/Claude.

## Current implementation

- Kotlin + Jetpack Compose Android app.
- Chaquopy embedded Python runtime.
- Main UI lives in `MainActivity.kt`.
- Python runner lives in `app/src/main/python/pocketlab_runner.py`.
- User pastes a script, taps Run, sees stdout/stderr, and copies a result report.

## Strict goal

PocketLab is not trying to be a full coding agent yet. The first successful product is:

> paste Python → run Python → capture result → copy/export evidence back to external AI.

## Current limitations to fix next

1. Add Android Storage Access Framework import for `.py` files.
2. Add share/export for the generated zip path.
3. Add persisted experiment history using Room or a simple JSON index.
4. Add a file list viewer for generated outputs.
5. Add optional requirements profiles in Gradle/Chaquopy.
6. Harden execution safety.

## Safety rules

- Do not add network permissions unless required.
- Do not request broad storage permissions.
- Keep all execution inside app-private experiment folders.
- Make dangerous features explicit and user-approved.
