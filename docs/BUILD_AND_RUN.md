# PocketLab v0.1 — Build and Run

PocketLab is a starter Android app for running AI-generated Python tools locally on an Android phone without using Termux directly.

## What it does now

- Paste a Python script into the app.
- Tap **Run**.
- The script runs through embedded Python using Chaquopy.
- PocketLab captures stdout, stderr, exit code, runtime, generated files, and a zipped result folder.
- Tap **Copy Report** to copy a clean ChatGPT/Claude handoff block.

## Build requirement

Use Android Studio on your computer for the first build. This package does not include a Gradle wrapper jar.

1. Open the `PocketLab` folder in Android Studio.
2. Let Gradle sync.
3. Connect your Android phone with USB debugging, or use an emulator.
4. Run the `app` configuration.

## Important notes

- This is the no-Termux version. It embeds Python inside the Android app.
- It is intentionally not a full local coding agent yet.
- It is meant for standard-library Python tools first.
- Scripts write into the run folder by default.
- PocketLab sets `POCKETLAB_RUN_DIR` and `HOME` to the run folder.

## Where outputs go

Inside app-private storage:

```text
/data/data/haus.harrow.pocketlab/files/experiments/<timestamp>_<experiment_name>/
```

Each run folder contains:

```text
tool.py
run_report.json
chatgpt_summary.txt
<experiment>_results.zip
any files your script generated
```

## Next build steps

1. Add import `.py` from Android file picker.
2. Add export/share zip through Android share sheet.
3. Add experiment history list.
4. Add simple package profiles: `stdlib`, `numpy`, `pillow`, `matplotlib`.
5. Add local tiny-model summary panel later.
