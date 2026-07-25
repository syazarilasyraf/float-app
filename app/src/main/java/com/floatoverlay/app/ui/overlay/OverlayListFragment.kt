package com.floatoverlay.app.ui.overlay

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.floatoverlay.app.FloatOverlayService
import com.floatoverlay.app.OverlayRepository
import com.floatoverlay.app.R
import com.floatoverlay.app.model.OverlayConfig

class OverlayListFragment : Fragment(), OverlayAdapter.OverlayListener {

    private lateinit var repository: OverlayRepository
    private lateinit var adapter: OverlayAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var addButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = OverlayRepository(requireContext())
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
}
