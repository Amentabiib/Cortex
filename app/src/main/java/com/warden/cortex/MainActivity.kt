package com.warden.cortex

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var resultText: TextView
    private lateinit var modelText: TextView
    private lateinit var decisionText: TextView
    private lateinit var askButton: Button
    private lateinit var exportLogButton: Button
    private lateinit var logText: TextView

    private var modelReady = false
    private val logEntries = mutableListOf<String>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    private val allowedActions = mapOf(
        "do_nothing" to null,
        "check_battery" to "dumpsys battery | grep level",
        "enable_high_refresh" to "settings put system peak_refresh_rate 90",
        "enable_normal_refresh" to "settings put system peak_refresh_rate 60"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        resultText = findViewById(R.id.resultText)
        modelText = findViewById(R.id.modelText)
        decisionText = findViewById(R.id.decisionText)
        askButton = findViewById(R.id.askButton)
        exportLogButton = findViewById(R.id.exportLogButton)
        logText = findViewById(R.id.logText)

        askButton.isEnabled = false
        askButton.setOnClickListener { runDecisionCycle() }
        exportLogButton.setOnClickListener { exportLog() }

        addLog("SYSTEM", "التطبيق بدأ التشغيل")
        checkShizukuStatus()
        loadLlamaModel()
    }

    private fun addLog(type: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val entry = "[$timestamp] [$type] $message"
        logEntries.add(entry)
        runOnUiThread {
            logText.text = logEntries.takeLast(5).joinToString("\n")
        }
    }

    private fun checkShizukuStatus() {
        try {
            if (!Shizuku.pingBinder()) {
                statusText.text = "Shizuku مو شغال. افتح تطبيق Shizuku أول."
                addLog("SHIZUKU", "غير متصل")
                return
            }
            val granted = Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (granted) {
                statusText.text = "✅ Shizuku متصل والصلاحية ممنوحة"
                addLog("SHIZUKU", "متصل والصلاحية ممنوحة")
            } else {
                statusText.text = "⚠️ Shizuku شغال، بس الصلاحية مو ممنوحة بعد"
                addLog("SHIZUKU", "بانتظار منح الصلاحية")
                Shizuku.requestPermission(1)
            }
        } catch (e: Exception) {
            statusText.text = "خطأ: ${e.message}"
            addLog("ERROR", "Shizuku check failed: ${e.message}")
        }
    }

    private fun runShizukuCommand(command: String): String {
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.waitFor()
            output
        } catch (e: Exception) {
            "فشل: ${e.message}"
        }
    }

    private fun loadLlamaModel() {
        Thread {
            try {
                val modelPath = "/storage/emulated/0/Download/qwen2.5-0.5b-instruct-q4_k_m.gguf"
                val file = File(modelPath)

                runOnUiThread { modelText.text = "جاري تحميل الموديل..." }
                addLog("MODEL", "بدء تحميل الموديل")

                if (!file.exists()) {
                    runOnUiThread { modelText.text = "❌ الموديل مو موجود" }
                    addLog("ERROR", "ملف الموديل غير موجود")
                    return@Thread
                }

                val startLoad = System.currentTimeMillis()
                val loaded = LlamaBridge.loadModel(modelPath)
                val loadTime = (System.currentTimeMillis() - startLoad) / 1000

                runOnUiThread {
                    if (loaded) {
                        modelReady = true
                        modelText.text = "✅ الموديل جاهز"
                        askButton.isEnabled = true
                        addLog("MODEL", "تحمّل بنجاح خلال ${loadTime}ث")
                    } else {
                        modelText.text = "❌ فشل تحميل الموديل"
                        addLog("ERROR", "فشل تحميل الموديل")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { modelText.text = "خطأ: ${e.message}" }
                addLog("ERROR", "Model load exception: ${e.message}")
            }
        }.start()
    }

    private fun runDecisionCycle() {
        askButton.isEnabled = false
        decisionText.text = "جاري جلب حالة الجهاز..."
        addLog("CYCLE", "بدء دورة قرار جديدة")

        Thread {
            val batteryOutput = runShizukuCommand("dumpsys battery | grep level")
            val batteryLevel = batteryOutput.trim().replace("level:", "").trim()
            addLog("SENSE", "البطارية: $batteryLevel%")

            val actionsListStr = allowedActions.keys.joinToString(", ")

            val prompt = """
                Device state: battery level is $batteryLevel percent.
                Choose exactly one action from this list only: $actionsListStr
                Reply with ONLY valid JSON in this exact format, nothing else:
                {"action": "action_name", "reason": "short reason in English"}
            """.trimIndent()

            runOnUiThread { decisionText.text = "جاري التفكير..." }

            val startGen = System.currentTimeMillis()
            val rawResponse = LlamaBridge.generate(prompt)
            val genTime = (System.currentTimeMillis() - startGen) / 1000
            addLog("MODEL_RAW", "رد خام (${genTime}ث): $rawResponse")

            runOnUiThread {
                handleModelResponse(rawResponse)
            }
        }.start()
    }

    private fun handleModelResponse(rawResponse: String) {
        try {
            val jsonStart = rawResponse.indexOf('{')
            val jsonEnd = rawResponse.lastIndexOf('}')
            if (jsonStart == -1 || jsonEnd == -1 || jsonEnd < jsonStart) {
                decisionText.text = "❌ رد غير صالح من الموديل:\n$rawResponse"
                addLog("ERROR", "رد غير صالح (بدون JSON)")
                askButton.isEnabled = true
                return
            }

            val jsonStr = rawResponse.substring(jsonStart, jsonEnd + 1)
            val json = JSONObject(jsonStr)
            val action = json.optString("action", "")
            val reason = json.optString("reason", "")

            if (!allowedActions.containsKey(action)) {
                decisionText.text = "❌ الموديل اقترح أمر غير مسموح: $action"
                addLog("REJECTED", "أمر غير مسموح: $action")
                askButton.isEnabled = true
                return
            }

            decisionText.text = "الاقتراح: $action\nالسبب: $reason"
            addLog("DECISION", "action=$action reason=$reason")
            showApprovalDialog(action, reason)

        } catch (e: Exception) {
            decisionText.text = "❌ فشل تحليل رد الموديل: ${e.message}"
            addLog("ERROR", "فشل تحليل JSON: ${e.message}")
            askButton.isEnabled = true
        }
    }

    private fun showApprovalDialog(action: String, reason: String) {
        AlertDialog.Builder(this)
            .setTitle("موافقة على تنفيذ")
            .setMessage("الأمر المقترح: $action\nالسبب: $reason\n\nهل توافق على التنفيذ؟")
            .setPositiveButton("موافق") { _, _ ->
                addLog("APPROVAL", "تمت الموافقة على: $action")
                executeAction(action)
            }
            .setNegativeButton("رفض") { _, _ ->
                resultText.text = "تم الرفض من طرفك"
                addLog("APPROVAL", "تم رفض: $action")
                askButton.isEnabled = true
            }
            .setCancelable(false)
            .show()
    }

    private fun executeAction(action: String) {
        val command = allowedActions[action]
        if (command == null) {
            resultText.text = "✅ تم تنفيذ: $action (لا شي فعلي)"
            addLog("EXECUTE", "$action = بدون أمر فعلي")
            askButton.isEnabled = true
            return
        }

        Thread {
            val output = runShizukuCommand(command)
            addLog("EXECUTE", "$action -> $output")
            runOnUiThread {
                resultText.text = "✅ تم تنفيذ: $action\nالنتيجة: $output"
                askButton.isEnabled = true
            }
        }.start()
    }

    private fun exportLog() {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val logFile = File(getExternalFilesDir(null), "cortex_log_$timestamp.txt")
            val writer = FileWriter(logFile)
            writer.write(logEntries.joinToString("\n"))
            writer.close()

            resultText.text = "✅ تم حفظ السجل بـ:\n${logFile.absolutePath}"
            addLog("EXPORT", "تم تصدير السجل إلى ${logFile.name}")
        } catch (e: Exception) {
            resultText.text = "❌ فشل تصدير السجل: ${e.message}"
        }
    }
}
