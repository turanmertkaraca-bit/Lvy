package `is`.xyz.mpv

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Defines bundled upscaling shader presets for anime watching.
 *
 * Shaders live in `app/src/main/assets/shaders/`. At first launch they are
 * copied to the app's filesDir so mpv can load them via `glsl-shaders` option.
 *
 * Preset list (curated for OPPO Reno 11F / Mali-G610 MC4):
 *  - none              : no shaders (just mpv's built-in scale= spline36)
 *  - anime4k_a         : Anime4K Mode A — lightest, recommended for 720p→1080p
 *  - anime4k_b         : Anime4K Mode B — adds Restore + denoise, ~50% slower
 *  - anime4k_c         : Anime4K Mode C — adds Thin (line darkening), heaviest
 *  - ravu_lite         : RAVU Lite r4 — best for non-anime / live action
 *  - fsrcnnx           : FSRCNNX — neural net, balanced quality/perf
 *  - artcnn            : ArtCNN — newer neural net, very clean edges
 */
object ShaderPresets {

    private const val TAG = "ShaderPresets"
    private const val SHADERS_DIR = "shaders"

    enum class Preset(
        val key: String,
        val displayName: String,
        val description: String,
        val files: List<String>
    ) {
        NONE(
            "none",
            "None (spline36)",
            "Pure mpv scaler. Fastest. Use if you don't need upscaling.",
            emptyList()
        ),
        ANIME4K_A(
            "anime4k_a",
            "Anime4K Mode A",
            "Lightest anime preset. Best for 720p→1080p on Mali-G610.",
            listOf(
                "Anime4K_Clamp_Highlights.glsl",
                "Anime4K_Restore_CNN_M.glsl",
                "Anime4K_Upscale_CNN_x2_M.glsl"
            )
        ),
        ANIME4K_B(
            "anime4k_b",
            "Anime4K Mode B",
            "Adds denoise + restore. ~50% slower than A. Best for noisy 480p sources.",
            listOf(
                "Anime4K_Clamp_Highlights.glsl",
                "Anime4K_Restore_CNN_S.glsl",
                "Anime4K_Upscale_CNN_x2_S.glsl",
                "Anime4K_Restore_CNN_M.glsl",
                "Anime4K_Upscale_CNN_x2_M.glsl"
            )
        ),
        ANIME4K_C(
            "anime4k_c",
            "Anime4K Mode C",
            "Adds line darkening + restore. Heaviest. Best for clean 1080p anime.",
            listOf(
                "Anime4K_Clamp_Highlights.glsl",
                "Anime4K_Restore_CNN_S.glsl",
                "Anime4K_Upscale_CNN_x2_S.glsl",
                "Anime4K_Restore_CNN_L.glsl",
                "Anime4K_Upscale_CNN_x2_L.glsl"
            )
        ),
        RAVU_LITE(
            "ravu_lite",
            "RAVU Lite r4",
            "Best for live-action / non-anime. Less aggressive than Anime4K.",
            listOf("ravu-lite-r4.hook")
        ),
        FSRCNNX(
            "fsrcnnx",
            "FSRCNNX x2 8-0-4-1",
            "Neural net upscaler. Balanced quality/perf. Bundled if file present.",
            listOf("FSRCNNX_x2_8-0-4-1.glsl")
        ),
        ARTCNN(
            "artcnn",
            "ArtCNN C4F32",
            "Newer neural net with very clean edges. Bundled if file present.",
            listOf("ArtCNN_C4F32_Depth_toToSpace.glsl")
        );

        companion object {
            fun fromKey(key: String?): Preset =
                values().firstOrNull { it.key == key } ?: NONE
        }
    }

    /**
     * Copies bundled shader files from assets to filesDir/shaders/ on first launch.
     * Subsequent calls are no-ops (skips files that already exist).
     */
    fun ensureShadersCopied(context: Context) {
        val outDir = File(context.filesDir, SHADERS_DIR)
        if (!outDir.exists()) outDir.mkdirs()

        val assetFiles = try {
            context.assets.list(SHADERS_DIR) ?: emptyArray()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list shader assets", e)
            emptyArray()
        }

        for (name in assetFiles) {
            val outFile = File(outDir, name)
            if (outFile.exists() && outFile.length() > 0) continue
            try {
                context.assets.open("$SHADERS_DIR/$name").use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                }
                Log.v(TAG, "Copied shader: $name (${outFile.length()} bytes)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy shader $name", e)
            }
        }
    }

    /**
     * Returns the colon-joined absolute path string suitable for mpv's
     * `glsl-shaders` option, or empty string if the preset is "none" or
     * none of its files are available.
     */
    fun shaderOptionValue(context: Context, preset: Preset): String {
        if (preset == Preset.NONE) return ""
        val outDir = File(context.filesDir, SHADERS_DIR)
        val existing = preset.files.mapNotNull { name ->
            val f = File(outDir, name)
            if (f.exists() && f.length() > 0) f.absolutePath else null
        }
        if (existing.isEmpty()) {
            Log.w(TAG, "Preset ${preset.key} has no bundled shader files available")
            return ""
        }
        // mpv's glsl-shaders takes a colon-separated list of paths
        return existing.joinToString(":")
    }

    /**
     * Applies the preset to the running mpv instance by setting the
     * `glsl-shaders` option. Call BEFORE loadfile for it to take effect.
     */
    fun apply(context: Context, preset: Preset) {
        ensureShadersCopied(context)
        val value = shaderOptionValue(context, preset)
        if (value.isEmpty()) {
            MPVLib.setOptionString("glsl-shaders", "")
            MPVLib.setOptionString("scale", "spline36")
            Log.i(TAG, "Preset ${preset.key}: cleared shaders, using spline36")
        } else {
            MPVLib.setOptionString("glsl-shaders", value)
            // When using a hooked upscaler (Anime4K/RAVU/FSRCNNX), keep the
            // base scaler as bilinear to avoid double-scaling cost.
            MPVLib.setOptionString("scale", "bilinear")
            Log.i(TAG, "Preset ${preset.key}: loaded ${value.split(":").size} shader files")
        }
    }

    /**
     * Applies the preset at runtime (after playback has started) by setting
     * the `glsl-shaders-cl` property. mpv reloads shaders immediately.
     */
    fun applyRuntime(context: Context, preset: Preset) {
        ensureShadersCopied(context)
        val value = shaderOptionValue(context, preset)
        // glsl-shaders-cl is the change-list variant: "+" adds, "-" removes,
        // empty list clears all.
        if (value.isEmpty()) {
            MPVLib.setPropertyString("glsl-shaders-cl", "")
            MPVLib.setPropertyString("scale", "spline36")
        } else {
            MPVLib.setPropertyString("glsl-shaders-cl", "+$value")
            MPVLib.setPropertyString("scale", "bilinear")
        }
    }
}
