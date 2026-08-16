package com.floatoverlay.app.resource

import android.os.Handler
import android.os.Looper

/**
 * Mock resource provider for offline development.
 *
 * Returns a few static Minecraft-related resources so the architecture can be
 * tested without network access or API keys.
 */
class MockResourceProvider : ResourceProvider {

    override val name: String = "Mock Resources"

    private val handler = Handler(Looper.getMainLooper())

    override fun search(query: String, callback: ResourceProvider.Callback) {
        handler.postDelayed({
            val lower = query.lowercase()
            val results = when {
                lower.contains("japanese") -> listOf(
                    Resource(
                        id = "mock-1",
                        title = "Japanese House Minecraft Build",
                        url = "https://example.com/japanese-house",
                        description = "Step-by-step Japanese house tutorial with materials list.",
                        source = "Mock"
                    ),
                    Resource(
                        id = "mock-2",
                        title = "Japanese Roof Patterns",
                        url = "https://example.com/japanese-roof",
                        description = "Curved roof design reference images.",
                        source = "Mock"
                    )
                )
                lower.contains("castle") -> listOf(
                    Resource(
                        id = "mock-3",
                        title = "Medieval Castle Blueprint",
                        url = "https://example.com/castle",
                        description = "Large castle layout with towers and walls.",
                        source = "Mock"
                    )
                )
                else -> listOf(
                    Resource(
                        id = "mock-0",
                        title = "Minecraft Building Ideas",
                        url = "https://example.com/ideas",
                        description = "General Minecraft build inspiration.",
                        source = "Mock"
                    )
                )
            }
            callback.onResult(results)
        }, 400)
    }
}
