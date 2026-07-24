package com.bjorn.claudepad

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pengelola Audio Streaming Dua Arah untuk CLAUDEPAD v3.6
 * Menghubungkan ke TCP socket port 8767 (di-forward via adb reverse tcp:8767 tcp:8767).
 * - Menggunakan AudioRecord untuk menangkap mic HP dan mengirimkannya ke PC.
 * - Menggunakan AudioTrack untuk menerima audio dari PC dan memutarnya di speaker HP.
 */
object AudioStreamManager {
    private const val TAG = "AudioStreamManager"
    private const val PORT = 8767
    private const val SAMPLE_RATE = 44100
    private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
    private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_STEREO
    private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

    private val isRunning = AtomicBoolean(false)
    private var scope: CoroutineScope? = null
    private var socket: Socket? = null

    var onStatusChanged: ((Boolean, String) -> Unit)? = null

    @Volatile
    var speakerEnabled: Boolean = true
        private set

    val active: Boolean
        get() = isRunning.get()

    fun setSpeakerEnabled(enabled: Boolean) {
        speakerEnabled = enabled
        Log.i(TAG, "Speaker ${
            if (enabled) "diaktifkan" else "dimatikan"
        }")
    }

    fun start() {
        if (isRunning.get()) return
        isRunning.set(true)
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        scope?.launch {
            try {
                onStatusChanged?.invoke(false, "Menghubungkan audio ke port $PORT...")
                val sock = Socket()
                sock.connect(InetSocketAddress("127.0.0.1", PORT), 3000)
                socket = sock
                onStatusChanged?.invoke(true, "Audio Terhubung")
                Log.i(TAG, "Terhubung ke audio server port $PORT")

                // Jalankan coroutine terpisah untuk Mic -> PC dan PC -> Speaker
                launch { recordAndSendLoop(sock) }
                launch { receiveAndPlayLoop(sock) }

            } catch (e: Exception) {
                Log.e(TAG, "Gagal terhubung ke audio server: ${e.message}", e)
                onStatusChanged?.invoke(false, "Audio Gagal: ${e.localizedMessage}")
                stop()
            }
        }
    }

    fun stop() {
        if (!isRunning.get() && socket == null) return
        isRunning.set(false)
        try {
            socket?.close()
        } catch (e: Exception) {
            // ignore
        }
        socket = null
        scope?.cancel()
        scope = null
        onStatusChanged?.invoke(false, "Audio Terputus")
        Log.i(TAG, "AudioStreamManager dihentikan")
    }

    private suspend fun recordAndSendLoop(sock: Socket) {
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING)
        val bufferSize = maxOf(minBufferSize, 2048)
        
        var audioRecord: AudioRecord? = null
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_IN,
                ENCODING,
                bufferSize
            )

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord gagal diinisialisasi")
                return
            }

            audioRecord.startRecording()
            Log.i(TAG, "AudioRecord mulai merekam")

            val data = ByteArray(bufferSize)
            val outputStream = sock.getOutputStream()

            while (isRunning.get() && !sock.isClosed) {
                val read = audioRecord.read(data, 0, data.size)
                if (read > 0) {
                    try {
                        outputStream.write(data, 0, read)
                        outputStream.flush()
                    } catch (e: Exception) {
                        Log.e(TAG, "Gagal mengirim data audio ke PC: ${e.message}")
                        break
                    }
                }
                yield()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pada recordAndSendLoop: ${e.message}", e)
        } finally {
            try {
                audioRecord?.stop()
                audioRecord?.release()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private suspend fun receiveAndPlayLoop(sock: Socket) {
        val minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, ENCODING)
        val bufferSize = maxOf(minBufferSize, 4096)

        var audioTrack: AudioTrack? = null
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(ENCODING)
                .setChannelMask(CHANNEL_OUT)
                .build()

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack.play()
            Log.i(TAG, "AudioTrack mulai memutar")

            val inputStream = sock.getInputStream()
            val buffer = ByteArray(4096)

            while (isRunning.get() && !sock.isClosed) {
                val read = withContext(Dispatchers.IO) {
                    inputStream.read(buffer)
                }
                if (read <= 0) break

                if (speakerEnabled) {
                    val written = audioTrack.write(buffer, 0, read)
                    if (written < 0) {
                        Log.w(TAG, "AudioTrack write error: $written")
                    }
                }
                yield()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pada receiveAndPlayLoop: ${e.message}", e)
        } finally {
            try {
                audioTrack?.stop()
                audioTrack?.release()
            } catch (e: Exception) {
                // ignore
            }
        }
    }
}
