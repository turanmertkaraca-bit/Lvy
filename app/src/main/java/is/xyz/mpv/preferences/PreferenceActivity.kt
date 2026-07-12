package `is`.xyz.mpv.preferences

import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentManager
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.google.android.material.color.DynamicColors
import `is`.xyz.mpv.R

class PreferenceActivity : AppCompatActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
    SharedPreferences.OnSharedPreferenceChangeListener, FragmentManager.OnBackStackChangedListener {
    private lateinit var preferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        preferences.registerOnSharedPreferenceChangeListener(this)
        supportFragmentManager.addOnBackStackChangedListener(this)
        if (preferences.getBoolean("material_you_theming", false))
            DynamicColors.applyToActivityIfAvailable(this)
        enableEdgeToEdge()

        val frameLayout = FrameLayout(this).apply {
            id = R.id.main
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        setContentView(frameLayout)
        supportActionBar?.elevation = 0F
        ViewCompat.setOnApplyWindowInsetsListener(frameLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        if (savedInstanceState != null) {
            supportActionBar?.subtitle = savedInstanceState.getCharSequence("subtitle")
        } else {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main, SettingsFragment())
                .commit()
        }
    }

    override fun onBackStackChanged() {
        if (supportFragmentManager.backStackEntryCount == 0) {
            supportActionBar?.subtitle = null
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putCharSequence("subtitle", supportActionBar?.subtitle)
        supportFragmentManager.removeOnBackStackChangedListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        preferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (key != "material_you_theming") return
        if (sharedPreferences.getBoolean(key, false))
            DynamicColors.applyToActivityIfAvailable(this)
        recreate()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat, pref: Preference
    ): Boolean {
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader, pref.fragment ?: return false
        ).apply { arguments = pref.extras }

        supportFragmentManager.beginTransaction().replace(R.id.main, fragment).addToBackStack(null)
            .commit()

        supportActionBar?.subtitle = pref.title
        return true
    }

    /**
     * The root preference fragment that displays preferences that link to the other preference
     * fragments below.
     */
    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences_root, rootKey)
        }
    }

    class GeneralPreference : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.pref_general, rootKey)
            // hide Material You on Android 11 or lower
            preferenceManager.findPreference<Preference>("material_you_theming")?.isVisible =
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        }
    }

    class VideoPreference : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.pref_video, rootKey)
        }
    }

    class UIPreference : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.pref_ui, rootKey)
            val packageManager = requireContext().packageManager
            if (!packageManager.hasSystemFeature(PackageManager.FEATURE_SCREEN_PORTRAIT))
                findPreference<Preference>("auto_rotation")?.isEnabled = false
        }
    }

    class GesturePreference : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.pref_gestures, rootKey)
            val packageManager = requireContext().packageManager
            if (!packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) {
                for (i in 0 until preferenceScreen.preferenceCount)
                    preferenceScreen.getPreference(i).isEnabled = false
            }
        }
    }

    class DeveloperPreference : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.pref_developer, rootKey)

            // === AnimeStream: Stress Bar benchmark launcher ===
            findPreference<androidx.preference.Preference>("run_stress_benchmark")
                ?.setOnPreferenceClickListener {
                    val activity = requireActivity()
                    if (!`is`.xyz.mpv.MPVLib.isCreated()) {
                        android.widget.Toast.makeText(
                            activity, getString(`is`.xyz.mpv.R.string.benchmark_no_video),
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        return@setOnPreferenceClickListener true
                    }
                    android.widget.Toast.makeText(
                        activity, getString(`is`.xyz.mpv.R.string.benchmark_running),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    `is`.xyz.mpv.StressBenchmark.start(activity) { result ->
                        activity.runOnUiThread {
                            showBenchmarkResult(activity, result)
                        }
                    }
                    true
                }
        }

        private fun showBenchmarkResult(
            context: android.content.Context,
            result: `is`.xyz.mpv.StressBenchmark.Result
        ) {
            val colorHex = when (result.level) {
                `is`.xyz.mpv.StressBenchmark.StressLevel.GREEN -> "#4CAF50"
                `is`.xyz.mpv.StressBenchmark.StressLevel.YELLOW -> "#FFC107"
                `is`.xyz.mpv.StressBenchmark.StressLevel.RED -> "#F44336"
            }
            val msg = buildString {
                append("Stress Level: ${result.level.label}\n\n")
                append("Avg video filter FPS: %.1f\n".format(result.avgVfFps))
                append("Frame drops during test: ${result.maxFrameDrops}\n")
                append("Max VO delay: ${result.maxVoDelay}\n\n")
                append("Recommended shader: ${result.recommendedPreset.displayName}\n")
                append("Recommended decoder: ${result.recommendedHwdec}\n\n")
                append(result.summary)
            }
            androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle(`is`.xyz.mpv.R.string.benchmark_complete)
                .setMessage(msg)
                .setPositiveButton(`is`.xyz.mpv.R.string.benchmark_apply_recommended) { _, _ ->
                    val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(context)
                    prefs.edit()
                        .putString("shader_preset", result.recommendedPreset.key)
                        .putBoolean("hardware_decoding", result.recommendedHwdec != "no")
                        .apply()
                    // Apply at runtime so user sees immediate effect
                    `is`.xyz.mpv.ShaderPresets.applyRuntime(context, result.recommendedPreset)
                    android.widget.Toast.makeText(
                        context, "Applied ${result.recommendedPreset.displayName}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    class AdvancePreference : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.pref_advanced, rootKey)
        }
    }
}
