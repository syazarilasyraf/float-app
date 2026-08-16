package com.floatoverlay.app.ui.minecraft

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.floatoverlay.app.FloatOverlayService
import com.floatoverlay.app.R
import com.floatoverlay.app.data.ProjectRepository
import com.floatoverlay.app.model.BuildProject
import com.floatoverlay.app.model.BuildStep
import com.floatoverlay.app.model.Material
import com.floatoverlay.app.model.Reference
import com.google.android.material.textfield.TextInputEditText

/**
 * Detail screen for a Minecraft build project.
 *
 * Provides overview, materials, steps, references, notes, and an
 * "Open in Float" action that launches the project as a floating overlay.
 */
class ProjectDetailActivity : AppCompatActivity() {

    private lateinit var repository: ProjectRepository
    private var projectId: String = ""
    private var project: BuildProject? = null

    private lateinit var nameText: TextView
    private lateinit var descriptionText: TextView
    private lateinit var progressText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var materialsRecycler: RecyclerView
    private lateinit var stepsRecycler: RecyclerView
    private lateinit var referencesRecycler: RecyclerView
    private lateinit var notesInput: EditText
    private lateinit var saveNotesButton: Button
    private lateinit var openFloatButton: Button
    private lateinit var addMaterialButton: Button
    private lateinit var addStepButton: Button
    private lateinit var addReferenceButton: Button

    private lateinit var materialAdapter: MaterialAdapter
    private lateinit var stepAdapter: StepAdapter
    private lateinit var referenceAdapter: ReferenceAdapter

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { addReferenceImage(it.toString()) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_project_detail)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        repository = ProjectRepository(this)
        projectId = intent.getStringExtra(EXTRA_PROJECT_ID) ?: ""
        if (projectId.isBlank()) {
            Toast.makeText(this, "Project not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        bindViews()
        setupAdapters()
        loadProject()
    }

    override fun onResume() {
        super.onResume()
        loadProject()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun bindViews() {
        nameText = findViewById(R.id.projectDetailName)
        descriptionText = findViewById(R.id.projectDetailDescription)
        progressText = findViewById(R.id.projectProgressText)
        progressBar = findViewById(R.id.projectProgressBar)
        materialsRecycler = findViewById(R.id.materialsRecyclerView)
        stepsRecycler = findViewById(R.id.stepsRecyclerView)
        referencesRecycler = findViewById(R.id.referencesRecyclerView)
        notesInput = findViewById(R.id.notesInput)
        saveNotesButton = findViewById(R.id.saveNotesButton)
        openFloatButton = findViewById(R.id.openFloatButton)
        addMaterialButton = findViewById(R.id.addMaterialButton)
        addStepButton = findViewById(R.id.addStepButton)
        addReferenceButton = findViewById(R.id.addReferenceButton)

        saveNotesButton.setOnClickListener { saveNotes() }
        openFloatButton.setOnClickListener { openInFloat() }
        addMaterialButton.setOnClickListener { showAddMaterialDialog() }
        addStepButton.setOnClickListener { showAddStepDialog() }
        addReferenceButton.setOnClickListener { pickReferenceImage() }
    }

    private fun setupAdapters() {
        materialAdapter = MaterialAdapter(emptyList(), object : MaterialAdapter.MaterialListener {
            override fun onToggle(material: Material, collected: Boolean) {
                updateProject { project ->
                    project.copy(
                        materials = project.materials.map {
                            if (it.id == material.id) it.copy(collected = collected) else it
                        },
                        updatedAt = System.currentTimeMillis()
                    )
                }
            }

            override fun onDelete(material: Material) {
                updateProject { project ->
                    project.copy(
                        materials = project.materials.filterNot { it.id == material.id },
                        updatedAt = System.currentTimeMillis()
                    )
                }
            }
        })
        materialsRecycler.layoutManager = LinearLayoutManager(this)
        materialsRecycler.adapter = materialAdapter

        stepAdapter = StepAdapter(emptyList(), object : StepAdapter.StepListener {
            override fun onToggle(step: BuildStep, completed: Boolean) {
                updateProject { project ->
                    project.copy(
                        steps = project.steps.map {
                            if (it.id == step.id) it.copy(completed = completed) else it
                        },
                        updatedAt = System.currentTimeMillis()
                    )
                }
            }

            override fun onDelete(step: BuildStep) {
                updateProject { project ->
                    project.copy(
                        steps = project.steps.filterNot { it.id == step.id },
                        updatedAt = System.currentTimeMillis()
                    )
                }
            }
        })
        stepsRecycler.layoutManager = LinearLayoutManager(this)
        stepsRecycler.adapter = stepAdapter

        referenceAdapter = ReferenceAdapter(emptyList(), object : ReferenceAdapter.ReferenceListener {
            override fun onDelete(reference: Reference) {
                updateProject { project ->
                    project.copy(
                        references = project.references.filterNot { it.id == reference.id },
                        updatedAt = System.currentTimeMillis()
                    )
                }
            }
        })
        referencesRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        referencesRecycler.adapter = referenceAdapter
    }

    private fun loadProject() {
        project = repository.getProject(projectId)
        val current = project ?: return

        title = current.name
        nameText.text = current.name
        descriptionText.text = current.description.takeIf { it.isNotBlank() } ?: "No description"
        progressText.text = "Steps: ${current.completedSteps}/${current.totalSteps} · Materials: ${current.materialProgressPercent}%"
        progressBar.progress = current.stepProgressPercent
        notesInput.setText(current.notes)

        materialAdapter.updateData(current.materials)
        stepAdapter.updateData(current.steps)
        referenceAdapter.updateData(current.references)
    }

    private fun saveNotes() {
        updateProject { it.copy(notes = notesInput.text.toString(), updatedAt = System.currentTimeMillis()) }
        Toast.makeText(this, "Notes saved", Toast.LENGTH_SHORT).show()
    }

    private fun openInFloat() {
        FloatOverlayService.openFloatingMinecraftProject(this, projectId)
        Toast.makeText(this, "Opened in Float overlay", Toast.LENGTH_SHORT).show()
    }

    private fun pickReferenceImage() {
        pickImageLauncher.launch("image/*")
    }

    private fun addReferenceImage(uri: String) {
        updateProject { project ->
            project.copy(
                references = project.references + Reference(imageUri = uri, source = Reference.Source.GALLERY),
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    private fun showAddMaterialDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_material_edit, null)
        val nameInput = view.findViewById<TextInputEditText>(R.id.materialNameInput)
        val qtyInput = view.findViewById<TextInputEditText>(R.id.materialQuantityInput)

        AlertDialog.Builder(this)
            .setTitle("Add Material")
            .setView(view)
            .setPositiveButton("Add") { _, _ ->
                val name = nameInput.text.toString().trim()
                val qty = qtyInput.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1
                if (name.isNotEmpty()) {
                    updateProject { project ->
                        project.copy(
                            materials = project.materials + Material(name = name, quantity = qty),
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddStepDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_step_edit, null)
        val titleInput = view.findViewById<TextInputEditText>(R.id.stepTitleInput)
        val descInput = view.findViewById<TextInputEditText>(R.id.stepDescriptionInput)

        AlertDialog.Builder(this)
            .setTitle("Add Build Step")
            .setView(view)
            .setPositiveButton("Add") { _, _ ->
                val title = titleInput.text.toString().trim()
                val desc = descInput.text.toString().trim()
                if (title.isNotEmpty()) {
                    updateProject { project ->
                        project.copy(
                            steps = project.steps + BuildStep(title = title, description = desc),
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateProject(transform: (BuildProject) -> BuildProject) {
        val current = project ?: return
        val updated = transform(current)
        repository.saveProject(updated)
        project = updated
        loadProject()
        FloatOverlayService.refreshMinecraftOverlays(this)
    }

    companion object {
        const val EXTRA_PROJECT_ID = "project_id"
    }
}
