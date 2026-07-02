package com.warden.cortex

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var resultText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        resultText = findViewById(R.id.resultText)
        checkShizukuStatus()
    }

    private fun checkShizukuStatus() {
        try {
            if (!Shizuku.pingBinder()) {
                statusText.text = "Shizuku مو شغال. افتح تطبيق Shizuku أول."
                return
            }

            val granted = Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (granted) {
                statusText.text = "✅ Shizuku متصل والصلاحية ممنوحة"
                runTestCommand()
            } else {
                statusText.text = "⚠️ Shizuku شغال، بس الصلاحية مو ممنوحة بعد"
                Shizuku.requestPermission(1)
            }
        } catch (e: Exception) {
            statusText.text = "خطأ: ${e.message}"
        }
    }

    private fun runTestCommand() {
        try {
            val process = Shizuku.newProcess(
                arrayOf("sh", "-c", "dumpsys battery | grep level"),
                null, null
            )
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.waitFor()
            resultText.text = "نتيجة الأمر:\n$output"
        } catch (e: Exception) {
            resultText.text = "فشل تنفيذ الأمر: ${e.message}"
        }
    }
}
