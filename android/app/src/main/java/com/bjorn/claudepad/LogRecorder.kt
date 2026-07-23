package com.bjorn.claudepad

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Structured session logger untuk APK (JSONL format).
 * Mirip server/log_recorder.py — mencatat semua I/O untuk diagnostic.
 *
 * File disimpan di ctx.filesDir/logs/session_YYYY-MM-DD_HHMMSS.jsonl.
 * Auto-rotate: max 5MB/file, simpan 10 file terakhir.
 */
object LogRecorder {
    private const val MAX_FILE_SIZE = 5 * 1024 * 1024L  // 5 MB
    private const val MAX_FILES = 10

    private val queue = ConcurrentLinkedQueue<String>()
    private val running = AtomicBoolean(false)
    @Volatile private var currentFile: File? = null
    @Volatile private var currentSize = 0L

    private fun logDir(ctx: Context): File {
        val dir = File(ctx.filesDir, "logs")
        dir.mkdirs()
        return dir
    }

    fun start(ctx: Context) {
        if (running.getAndSet(true)) return
        Thread {
            while (running.get()) {
                val entry = queue.poll()
                if (entry != null) {
                    appendToFile(entry)
                } else {
                    Thread.sleep(500)
                }
            }
        }.start()
    }

    fun stop() {
        running.set(false)
    }

    fun log(ctx: Context, direction: String, cmdType: String,
            payloadSummary: String, peer: String = "") {
        val entry = JSONObject().apply {
            put("ts", System.currentTimeMillis() / 1000.0)
            put("dir", direction)
            put("cmd", cmdType)
            put("payload", payloadSummary.take(200))
            put("peer", peer)
        }
        queue.offer(entry.toString())
        if (!running.get()) start(ctx)
    }

    private fun appendToFile(json: String) {
        val f = currentFile
        if (f == null || !f.exists() || currentSize > MAX_FILE_SIZE) {
            rotateIfNeeded()
        }
        try {
            currentFile?.appendText(json + "\n")
            currentSize += json.length + 1
        } catch (_: Exception) { }
    }

    private fun rotateIfNeeded() {
        val ctx = App.instance ?: return
        val dir = logDir(ctx)
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "session_$stamp.jsonl")
        currentFile = file
        currentSize = if (file.exists()) file.length() else 0L
    }

    fun recentEntries(ctx: Context, n: Int = 50): List<String> {
        val dir = logDir(ctx)
        val files = dir.listFiles { f -> f.extension == "jsonl" }
            ?.sortedByDescending { it.lastModified() }
            ?: return emptyList()
        if (files.isEmpty()) return emptyList()
        val entries = mutableListOf<String>()
        try {
            files[0].readLines().forEach { line ->
                if (line.isNotBlank()) entries.add(line)
            }
        } catch (_: Exception) { }
        return entries.takeLast(n)
    }

    fun exportFile(ctx: Context): File? {
        val dir = logDir(ctx)
        return dir.listFiles { f -> f.extension == "jsonl" }
            ?.maxByOrNull { it.lastModified() }
    }

    fun clear(ctx: Context) {
        val dir = logDir(ctx)
        dir.listFiles { f -> f.extension == "jsonl" }?.forEach { it.delete() }
        currentFile = null
        currentSize = 0L
    }
}
