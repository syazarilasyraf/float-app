package com.floatoverlay.app.ui.game

import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import java.lang.reflect.Method
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.floatoverlay.app.LogStore
import com.floatoverlay.app.PresetRepository
import com.floatoverlay.app.R
import com.floatoverlay.app.model.WindowPreset

class GameLauncherFragment : Fragment(), PresetAdapter.PresetListener {

    private lateinit var repository: PresetRepository
    private lateinit var adapter: PresetAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var addButton: Button
    private var reflectionAllowed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = PresetRepository(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_game_launcher, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.presetRecyclerView)
        addButton = view.findViewById(R.id.addPresetButton)

        adapter = PresetAdapter(buildPresets(), this)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        addButton.setOnClickListener {
            PresetEditDialog.show(requireContext()) { preset ->
                repository.addOrUpdate(preset)
                refresh()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onLaunch(preset: WindowPreset) {
        launchClashRoyale(preset)
    }

    override fun onEdit(preset: WindowPreset) {
        PresetEditDialog.show(requireContext(), preset) { updated ->
            repository.addOrUpdate(updated)
            refresh()
        }
    }

    override fun onDelete(preset: WindowPreset) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete preset")
            .setMessage("Are you sure you want to delete \"${preset.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                repository.delete(preset.id)
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refresh() {
        adapter.updateData(buildPresets())
    }

    private fun buildPresets(): List<WindowPreset> {
        val fullscreen = WindowPreset(
            id = PresetAdapter.BUILTIN_FULLSCREEN_ID,
            name = "Launch fullscreen",
            widthPercent = 100,
            heightPercent = 100,
            xPercent = 0,
            yPercent = 0,
            isFullscreen = true
        )
        return listOf(fullscreen) + repository.getPresets()
    }

    private fun launchClashRoyale(preset: WindowPreset) {
        val packageManager = requireActivity().packageManager
        val resolved = resolveLaunchIntent(packageManager)
        if (resolved == null) {
            val disabledPackage = findDisabledClashRoyale(packageManager)
            val message = if (disabledPackage != null) {
                "Clash Royale found but is disabled/hidden ($disabledPackage)"
            } else {
                "Clash Royale not installed"
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            return
        }

        val (intent, packageName) = resolved
        LogStore.log("GameLauncher", "Resolved launch intent for $packageName")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)

        val options = if (preset.isFullscreen) {
            ActivityOptions.makeBasic()
        } else {
            val metrics = getScreenMetrics()
            val rect = computeRect(preset, metrics)
            ActivityOptions.makeBasic().setLaunchBounds(rect).also {
                requestFreeformWindowingMode(it)
            }
        }

        LogStore.log(
            "GameLauncher",
            "Starting Clash Royale preset=${preset.name} fullscreen=${preset.isFullscreen}"
        )
        startActivity(intent, options.toBundle())
    }

    private fun requestFreeformWindowingMode(options: ActivityOptions) {
        allowReflection()

        val methodName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            "setLaunchWindowingMode"
        } else {
            "setLaunchStackId"
        }
        val modeId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WINDOWING_MODE_FREEFORM
        } else {
            FREEFORM_WORKSPACE_STACK_ID
        }

        try {
            ActivityOptions::class.java
                .getMethod(methodName, Int::class.javaPrimitiveType)
                .invoke(options, modeId)
            LogStore.log("GameLauncher", "$methodName($modeId) succeeded")
        } catch (e: Exception) {
            LogStore.log("GameLauncher", "$methodName unavailable: $e")
        }
    }

    private fun allowReflection() {
        if (reflectionAllowed) return
        reflectionAllowed = true

        try {
            val forName = Class::class.java.getDeclaredMethod("forName", String::class.java)
            val classArrayClass = arrayOf<Class<*>>().javaClass
            val getDeclaredMethod = Class::class.java.getDeclaredMethod(
                "getDeclaredMethod",
                String::class.java,
                classArrayClass
            )

            val vmRuntimeClass = forName.invoke(null, "dalvik.system.VMRuntime") as Class<*>
            val getRuntime = getDeclaredMethod.invoke(
                vmRuntimeClass,
                "getRuntime",
                arrayOf<Class<*>>()
            ) as Method
            val setHiddenApiExemptions = getDeclaredMethod.invoke(
                vmRuntimeClass,
                "setHiddenApiExemptions",
                arrayOf<Class<*>>(Array<String>::class.java)
            ) as Method

            val vmRuntime = getRuntime.invoke(null)
            setHiddenApiExemptions.invoke(vmRuntime, arrayOf("L"))
            LogStore.log("GameLauncher", "allowReflection succeeded")
        } catch (e: Throwable) {
            LogStore.log("GameLauncher", "allowReflection failed: $e")
        }
    }

    private fun resolveLaunchIntent(packageManager: PackageManager): Pair<Intent, String>? {
        for (packageName in CLASH_ROYALE_PACKAGES) {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                return Pair(intent, packageName)
            }
        }
        return null
    }

    private fun findDisabledClashRoyale(packageManager: PackageManager): String? {
        for (packageName in CLASH_ROYALE_PACKAGES) {
            val info = try {
                packageManager.getApplicationInfo(packageName, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
            if (info != null && !info.enabled) {
                return packageName
            }
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun getScreenMetrics(): DisplayMetrics {
        val metrics = DisplayMetrics()
        requireActivity().windowManager.defaultDisplay.getRealMetrics(metrics)
        return metrics
    }

    private fun computeRect(preset: WindowPreset, metrics: DisplayMetrics): Rect {
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels
        val left = (preset.xPercent / 100f * screenW).toInt()
        val top = (preset.yPercent / 100f * screenH).toInt()
        val right = left + (preset.widthPercent / 100f * screenW).toInt()
        val bottom = top + (preset.heightPercent / 100f * screenH).toInt()
        return Rect(left, top, right, bottom)
    }

    companion object {
        private const val WINDOWING_MODE_FREEFORM = 5
        private const val FREEFORM_WORKSPACE_STACK_ID = 2

        private val CLASH_ROYALE_PACKAGES = listOf(
            "com.supercell.clashroyale",
            "com.tencent.tmgp.supercell.clashroyale"
        )
    }
}
