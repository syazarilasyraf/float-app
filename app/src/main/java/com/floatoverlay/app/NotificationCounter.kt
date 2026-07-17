package com.floatoverlay.app

import org.json.JSONObject

class NotificationCounter(private val repository: OverlayRepository) {

    enum class Category { DONATION, CHAT, VIEWER }

    private val counts = mutableMapOf<Category, Int>()

    init {
        load()
    }

    fun increment(category: Category, amount: Int = 1): Int {
        val newValue = (counts[category] ?: 0) + amount
        counts[category] = newValue
        save()
        return total()
    }

    fun total(): Int = counts.values.sum()

    fun getBreakdown(): Map<Category, Int> = counts.toMap()

    fun clear() {
        counts.clear()
        save()
    }

    private fun save() {
        val json = JSONObject()
        counts.forEach { (category, count) ->
            json.put(category.name, count)
        }
        // Persist via a simple helper; using OverlayRepository's prefs for simplicity.
        repository.saveCounterState(json.toString())
    }

    private fun load() {
        val jsonString = repository.loadCounterState()
        if (jsonString.isBlank()) return
        try {
            val json = JSONObject(jsonString)
            Category.values().forEach { category ->
                val value = json.optInt(category.name, 0)
                if (value > 0) counts[category] = value
            }
        } catch (e: Exception) {
            counts.clear()
        }
    }
}
