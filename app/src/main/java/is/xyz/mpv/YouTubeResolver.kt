package `is`.xyz.mpv

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.localization.Localization
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request as OkHttpRequest
import java.util.concurrent.TimeUnit

/**
 * Resolves a YouTube URL into direct video + audio stream URLs that mpv can play.
 * Uses NewPipeExtractor. A/V sync is maintained by passing separate video and
 * audio URLs to mpv via the `audio-file` option.
 */
object YouTubeResolver {

    private const val TAG = "YouTubeResolver"

    data class ResolvedStream(
        val videoUrl: String,
        val audioUrl: String?,
        val title: String,
        val resolution: String
    )

    suspend fun resolve(youtubeUrl: String, baseResolution: String): ResolvedStream? =
        withContext(Dispatchers.IO) {
            try {
                ensureInitialized()
                val streamUrl = normalizeUrl(youtubeUrl)
                Log.i(TAG, "Resolving: $streamUrl at $baseResolution")

                val extractor = ServiceList.YouTube.getStreamExtractor(streamUrl)
                extractor.fetchPage()

                val videoItem = pickVideoStream(extractor, baseResolution)
                if (videoItem == null) {
                    Log.e(TAG, "No suitable video stream found")
                    return@withContext null
                }

                val videoUrl: String = videoItem.content ?: videoItem.url ?: ""
                val audioItem = pickAudioStream(extractor)
                val audioUrl: String? = audioItem?.content ?: audioItem?.url

                val resolved = ResolvedStream(
                    videoUrl = videoUrl,
                    audioUrl = if (videoItem.isVideoOnly) audioUrl else null,
                    title = extractor.name ?: "YouTube",
                    resolution = videoItem.height.toString() + "p"
                )
                Log.i(TAG, "Resolved: " + resolved.resolution)
                resolved
            } catch (e: Exception) {
                Log.e(TAG, "YouTube extraction failed", e)
                null
            }
        }

    fun isYouTubeUrl(url: String?): Boolean {
        if (url == null) return false
        val u = url.trim()
        if (u.isEmpty()) return false
        return u.contains("youtube.com/watch") ||
               u.contains("youtu.be/") ||
               u.contains("youtube.com/shorts/") ||
               u.contains("m.youtube.com/") ||
               u.contains("music.youtube.com/watch")
    }

    private var initialized = false

    @Synchronized
    private fun ensureInitialized() {
        if (initialized) return
        NewPipe.init(OkHttpDownloader(), Localization("en", "US"))
        initialized = true
    }

    private fun normalizeUrl(url: String): String {
        val u = url.trim()
        if (u.startsWith("https://youtu.be/") || u.startsWith("http://youtu.be/")) {
            val id = u.substringAfter("youtu.be/").substringBefore("?").substringBefore("/")
            return "https://www.youtube.com/watch?v=" + id
        }
        if (u.contains("youtube.com/shorts/")) {
            val id = u.substringAfter("youtube.com/shorts/").substringBefore("?").substringBefore("/")
            return "https://www.youtube.com/watch?v=" + id
        }
        return u
    }

    private fun pickVideoStream(
        extractor: StreamExtractor,
        target: String
    ): org.schabi.newpipe.extractor.stream.VideoStream? {
        val all: List<org.schabi.newpipe.extractor.stream.VideoStream> =
            extractor.videoStreams ?: emptyList()
        val videoOnly = all.filter { it.isVideoOnly }

        if (videoOnly.isEmpty()) {
            return all.filter { !it.isVideoOnly }
                .sortedByDescending { it.height }
                .firstOrNull()
        }

        val targetHeight = heightFromString(target)
        val chosen = videoOnly
            .filter { it.height <= targetHeight }
            .maxByOrNull { it.height }
        return chosen ?: videoOnly.minByOrNull { it.height }
    }

    private fun pickAudioStream(
        extractor: StreamExtractor
    ): org.schabi.newpipe.extractor.stream.AudioStream? {
        val all: List<org.schabi.newpipe.extractor.stream.AudioStream> =
            extractor.audioStreams ?: emptyList()
        return all.filter { it.bitrate > 0 }.maxByOrNull { it.bitrate }
    }

    private fun heightFromString(s: String): Int = when (s) {
        "144p" -> 144
        "240p" -> 240
        "360p" -> 360
        "480p" -> 480
        "720p" -> 720
        "1080p" -> 1080
        else -> 480
    }
}

/**
 * Minimal OkHttp-backed Downloader for NewPipeExtractor.
 * Uses only verified getter methods: url(), httpMethod(), dataToSend(), headers().
 */
private class OkHttpDownloader : Downloader() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override fun execute(request: Request): Response {
        val data: ByteArray? = request.dataToSend()
        val body = data?.toRequestBody("application/octet-stream".toMediaTypeOrNull())
        val builder = OkHttpRequest.Builder()
            .url(request.url())
            .method(request.httpMethod(), body)

        val headers: Map<String, List<String>> = request.headers()
        for ((key, values) in headers) {
            for (v in values) {
                builder.header(key, v)
            }
        }

        builder.header("User-Agent",
            "Mozilla/5.0 (Linux; Android 13; Reno 11F) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
        builder.header("Accept-Language", "en-US,en;q=0.9")

        client.newCall(builder.build()).execute().use { resp ->
            val respBody: String = resp.body?.string() ?: ""
            val respHeaders: Map<String, List<String>> = resp.headers.toMultimap()
            return Response(
                resp.code,
                resp.message,
                respHeaders,
                respBody,
                resp.request.url.toString()
            )
        }
    }
}
