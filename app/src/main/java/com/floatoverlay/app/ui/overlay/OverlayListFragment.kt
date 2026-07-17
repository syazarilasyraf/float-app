package com.floatoverlay.app.ui.overlay

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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

        adapter = OverlayAdapter(repository.getOverlays(), this)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        addButton.setOnClickListener {
            OverlayEditDialog.show(requireContext()) { overlay ->
                repository.addOrUpdate(overlay)
                reloadOverlays()
                refresh()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onToggle(overlay: OverlayConfig, enabled: Boolean) {
        repository.addOrUpdate(overlay.copy(enabled = enabled))
        reloadOverlays()
    }

    override fun onEdit(overlay: OverlayConfig) {
        OverlayEditDialog.show(requireContext(), overlay) { updated ->
            repository.addOrUpdate(updated)
            reloadOverlays()
            refresh()
        }
    }

    override fun onDelete(overlay: OverlayConfig) {
        repository.delete(overlay.id)
        reloadOverlays()
        refresh()
    }

    private fun refresh() {
        adapter.updateData(repository.getOverlays())
    }

    private fun reloadOverlays() {
        FloatOverlayService.reloadOverlays(requireContext())
    }
}
