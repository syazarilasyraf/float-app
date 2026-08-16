package com.floatoverlay.app.resource

/**
 * A generic resource that can be discovered by an AI assistant.
 *
 * Resources are source-agnostic: they may come from web search, image search,
 * local files, AI generation, etc.
 */
data class Resource(
    val id: String,
    val title: String,
    val url: String,
    val thumbnailUrl: String = "",
    val description: String = "",
    val source: String = "",
    val metadata: Map<String, String> = emptyMap()
)
