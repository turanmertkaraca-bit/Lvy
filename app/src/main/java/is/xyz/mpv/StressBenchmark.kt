package `is`.xyz.mpv

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Stress Bar benchmark for the AnimeStream player.
 *
 * Samples mpv's `estimated-vf-fps`, `frame-drop-count`, and
 * `vo-delayed-frame-count` properties every 500ms while a video plays.
 * Aggregates them into a single score (Green / Yellow / Red) and recommends
 * a shader preset + decoder combo appropriate for the user's hardware.
 *
 * The benchmark is "soft" — it just samples whatever is currently playing.
 * For a proper isolated test, the user should open the bundled test clip
 * (or any short anime clip) and start the benchmark from the settings menu.
 */
object StressBenchmark {

    private const val TAG = "StressBenchmark"
    private const val SAMPLE_INTERVAL_MS = 500L
    private const val MIN_SAMPLES = 20  // ~10 seconds at 500ms intervals

    enum class StressLevel(val label: String, val color: String) {
        GREEN("Comfortable", "#4CAF50"),
        YELLOW("Marginal", "#FFC107"),
        RED("Overloaded", "#F44336")
    }

    data class Result(
        val level: StressLevel,
        val avgVfFps: Double,
        val maxFrameDrops: Int,
        val maxVoDelay: Int,
        val recommendedPreset: ShaderPresets.Preset,
        val recommendedHwdec: String,
        val summary: String
    )

    private var job: Job? = null
    private var onComplete: ((Result) -> Unit)? = null

    // Sample buffers
    private val fpsSamples = mutableListOf<Double>()
    private var maxFrameDrops = 0
    private var maxVoDelay = 0
    private var startFrameDrops = 0

    /** True if benchmark is currently running. */
    fun isRunning(): Boolean = job?.isActive == true

    /**
     * Starts the benchmark. Calls [onResult] when complete (~10 seconds).
     * If already running, no-op.
     */
    fun start(context: Context, onResult: (Result) -> Unit) {
        if (isRunning()) {
            Log.w(TAG, "Benchmark already running")
            return
        }
        onComplete = onResult
        fpsSamples.clear()
        maxFrameDrops = 0
        maxVoDelay = 0
        startFrameDrops = MPVLib.getPropertyInt("frame-drop-count") ?: 0

        Log.i(TAG, "Starting benchmark (sampling every ${SAMPLE_INTERVAL_MS}ms for ~${MIN_SAMPLES} samples)")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        job = scope.launch {
            repeat(MIN_SAMPLES) {
                sampleOnce()
                delay(SAMPLE_INTERVAL_MS)
            }
            val result = computeResult()
            withContext(Dispatchers.Main) {
                onComplete?.invoke(result)
                onComplete = null
            }
            scope.cancel()  // self-terminate
        }
    }

    /** Cancels a running benchmark. */
    fun cancel() {
        job?.cancel()
        job = null
        onComplete = null
    }

    private fun sampleOnce() {
        val fps = MPVLib.getPropertyDouble("estimated-vf-fps") ?: -1.0
        val drops = MPVLib.getPropertyInt("frame-drop-count") ?: 0
        val voDelay = MPVLib.getPropertyInt("vo-delayed-frame-count") ?: 0

        if (fps > 0) fpsSamples.add(fps)
        maxFrameDrops = max(maxFrameDrops, drops - startFrameDrops)
        maxVoDelay = max(maxVoDelay, voDelay)
    }

    private fun computeResult(): Result {
        if (fpsSamples.isEmpty()) {
            return Result(
                level = StressLevel.RED,
                avgVfFps = 0.0,
                maxFrameDrops = 0,
                maxVoDelay = 0,
                recommendedPreset = ShaderPresets.Preset.NONE,
                recommendedHwdec = "mediacodec",
                summary = "No video FPS data — start a video before running the benchmark."
            )
        }
        val avgFps = fpsSamples.average()
        val displayFps = 60.0  // most phones including Reno 11F
        val fpsRatio = avgFps / displayFps

        // Score: blend FPS achievement with frame-drop penalty
        val level = when {
            fpsRatio >= 0.95 && maxFrameDrops <= 2 && maxVoDelay <= 1 -> StressLevel.GREEN
            fpsRatio >= 0.85 && maxFrameDrops <= 15 && maxVoDelay <= 4 -> StressLevel.YELLOW
            else -> StressLevel.RED
        }

        // Recommendation table — calibrated for Mali-G610 MC4 (Reno 11F).
        // Greens keep current preset; Yellows step down one tier; Reds step down two tiers
        // or fall back to RAVU/None.
        val (preset, hwdec, summary) = when (level) {
            StressLevel.GREEN -> Triple(
                ShaderPresets.Preset.ANIME4K_A,
                "mediacodec",
                "Your device handles the current load comfortably. " +
                "Anime4K Mode A and hardware decoding are safe to use. " +
                "You could try Mode B for better quality on cleaner sources."
            )
            StressLevel.YELLOW -> Triple(
                ShaderPresets.Preset.ANIME4K_A,
                "mediacodec",
                "Slight stress detected. Stick with Anime4K Mode A and hardware decoding. " +
                "If issues persist, drop base resolution to 360p or switch to RAVU Lite."
            )
            StressLevel.RED -> Triple(
                ShaderPresets.Preset.RAVU_LITE,
                "mediacodec",
                "Your device is overloaded. Switching to RAVU Lite (lighter than Anime4K) " +
                "and keeping hardware decode. If still bad, set shader preset to None and " +
                "lower the base resolution further."
            )
        }

        return Result(
            level = level,
            avgVfFps = avgFps,
            maxFrameDrops = maxFrameDrops,
            maxVoDelay = maxVoDelay,
            recommendedPreset = preset,
            recommendedHwdec = hwdec,
            summary = summary
        )
    }
}
