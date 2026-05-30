package com.weishao.webdav.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object LogManager {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs
    
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun init(context: Context) {
        logFile = File(context.filesDir, "app_logs.txt")
        if (logFile?.exists() == true) {
            _logs.value = logFile?.readLines()?.takeLast(500) ?: emptyList()
        }
    }

    fun log(message: String) {
        val timestamp = dateFormat.format(Date())
        val entry = "[$timestamp] $message"
        _logs.value = (_logs.value + entry).takeLast(500)
        
        try {
            logFile?.appendText("$entry\n")
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun clear() {
        _logs.value = emptyList()
        logFile?.delete()
    }
}
