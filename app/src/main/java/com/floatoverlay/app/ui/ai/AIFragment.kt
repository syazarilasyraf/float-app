package com.floatoverlay.app.ui.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.floatoverlay.app.FloatOverlayService
import com.floatoverlay.app.R
import com.floatoverlay.app.ai.AIProvider
import com.floatoverlay.app.ai.AIProviderFactory
import com.floatoverlay.app.ai.ToolExecutionResult
import com.floatoverlay.app.ai.ToolRegistry
import com.floatoverlay.app.ai.asText
import com.floatoverlay.app.ai.provider.MockAIProvider
import com.floatoverlay.app.ai.tool.AddBuildStepTool
import com.floatoverlay.app.ai.tool.AddMaterialTool
import com.floatoverlay.app.ai.tool.CreateBuildProjectTool
import com.floatoverlay.app.data.ConversationRepository
import com.floatoverlay.app.data.ProjectRepository
import com.floatoverlay.app.data.SettingsRepository
import com.floatoverlay.app.model.Message
import com.google.android.material.textfield.TextInputEditText

/**
 * In-app AI assistant chat.
 *
 * The same conversation can also be opened as a floating overlay through
 * [FloatOverlayService] by creating a special overlay config.
 */
class AIFragment : Fragment() {

    private lateinit var conversationRepository: ConversationRepository
    private lateinit var projectRepository: ProjectRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var provider: AIProvider

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatAdapter
    private lateinit var inputField: EditText
    private lateinit var sendButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var clearButton: ImageButton
    private lateinit var floatButton: ImageButton
    private lateinit var settingsButton: ImageButton
    private lateinit var copyButton: ImageButton

    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context = requireContext()
        conversationRepository = ConversationRepository(context)
        projectRepository = ProjectRepository(context)
        settingsRepository = SettingsRepository(context)
        provider = AIProviderFactory.create(context)
        registerTools()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_ai, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.chatRecyclerView)
        inputField = view.findViewById(R.id.chatInput)
        sendButton = view.findViewById(R.id.sendButton)
        progressBar = view.findViewById(R.id.chatProgress)
        statusText = view.findViewById(R.id.providerStatus)
        clearButton = view.findViewById(R.id.clearChatButton)
        floatButton = view.findViewById(R.id.openFloatAiButton)
        settingsButton = view.findViewById(R.id.aiSettingsButton)
        copyButton = view.findViewById(R.id.copyChatButton)

        adapter = ChatAdapter()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        updateStatus()
        refreshMessages()

        sendButton.setOnClickListener { sendMessage() }
        clearButton.setOnClickListener { confirmClear() }
        floatButton.setOnClickListener { openFloatingAI() }
        settingsButton.setOnClickListener { showSettingsDialog() }
        copyButton.setOnClickListener { copyConversationToClipboard() }
    }

    override fun onResume() {
        super.onResume()
        // Provider may have changed in settings.
        provider = AIProviderFactory.create(requireContext())
        updateStatus()
    }

    private fun updateStatus() {
        val hasKey = !AIProviderFactory.getKimiApiKey(requireContext()).isNullOrBlank()
        val keyHint = if (hasKey) "key set" else "no key"
        statusText.text = "Provider: ${provider.name} ($keyHint)"
    }

    private fun refreshMessages() {
        val conversation = conversationRepository.getConversation()
        adapter.submitList(conversation.messages)
        recyclerView.post { recyclerView.scrollToPosition(adapter.itemCount - 1) }
    }

    private fun sendMessage() {
        val text = inputField.text.toString().trim()
        if (text.isEmpty()) return

        inputField.text.clear()
        conversationRepository.addMessage(Message(role = Message.Role.USER, content = text))
        refreshMessages()
        setLoading(true)

        val conversation = conversationRepository.getConversation()
        provider.sendMessage(conversation.messages, ToolRegistry.all(), object : AIProvider.AIResponseCallback {
            override fun onLoading() {}

            override fun onResult(message: Message) {
                setLoading(false)
                handleAssistantMessage(message)
            }

            override fun onError(error: Throwable) {
                setLoading(false)
                conversationRepository.addMessage(
                    Message(
                        role = Message.Role.ASSISTANT,
                        content = "Error: ${error.message}"
                    )
                )
                refreshMessages()
            }
        })
    }

    private fun handleAssistantMessage(message: Message) {
        val toolCall = message.toolCall
        if (toolCall != null) {
            // Store the assistant's intent to call a tool.
            conversationRepository.addMessage(message)

            // Execute the tool.
            val tool = ToolRegistry.get(toolCall.toolName)
            val result = tool?.execute(toolCall.arguments)
                ?: ToolExecutionResult.Error("Tool '${toolCall.toolName}' not found")

            // Store the tool result.
            conversationRepository.addMessage(
                Message(
                    role = Message.Role.TOOL,
                    content = result.asText(),
                    toolResult = Message.ToolResult(
                        toolName = toolCall.toolName,
                        success = result is ToolExecutionResult.Success,
                        message = result.asText(),
                        toolCallId = toolCall.toolCallId
                    )
                )
            )

            refreshMessages()
            Toast.makeText(requireContext(), result.asText(), Toast.LENGTH_SHORT).show()
        } else {
            conversationRepository.addMessage(message)
            refreshMessages()
        }
    }

    private fun setLoading(loading: Boolean) {
        isLoading = loading
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        sendButton.isEnabled = !loading
    }

    private fun confirmClear() {
        AlertDialog.Builder(requireContext())
            .setTitle("Clear conversation")
            .setMessage("Delete all messages?")
            .setPositiveButton("Clear") { _, _ ->
                conversationRepository.clearConversation()
                refreshMessages()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openFloatingAI() {
        FloatOverlayService.toggleFloatingAI(requireContext())
    }

    private fun copyConversationToClipboard() {
        val conversation = conversationRepository.getConversation()
        val text = conversation.messages
            .filter { it.role != Message.Role.SYSTEM }
            .joinToString("\n\n") { msg ->
                val prefix = when (msg.role) {
                    Message.Role.USER -> "me:"
                    Message.Role.ASSISTANT -> "float:"
                    Message.Role.TOOL -> "tool:"
                    else -> "${msg.role.name.lowercase()}:"
                }
                "$prefix ${msg.content}"
            }
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Float AI conversation", text))
        Toast.makeText(requireContext(), "Conversation copied", Toast.LENGTH_SHORT).show()
    }

    private fun showSettingsDialog() {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_ai_settings, null)
        val providerSpinner = view.findViewById<Spinner>(R.id.providerSpinner)
        val keyInput = view.findViewById<TextInputEditText>(R.id.apiKeyInput)
        val modelSpinner = view.findViewById<Spinner>(R.id.modelSpinner)

        val providers = listOf(SettingsRepository.PROVIDER_MOCK, SettingsRepository.PROVIDER_KIMI)
        providerSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            providers
        )
        providerSpinner.setSelection(providers.indexOf(settingsRepository.getSelectedProviderName()).coerceAtLeast(0))

        val models = listOf("k3", "k3-256k", "kimi-for-coding", "kimi-for-coding-highspeed")
        modelSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            models
        )
        modelSpinner.setSelection(models.indexOf(settingsRepository.getKimiModel()).coerceAtLeast(0))

        // Pre-fill key is hidden for security; user can paste a new one to update.
        keyInput.hint = "Paste Kimi API key (optional)"

        AlertDialog.Builder(requireContext())
            .setTitle("AI Provider Settings")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val selectedProvider = providerSpinner.selectedItem as String
                val newKey = keyInput.text.toString().trim()
                val selectedModel = modelSpinner.selectedItem as String

                settingsRepository.setSelectedProviderName(selectedProvider)
                settingsRepository.setKimiModel(selectedModel)
                if (newKey.isNotBlank()) {
                    AIProviderFactory.saveKimiApiKey(requireContext(), newKey)
                }

                provider = AIProviderFactory.create(requireContext())
                updateStatus()
                Toast.makeText(requireContext(), "Provider updated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun registerTools() {
        ToolRegistry.register(CreateBuildProjectTool(projectRepository))
        ToolRegistry.register(AddMaterialTool(projectRepository))
        ToolRegistry.register(AddBuildStepTool(projectRepository))
    }
}
