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

    private fun runShizukuCommand(command: String): String {
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null
            ) as Process

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.waitFor()
            output
        } catch (e: Exception) {
            "فشل: ${e.message}"
        }
    }

    private fun runTestCommand() {
        val output = runShizukuCommand("dumpsys battery | grep level")
        resultText.text = "نتيجة الأمر:\n$output"
    }
}
