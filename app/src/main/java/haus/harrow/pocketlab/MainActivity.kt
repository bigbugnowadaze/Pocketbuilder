package haus.harrow.pocketlab

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PocketLabApp() }
    }
}

data class RunResult(
    val experiment: String = "",
    val exitCode: Int = -1,
    val runtime: String = "",
    val stdout: String = "",
    val stderr: String = "",
    val zipPath: String = "",
    val summary: String = ""
)

@Composable
fun PocketLabApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var experimentName by remember { mutableStateOf("compression_test") }
    var script by remember { mutableStateOf(sampleScript) }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<RunResult?>(null) }
    var status by remember { mutableStateOf("Paste a Python tool, hit Run, copy the report back to ChatGPT/Claude.") }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0B0D))
                .padding(16.dp)
        ) {
            Text("PocketLab", fontSize = 28.sp, color = Color.White)
            Text("Local Python experiment runner", color = Color(0xFFBDBDBD))
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = experimentName,
                onValueChange = { experimentName = it },
                label = { Text("Experiment name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = script,
                onValueChange = { script = it },
                label = { Text("Python script") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = !running && script.isNotBlank(),
                    onClick = {
                        running = true
                        status = "Running Python locally..."
                        result = null
                        scope.launch {
                            val r = runPocketLabScript(context, script, experimentName)
                            result = r
                            status = if (r.exitCode == 0) "Run complete." else "Run failed. Copy the report back to ChatGPT/Claude."
                            running = false
                        }
                    }
                ) { Text(if (running) "Running..." else "Run") }

                Spacer(Modifier.width(8.dp))

                Button(
                    enabled = result != null,
                    onClick = {
                        result?.let { copyToClipboard(context, "PocketLab run report", it.summary) }
                        status = "Copied ChatGPT/Claude summary to clipboard."
                    }
                ) { Text("Copy Report") }
            }

            Spacer(Modifier.height(8.dp))
            Text(status, color = Color(0xFFBDBDBD), fontSize = 13.sp)

            result?.let { RunResultCard(it) }
        }
    }
}

@Composable
fun RunResultCard(result: RunResult) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp)
            .padding(top = 10.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Result: ${result.experiment}", fontSize = 18.sp)
            Text("Exit code: ${result.exitCode}   Runtime: ${result.runtime}s")
            if (result.zipPath.isNotBlank()) Text("Zip: ${result.zipPath}", fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            Text("STDOUT", color = Color(0xFF9E9E9E), fontSize = 12.sp)
            Text(result.stdout.ifBlank { "(empty)" }, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            Text("STDERR", color = Color(0xFF9E9E9E), fontSize = 12.sp)
            Text(result.stderr.ifBlank { "(empty)" }, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
    }
}

suspend fun runPocketLabScript(context: Context, script: String, experimentName: String): RunResult =
    withContext(Dispatchers.IO) {
        try {
            val py = Python.getInstance()
            val module = py.getModule("pocketlab_runner")
            val jsonText = module.callAttr("run_script", script, context.filesDir.absolutePath, experimentName).toString()
            val obj = JSONObject(jsonText)
            // The Python function also writes chatgpt_summary.txt; recreate a reliable copy block on Kotlin side.
            val stdout = obj.optString("stdout")
            val stderr = obj.optString("stderr")
            val files = obj.optJSONArray("files")
            val fileLines = buildString {
                if (files != null) {
                    for (i in 0 until files.length()) {
                        val f = files.getJSONObject(i)
                        append("- ${f.optString("path")} (${f.optLong("bytes")} bytes)\n")
                    }
                }
            }.ifBlank { "- none\n" }
            val copySummary = """
I ran the Python tool locally in PocketLab on Android.

Experiment: ${obj.optString("experiment")}
Exit code: ${obj.optInt("exit_code")}
Runtime seconds: ${obj.optDouble("runtime_seconds")}
Run folder: ${obj.optString("run_dir")}
Zip path: ${obj.optString("zip_path")}

Generated files:
$fileLines
STDOUT:
```text
$stdout
```

STDERR:
```text
$stderr
```

Please analyze the result, explain what it means, and produce the next improved version of the tool if needed.
""".trimIndent()
            RunResult(
                experiment = obj.optString("experiment"),
                exitCode = obj.optInt("exit_code"),
                runtime = obj.optDouble("runtime_seconds").toString(),
                stdout = stdout,
                stderr = stderr,
                zipPath = obj.optString("zip_path"),
                summary = copySummary
            )
        } catch (t: Throwable) {
            val msg = t.stackTraceToString()
            RunResult(
                experiment = experimentName,
                exitCode = 1,
                runtime = "0",
                stderr = msg,
                summary = "PocketLab itself failed before running the script.\n\n```text\n$msg\n```"
            )
        }
    }

fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

val sampleScript = """
import json, math, os, time

run_dir = os.environ.get('POCKETLAB_RUN_DIR', '.')
print('PocketLab Python is running.')
print('Run dir:', run_dir)

values = [math.sin(i / 10) for i in range(100)]
summary = {
    'count': len(values),
    'min': min(values),
    'max': max(values),
    'mean': sum(values) / len(values),
}

with open('results.json', 'w', encoding='utf-8') as f:
    json.dump(summary, f, indent=2)

print('Summary:', summary)
""".trimIndent()
