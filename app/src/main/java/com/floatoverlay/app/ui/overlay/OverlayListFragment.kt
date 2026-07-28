package com.floatoverlay.app.ui.overlay

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.floatoverlay.app.FloatOverlayService
import com.floatoverlay.app.OverlayRepository
import com.floatoverlay.app.ProfileRepository
import com.floatoverlay.app.R
import com.floatoverlay.app.model.OverlayConfig
import com.floatoverlay.app.model.OverlayProfile
import com.google.android.material.textfield.TextInputEditText

class OverlayListFragment : Fragment(), OverlayAdapter.OverlayListener {

    private lateinit var repository: OverlayRepository
    private lateinit var profileRepository: ProfileRepository
    private lateinit var adapter: OverlayAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var addButton: Button
    private lateinit var saveProfileButton: Button
    private lateinit var profilesButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = OverlayRepository(requireContext())
        profileRepository = ProfileRepository(requireContext())
        migrateZIndex()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_overlay_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.overlayRecyclerView)
        addButton = view.findViewById(R.id.addOverlayButton)
        saveProfileButton = view.findViewById(R.id.saveProfileButton)
        profilesButton = view.findViewById(R.id.profilesButton)

        adapter = OverlayAdapter(getSortedOverlays(), this)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        addButton.setOnClickListener {
            OverlayEditDialog.show(requireContext()) { overlay ->
                repository.addOrUpdate(overlay)
                reloadOverlays() // full reload for new overlay
                refresh()
            }
        }

        saveProfileButton.setOnClickListener {
            showSaveProfileDialog()
        }

        profilesButton.setOnClickListener {
            showProfilesDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onToggle(overlay: OverlayConfig, enabled: Boolean) {
        val latest = repository.getOverlay(overlay.id) ?: overlay
        repository.addOrUpdate(latest.copy(enabled = enabled))
        reloadOverlays(overlay.id)
    }

    override fun onEdit(overlay: OverlayConfig) {
        val latest = repository.getOverlay(overlay.id) ?: overlay
        OverlayEditDialog.show(requireContext(), latest) { updated ->
            repository.addOrUpdate(updated)
            reloadOverlays(overlay.id)
            refresh()
        }
    }

    override fun onDelete(overlay: OverlayConfig) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete overlay")
            .setMessage("Are you sure you want to delete \"${overlay.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                repository.delete(overlay.id)
                reloadOverlays() // full reload to remove it
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onRefresh(overlay: OverlayConfig) {
        FloatOverlayService.refreshOverlay(requireContext(), overlay.id)
    }

    override fun onMoveUp(overlay: OverlayConfig) {
        moveOverlay(overlay, -1)
    }

    override fun onMoveDown(overlay: OverlayConfig) {
        moveOverlay(overlay, 1)
    }

    private fun moveOverlay(overlay: OverlayConfig, direction: Int) {
        val sorted = getSortedOverlays().toMutableList()
        val index = sorted.indexOfFirst { it.id == overlay.id }
        if (index < 0) return
        val neighborIndex = index + direction
        if (neighborIndex < 0 || neighborIndex >= sorted.size) return

        val current = sorted[index]
        val neighbor = sorted[neighborIndex]
        val currentZ = current.zIndex
        val neighborZ = neighbor.zIndex

        repository.addOrUpdate(current.copy(zIndex = neighborZ))
        repository.addOrUpdate(neighbor.copy(zIndex = currentZ))

        refresh()
        FloatOverlayService.reorderOverlays(requireContext())
    }

    private fun refresh() {
        adapter.updateData(getSortedOverlays())
    }

    private fun getSortedOverlays(): List<OverlayConfig> {
        return repository.getOverlays().sortedWith(
            compareByDescending<OverlayConfig> { it.zIndex }
                .thenBy { it.id }
        )
    }

    private fun migrateZIndex() {
        val overlays = repository.getOverlays()
        if (overlays.isEmpty()) return
        // If every overlay still has the default zIndex, assign list order once.
        // The topmost item (last in the original list) gets the highest zIndex.
        if (overlays.all { it.zIndex == 0 }) {
            val count = overlays.size
            overlays.forEachIndexed { index, overlay ->
                repository.addOrUpdate(overlay.copy(zIndex = count - 1 - index))
            }
        }
    }

    private fun reloadOverlays(overlayId: String? = null) {
        FloatOverlayService.reloadOverlays(requireContext(), overlayId)
    }

    private fun showSaveProfileDialog() {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_save_profile, null)
        val nameInput = view.findViewById<TextInputEditText>(R.id.profileNameInput)
        val orientationGroup = view.findViewById<RadioGroup>(R.id.profileOrientationGroup)

        val currentOrientation = getCurrentOrientationLabel()
        when (currentOrientation) {
            "portrait" -> orientationGroup.check(R.id.profileOrientationPortrait)
            "landscape" -> orientationGroup.check(R.id.profileOrientationLandscape)
            else -> orientationGroup.check(R.id.profileOrientationAny)
        }

        AlertDialog.Builder(requireContext())
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(requireContext(), "Profile name is required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val orientation = when (orientationGroup.checkedRadioButtonId) {
                    R.id.profileOrientationPortrait -> "portrait"
                    R.id.profileOrientationLandscape -> "landscape"
                    else -> "any"
                }
                val snapshot = repository.getOverlays().map { it.copy() }
                val profile = OverlayProfile(
                    name = name,
                    orientation = orientation,
                    overlays = snapshot
                )
                profileRepository.addOrUpdate(profile)
                Toast.makeText(requireContext(), "Profile \"$name\" saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showProfilesDialog() {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_profile_list, null)
        val recycler = view.findViewById<RecyclerView>(R.id.profileRecyclerView)
        val emptyText = view.findViewById<View>(R.id.profileEmptyText)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(view)
            .setPositiveButton("Close", null)
            .show()

        val profiles = profileRepository.getProfiles()
        if (profiles.isEmpty()) {
            recycler.visibility = View.GONE
            emptyText.visibility = View.VISIBLE
        } else {
            recycler.visibility = View.VISIBLE
            emptyText.visibility = View.GONE
            recycler.layoutManager = LinearLayoutManager(requireContext())
            recycler.adapter = ProfileAdapter(profiles, object : ProfileAdapter.ProfileListener {
                override fun onApply(profile: OverlayProfile) {
                    dialog.dismiss()
                    applyProfile(profile)
                }

                override fun onRename(profile: OverlayProfile) {
                    showRenameProfileDialog(profile)
                }

                override fun onDelete(profile: OverlayProfile) {
                    deleteProfile(profile)
                }
            })
        }
    }

    private fun applyProfile(profile: OverlayProfile) {
        if (profile.overlays.isEmpty()) {
            AlertDialog.Builder(requireContext())
                .setTitle("Apply empty profile?")
                .setMessage("\"${profile.name}\" has no overlays. This will clear your current setup.")
                .setPositiveButton("Apply") { _, _ ->
                    doApplyProfile(profile)
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            doApplyProfile(profile)
        }
    }

    private fun doApplyProfile(profile: OverlayProfile) {
        FloatOverlayService.applyProfile(requireContext(), profile.id)
        Toast.makeText(requireContext(), "Profile \"${profile.name}\" applied", Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun showRenameProfileDialog(profile: OverlayProfile) {
        val input = TextInputEditText(requireContext()).apply {
            setText(profile.name)
            hint = "Profile name"
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Rename profile")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isEmpty()) {
                    Toast.makeText(requireContext(), "Name is required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                profileRepository.addOrUpdate(profile.copy(name = newName))
                Toast.makeText(requireContext(), "Profile renamed", Toast.LENGTH_SHORT).show()
                showProfilesDialog()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteProfile(profile: OverlayProfile) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete profile")
            .setMessage("Are you sure you want to delete \"${profile.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                profileRepository.delete(profile.id)
                Toast.makeText(requireContext(), "Profile deleted", Toast.LENGTH_SHORT).show()
                showProfilesDialog()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun getCurrentOrientationLabel(): String {
        val orientation = resources.configuration.orientation
        return if (orientation == Configuration.ORIENTATION_LANDSCAPE) "landscape" else "portrait"
    }
}
