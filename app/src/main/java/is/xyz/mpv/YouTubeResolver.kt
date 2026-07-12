package `is`.xyz.mpv

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.localization.Localization
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request as OkHttpRequest
import java.util.concurrent.TimeUnit

/**
 * Resolves a YouTube URL into direct video + audio stream URLs that mpv can play.
 *
 * Uses NewPipeExtractor to query YouTube's player response and select the best
 * itag matching the user's preferred "base resolution" (saves bandwidth by
 * fetching 480p instead of 1080p, then letting Anime4K upscale on-device).
 *
 * A/V sync is maintained by returning separate URLs and letting the caller
 * set `audio-file=<audioUrl>` on mpv before `loadfile <videoUrl>`. mpv's
 * internal demuxer syncs the two streams seamlessly.
 */
object YouTubeResolver {

    private const val TAG = "YouTubeResolver"

    /** User-selectable base resolutions (the resolution fetched from YouTube). */
    val BASE_RESOLUTIONS = arrayOf("144p", "240p", "360p", "480p", "720p", "1080p")

    data class ResolvedStream(
        val videoUrl: String,
        val audioUrl: String?,   // null if the video stream is muxed (already contains audio)
        val title: String,
        val resolution: String
    )

    /**
     * Synchronous wrapper used during activity startup. Blocks the calling thread
     * until resolution completes. Must NOT be called on the main thread — caller
     * is responsible for offloading if needed.
     *
     * @param youtubeUrl a youtube.com/watch?v=… or youtu.be/… URL
     * @param baseResolution one of [BASE_RESOLUTIONS]
     */
    fun resolveBlocking(youtubeUrl: String, baseResolution: String): ResolvedStream? =
        runBlocking { resolve(youtubeUrl, baseResolution) }

    /**
     * Async variant for coroutine contexts.
     */
    suspend fun resolve(youtubeUrl: String, baseResolution: String): ResolvedStream? =
        withContext(Dispatchers.IO) {
            try {
                ensureInitialized()
                val service = ServiceList.YouTube
                val streamUrl = normalizeUrl(youtubeUrl)
                Log.i(TAG, "Resolving YouTube stream: $streamUrl at $baseResolution")

                val extractor = service.getStreamExtractor(streamUrl)
                extractor.fetchPage()

                val videoItem = pickVideoStream(extractor, baseResolution)
                if (videoItem == null) {
                    Log.e(TAG, "No suitable video stream found")
                    return@withContext null
                }

                val videoUrl = videoItem.content ?: videoItem.url
                val audioItem = pickAudioStream(extractor)
                val audioUrl = audioItem?.content ?: audioItem?.url

                val resolved = ResolvedStream(
                    videoUrl = videoUrl,
                    audioUrl = if (videoItem.isVideoOnly) audioUrl else null,
                    title = extractor.name ?: "YouTube",
                    resolution = "${videoItem.height}p"
                )
                Log.i(TAG, "Resolved: ${resolved.resolution} "
                        + "audio=${audioItem?.bitrate ?: 0}bps "
                        + "muxed=${!videoItem.isVideoOnly}")
                resolved
            } catch (e: Exception) {
                Log.e(TAG, "YouTube extraction failed", e)
                null
            }
        }

    /** True if the URL looks like a YouTube link. */
    fun isYouTubeUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val u = url.trim()
        return u.contains("youtube.com/watch") ||
               u.contains("youtu.be/") ||
               u.contains("youtube.com/shorts/") ||
               u.contains("m.youtube.com/") ||
               u.contains("music.youtube.com/watch")
    }

    // ------------------------------------------------------------------

    private var initialized = false
    @Synchronized
    private fun ensureInitialized() {
        if (initialized) return
        NewPipe.init(OkHttpDownloader(), Localization.fromLocalizationCode("en-US"))
        initialized = true
    }

    private fun normalizeUrl(url: String): String {
        val u = url.trim()
        return when {
            u.startsWith("https://youtu.be/") || u.startsWith("http://youtu.be/") -> {
                val id = u.substringAfter("youtu.be/").substringBefore("?").substringBefore("/")
                "https://www.youtube.com/watch?v=$id"
            }
            u.contains("youtube.com/shorts/") -> {
                val id = u.substringAfter("youtube.com/shorts/").substringBefore("?").substringBefore("/")
                "https://www.youtube.com/watch?v=$id"
            }
            else -> u
        }
    }

    private fun pickVideoStream(
        extractor: StreamExtractor,
        target: String
    ): org.schabi.newpipe.extractor.stream.VideoStream? {
        val all = extractor.videoStreams.orEmpty()
        val videoOnly = all.filter { it.isVideoOnly }

        if (videoOnly.isEmpty()) {
            // Fall back to muxed streams (lower quality, but always available)
            return all.filter { !it.isVideoOnly }
                .sortedByDescending { it.height }
                .firstOrNull()
        }

        val targetHeight = heightFromString(target)
        // Pick the closest stream not exceeding the target.
        return videoOnly
            .filter { it.height <= targetHeight }
            .maxByOrNull { it.height }
            ?: videoOnly.minByOrNull { it.height }  // target too low, give smallest available
    }

    private fun pickAudioStream(extractor: StreamExtractor): org.schabi.newpipe.extractor.stream.AudioStream? {
        return extractor.audioStreams
            .orEmpty()
            .filter { it.bitrate > 0 }
            .maxByOrNull { it.bitrate }
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
 * NewPipe requires a Downloader implementation to fetch pages.
 */
private class OkHttpDownloader : Downloader() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override fun execute(request: Request): Response {
        // NewPipeExtractor's Request uses dataToSend() (byte[]) not requestBody
        val body = request.dataToSend
            ?.toRequestBody("application/octet-stream".toMediaTypeOrNull())
        val builder = OkHttpRequest.Builder()
            .url(request.url)
            .method(request.getHttpMethod(), body)

        // request.getHeaders() returns Map<String, List<String>> — iterate all values
        request.getHeaders().forEach { (k, values) ->
            values.forEach { v -> builder.header(k, v) }
        }

        // Pretend to be a real browser to avoid YouTube blocking
        builder.header("User-Agent",
            "Mozilla/5.0 (Linux; Android 13; Reno 11F) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
        builder.header("Accept-Language", "en-US,en;q=0.9")

        client.newCall(builder.build()).execute().use { resp ->
            val respBody = resp.body?.string().orEmpty()
            return Response(
                resp.code,
                resp.message,
                resp.headers.toMultimap().mapValues { it.value.joinToString(", ") },
                respBody,
                resp.request.url.toString()
            )
        }
    }
}
