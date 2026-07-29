package com.whiskeymike.wmpoketrap.data

import android.content.Context

object PokemonCatalog {
    @Volatile private var cached: List<String>? = null

    fun all(context: Context): List<String> {
        cached?.let { return it }
        val text = context.assets.open("pokemon_names.txt").bufferedReader().use { it.readText() }
        val list = text.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        cached = list
        return list
    }
}
