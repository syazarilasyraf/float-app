package com.floatoverlay.app.resource

/**
 * Abstraction for resource discovery.
 *
 * Future implementations may search the web, query image APIs, scan local
 * storage, or call AI services. The AI assistant will use these providers
 * to find references for modules like Minecraft.
 */
interface ResourceProvider {

    val name: String

    /**
     * Search for resources matching [query].
     *
     * @param callback invoked on the main thread with results or an error.
     */
    fun search(query: String, callback: Callback)

    interface Callback {
        fun onResult(resources: List<Resource>)
        fun onError(error: Throwable)
    }
}
