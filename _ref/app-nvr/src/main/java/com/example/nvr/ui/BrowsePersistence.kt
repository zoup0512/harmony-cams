package com.example.nvr.ui

import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * "浏览" 功能的持久化层：
 * - 用 SharedPreferences 存 JSON 数组，避免引入 Room/数据库迁移成本。
 * - 收藏（BrowseFavorite）和网络源（BrowseNetworkSource）分两个键。
 */
private const val PREF_KEY_BROWSE_FAVORITES = "browse_favorites_json"
private const val PREF_KEY_BROWSE_NETWORK_SOURCES = "browse_network_sources_json"
private const val TAG = "BrowsePersistence"

fun loadBrowseFavorites(prefs: SharedPreferences): List<BrowseFavorite> {
    val raw = prefs.getString(PREF_KEY_BROWSE_FAVORITES, null) ?: return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { idx ->
            val obj = array.optJSONObject(idx) ?: return@mapNotNull null
            val sourceName = obj.optString("source", BrowseSourceKind.NETWORK_URL.name)
            val source = runCatching { BrowseSourceKind.valueOf(sourceName) }.getOrDefault(BrowseSourceKind.NETWORK_URL)
            BrowseFavorite(
                id = obj.optString("id"),
                title = obj.optString("title"),
                url = obj.optString("url"),
                source = source,
                addedAtMs = obj.optLong("addedAtMs", System.currentTimeMillis()),
            )
        }
    }.onFailure { Log.e(TAG, "loadBrowseFavorites failed: ${it.message}") }
        .getOrDefault(emptyList())
}

fun saveBrowseFavorites(prefs: SharedPreferences, favorites: List<BrowseFavorite>) {
    val array = JSONArray()
    favorites.forEach { fav ->
        array.put(
            JSONObject().apply {
                put("id", fav.id)
                put("title", fav.title)
                put("url", fav.url)
                put("source", fav.source.name)
                put("addedAtMs", fav.addedAtMs)
            }
        )
    }
    prefs.edit().putString(PREF_KEY_BROWSE_FAVORITES, array.toString()).apply()
}

fun addBrowseFavorite(prefs: SharedPreferences, favorite: BrowseFavorite): List<BrowseFavorite> {
    val current = loadBrowseFavorites(prefs)
    if (current.any { it.url == favorite.url }) return current
    val next = current + favorite
    saveBrowseFavorites(prefs, next)
    return next
}

fun removeBrowseFavorite(prefs: SharedPreferences, favoriteId: String): List<BrowseFavorite> {
    val current = loadBrowseFavorites(prefs)
    val next = current.filterNot { it.id == favoriteId }
    saveBrowseFavorites(prefs, next)
    return next
}

fun loadBrowseNetworkSources(prefs: SharedPreferences): List<BrowseNetworkSource> {
    val raw = prefs.getString(PREF_KEY_BROWSE_NETWORK_SOURCES, null) ?: return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { idx ->
            val obj = array.optJSONObject(idx) ?: return@mapNotNull null
            BrowseNetworkSource(
                id = obj.optString("id"),
                title = obj.optString("title"),
                url = obj.optString("url"),
                username = obj.optString("username", ""),
                password = obj.optString("password", ""),
                addedAtMs = obj.optLong("addedAtMs", System.currentTimeMillis()),
            )
        }
    }.onFailure { Log.e(TAG, "loadBrowseNetworkSources failed: ${it.message}") }
        .getOrDefault(emptyList())
}

fun saveBrowseNetworkSources(prefs: SharedPreferences, sources: List<BrowseNetworkSource>) {
    val array = JSONArray()
    sources.forEach { src ->
        array.put(
            JSONObject().apply {
                put("id", src.id)
                put("title", src.title)
                put("url", src.url)
                put("username", src.username)
                put("password", src.password)
                put("addedAtMs", src.addedAtMs)
            }
        )
    }
    prefs.edit().putString(PREF_KEY_BROWSE_NETWORK_SOURCES, array.toString()).apply()
}

fun addBrowseNetworkSource(prefs: SharedPreferences, source: BrowseNetworkSource): List<BrowseNetworkSource> {
    val current = loadBrowseNetworkSources(prefs)
    if (current.any { it.url == source.url }) return current
    val next = current + source
    saveBrowseNetworkSources(prefs, next)
    return next
}

fun removeBrowseNetworkSource(prefs: SharedPreferences, sourceId: String): List<BrowseNetworkSource> {
    val current = loadBrowseNetworkSources(prefs)
    val next = current.filterNot { it.id == sourceId }
    saveBrowseNetworkSources(prefs, next)
    return next
}
