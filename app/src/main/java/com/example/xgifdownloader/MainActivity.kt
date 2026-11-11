package com.example.xgifdownloader

import android.app.Activity
import android.content.ContentValues
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.media.MediaMetadataRetriever
import android.graphics.Bitmap
import android.graphics.Matrix
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.squareup.gifencoder.GifEncoder
import com.squareup.gifencoder.ImageOptions
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var linkInput: EditText
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var downloadButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(36), dp(24), dp(24))
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        root.addView(TextView(this).apply {
            text = "X GIF Downloader"
            textSize = 25f
            setTextColor(0xFF172033.toInt())
        })
        root.addView(TextView(this).apply {
            text = "Pega el enlace del post de X que contiene el GIF. Se guardará en la carpeta Descargas."
            textSize = 15f
            setTextColor(0xFF526078.toInt())
            setPadding(0, dp(12), 0, dp(20))
        })
        linkInput = EditText(this).apply {
            hint = "https://x.com/usuario/status/…"
            singleLine = true
            textSize = 14f
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        root.addView(linkInput, LinearLayout.LayoutParams(-1, dp(56)))
        downloadButton = Button(this).apply {
            text = "Descargar GIF"
            setOnClickListener { startDownload() }
        }
        root.addView(downloadButton, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(16) })
        progress = ProgressBar(this).apply { visibility = android.view.View.GONE }
        root.addView(progress, LinearLayout.LayoutParams(-2, dp(44)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(12) })
        status = TextView(this).apply {
            text = ""
            textSize = 14f
            setTextColor(0xFF263247.toInt())
            setPadding(0, dp(12), 0, 0)
        }
        root.addView(status)
        setContentView(root)
    }

    private fun startDownload() {
        val input = linkInput.text.toString().trim()
        val id = Regex("(?:twitter\\.com|x\\.com)/[^/?]+/status/(\\d+)", RegexOption.IGNORE_CASE)
            .find(input)?.groupValues?.getOrNull(1)
        if (id == null) {
            status.text = "Enlace no válido. Pega la URL de un post de X."
            return
        }
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(linkInput.windowToken, 0)
        setBusy(true, "Buscando el GIF en X…")
        executor.execute {
            try {
                val mediaUrl = resolveMedia(id)
                runOnUiThread { status.text = "Descargando y convirtiendo a GIF…" }
                val video = downloadToTemp(mediaUrl)
                val gif = convertToGif(video)
                val saved = saveToDownloads(gif)
                video.delete(); gif.delete()
                runOnUiThread {
                    setBusy(false, "GIF guardado en Descargas: $saved")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    setBusy(false, "No se pudo descargar: ${e.message ?: "error desconocido"}")
                }
            }
        }
    }

    private fun resolveMedia(id: String): String {
        val endpoint = URL("https://api.vxtwitter.com/Twitter/status/$id")
        val connection = endpoint.openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.setRequestProperty("User-Agent", "XGifDownloader/1.0")
        try {
            if (connection.responseCode !in 200..299) throw IllegalStateException("No se pudo consultar el post (HTTP ${connection.responseCode}).")
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val urls = json.optJSONArray("mediaURLs")
            if (urls != null) {
                for (i in 0 until urls.length()) {
                    val candidate = urls.optString(i)
                    if (candidate.startsWith("https://") || candidate.startsWith("http://")) return candidate
                }
            }
            throw IllegalStateException("El post no contiene un GIF o vídeo compatible, o no es público.")
        } finally { connection.disconnect() }
    }

    private fun downloadToTemp(mediaUrl: String): File {
        val file = File(cacheDir, "source_${System.currentTimeMillis()}.mp4")
        val connection = URL(mediaUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 20000
        connection.readTimeout = 30000
        connection.setRequestProperty("User-Agent", "Mozilla/5.0")
        try {
            if (connection.responseCode !in 200..299) throw IllegalStateException("Error al descargar el vídeo (HTTP ${connection.responseCode}).")
            BufferedInputStream(connection.inputStream).use { input -> FileOutputStream(file).use { output -> input.copyTo(output) } }
        } finally { connection.disconnect() }
        return file
    }

    private fun convertToGif(video: File): File {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(video.absolutePath)
        try {
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: throw IllegalStateException("No se pudo leer el vídeo.")
            if (durationMs <= 0) throw IllegalStateException("El vídeo está vacío.")
            val duration = minOf(durationMs, 8000L)
            val first = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: throw IllegalStateException("No se pudieron extraer fotogramas.")
            val scale = minOf(1f, 480f / max(first.width, first.height))
            val width = max(1, (first.width * scale).roundToInt())
            val height = max(1, (first.height * scale).roundToInt())
            val output = File(cacheDir, "converted_${System.currentTimeMillis()}.gif")
            FileOutputStream(output).use { stream ->
                val encoder = GifEncoder(stream, width, height, 0)
                var time = 0L
                var frames = 0
                while (time < duration && frames < 80) {
                    val frame = if (time == 0L) first else retriever.getFrameAtTime(time * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                    if (frame != null) {
                        val scaled = if (frame.width == width && frame.height == height) frame
                            else Bitmap.createScaledBitmap(frame, width, height, true)
                        val options = ImageOptions().apply { setDelay(100, TimeUnit.MILLISECONDS) }
                        encoder.addImage(bitmapPixels(scaled), options)
                        if (scaled !== frame) scaled.recycle()
                        if (frame !== first) frame.recycle()
                        frames++
                    }
                    time += 100
                }
                encoder.finishEncoding()
                if (first.isRecycled.not()) first.recycle()
                if (frames == 0) throw IllegalStateException("No se pudieron convertir los fotogramas.")
            }
            return output
        } finally { retriever.release() }
    }

    private fun saveToDownloads(gif: File): String {
        val displayName = "x_gif_${System.currentTimeMillis()}.gif"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, "image/gif")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Android no permitió crear el archivo en Descargas.")
        contentResolver.openOutputStream(uri)?.use { output -> gif.inputStream().use { it.copyTo(output) } }
            ?: throw IllegalStateException("No se pudo escribir el GIF.")
        if (Build.VERSION.SDK_INT >= 29) {
            val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            contentResolver.update(uri, done, null, null)
        }
        return displayName
    }

    private fun bitmapPixels(bitmap: Bitmap): Array<IntArray> {
        val width = bitmap.width
        val height = bitmap.height
        val flat = IntArray(width * height)
        bitmap.getPixels(flat, 0, width, 0, 0, width, height)
        return Array(width) { x -> IntArray(height) { y -> flat[y * width + x] } }
    }

    private fun setBusy(busy: Boolean, message: String) {
        downloadButton.isEnabled = !busy
        progress.visibility = if (busy) android.view.View.VISIBLE else android.view.View.GONE
        status.text = message
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
