import contextlib
import io
import json
import os
import runpy
import sys
import time
import traceback
import zipfile
from pathlib import Path


def _safe_name(name: str) -> str:
    cleaned = ''.join(ch if ch.isalnum() or ch in ('-', '_') else '_' for ch in name.strip())
    return cleaned[:64] or 'experiment'


def run_script(script_text: str, app_files_dir: str, experiment_name: str = 'experiment') -> str:
    """Run pasted Python code in an isolated experiment folder.

    Returns a JSON string so Kotlin can parse/display it easily.
    """
    started = time.time()
    stamp = time.strftime('%Y%m%d_%H%M%S')
    exp_name = f"{stamp}_{_safe_name(experiment_name)}"
    root = Path(app_files_dir) / 'experiments' / exp_name
    root.mkdir(parents=True, exist_ok=True)

    script_path = root / 'tool.py'
    script_path.write_text(script_text, encoding='utf-8')

    old_cwd = os.getcwd()
    old_argv = sys.argv[:]
    old_path = sys.path[:]
    stdout = io.StringIO()
    stderr = io.StringIO()
    exit_code = 0
    tb = None

    # Make generated files land in this run folder by default.
    os.environ['POCKETLAB_RUN_DIR'] = str(root)
    os.environ['HOME'] = str(root)

    try:
        os.chdir(root)
        sys.argv = [str(script_path)]
        sys.path.insert(0, str(root))
        with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
            runpy.run_path(str(script_path), run_name='__main__')
    except SystemExit as exc:
        code = exc.code
        if isinstance(code, int):
            exit_code = code
        elif code is None:
            exit_code = 0
        else:
            exit_code = 1
            stderr.write(str(code) + '\n')
    except BaseException:
        exit_code = 1
        tb = traceback.format_exc()
        stderr.write(tb)
    finally:
        os.chdir(old_cwd)
        sys.argv = old_argv
        sys.path = old_path

    runtime = time.time() - started
    files = []
    for path in sorted(root.rglob('*')):
        if path.is_file():
            rel = str(path.relative_to(root))
            files.append({'path': rel, 'bytes': path.stat().st_size})

    report = {
        'experiment': exp_name,
        'run_dir': str(root),
        'script_path': str(script_path),
        'exit_code': exit_code,
        'runtime_seconds': round(runtime, 4),
        'stdout': stdout.getvalue(),
        'stderr': stderr.getvalue(),
        'traceback': tb,
        'files': files,
    }

    (root / 'run_report.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    (root / 'chatgpt_summary.txt').write_text(make_chatgpt_summary(report), encoding='utf-8')

    zip_path = root / f'{exp_name}_results.zip'
    with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_DEFLATED) as zf:
        for path in root.rglob('*'):
            if path == zip_path:
                continue
            if path.is_file():
                zf.write(path, path.relative_to(root))
    report['zip_path'] = str(zip_path)
    report['files'].append({'path': zip_path.name, 'bytes': zip_path.stat().st_size})
    (root / 'run_report.json').write_text(json.dumps(report, indent=2), encoding='utf-8')

    return json.dumps(report)


def make_chatgpt_summary(report: dict) -> str:
    stdout = report.get('stdout') or ''
    stderr = report.get('stderr') or ''
    max_chars = 7000
    def clip(s):
        if len(s) <= max_chars:
            return s
        return s[:max_chars] + '\n...[truncated by PocketLab]...'

    files = '\n'.join(f"- {f['path']} ({f['bytes']} bytes)" for f in report.get('files', [])) or '- none'
    return f"""I ran the Python tool locally in PocketLab on Android.

Experiment: {report.get('experiment')}
Exit code: {report.get('exit_code')}
Runtime seconds: {report.get('runtime_seconds')}
Run folder: {report.get('run_dir')}

Generated files:
{files}

STDOUT:
```text
{clip(stdout)}
```

STDERR:
```text
{clip(stderr)}
```

Please analyze the result, explain what it means, and produce the next improved version of the tool if needed.
"""
