package com.awlaunch.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONArray
import org.json.JSONObject

class FavoritesRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun observePinned(): Flow<Set<String>> = observeStringSet(KEY_PINNED)
    fun togglePin(key: String) = toggleInSet(KEY_PINNED, key)

    fun observeHidden(): Flow<Set<String>> = observeStringSet(KEY_HIDDEN)
    fun toggleHide(key: String) = toggleInSet(KEY_HIDDEN, key)

    fun observeNames(): Flow<Map<String, String>> = callbackFlow {
        fun read(): Map<String, String> {
            val raw = prefs.getString(KEY_NAMES, null) ?: return emptyMap()
            return try {
                val obj = JSONObject(raw)
                buildMap {
                    obj.keys().forEach { k -> put(k, obj.getString(k)) }
                }
            } catch (_: Throwable) {
                emptyMap()
            }
        }
        trySend(read())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
            if (k == KEY_NAMES) trySend(read())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun setName(key: String, name: String?) {
        val raw = prefs.getString(KEY_NAMES, null) ?: "{}"
        val obj = try { JSONObject(raw) } catch (_: Throwable) { JSONObject() }
        if (name.isNullOrBlank()) obj.remove(key) else obj.put(key, name.trim())
        prefs.edit().putString(KEY_NAMES, obj.toString()).apply()
    }

    fun observeGroups(): Flow<Map<String, Set<String>>> = callbackFlow {
        fun read(): Map<String, Set<String>> {
            val raw = prefs.getString(KEY_GROUPS, null) ?: return emptyMap()
            return try {
                val obj = JSONObject(raw)
                buildMap {
                    obj.keys().forEach { group ->
                        val arr = obj.getJSONArray(group)
                        val set = (0 until arr.length()).mapTo(mutableSetOf()) { arr.getString(it) }
                        put(group, set)
                    }
                }
            } catch (_: Throwable) {
                emptyMap()
            }
        }
        trySend(read())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
            if (k == KEY_GROUPS) trySend(read())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun addAppToGroup(appKey: String, group: String) = mutateGroups { current ->
        current.getOrPut(group.trim()) { mutableSetOf() }.add(appKey)
    }

    fun removeAppFromGroup(appKey: String, group: String) = mutateGroups { current ->
        current[group]?.remove(appKey)
    }

    fun renameGroup(oldName: String, newName: String) = mutateGroups { current ->
        val trimmed = newName.trim()
        if (trimmed.isNotBlank() && trimmed != oldName && oldName in current) {
            current[trimmed] = current.remove(oldName) ?: mutableSetOf()
        }
    }

    fun deleteGroup(name: String) = mutateGroups { current ->
        current.remove(name)
    }

    private fun mutateGroups(transform: (MutableMap<String, MutableSet<String>>) -> Unit) {
        val raw = prefs.getString(KEY_GROUPS, null) ?: "{}"
        val obj = try { JSONObject(raw) } catch (_: Throwable) { JSONObject() }
        val current = mutableMapOf<String, MutableSet<String>>()
        obj.keys().forEach { g ->
            val arr = obj.getJSONArray(g)
            current[g] = (0 until arr.length()).mapTo(mutableSetOf()) { arr.getString(it) }
        }
        transform(current)
        // Auto-prune empty groups.
        current.keys.toList().forEach { if (current[it].isNullOrEmpty()) current.remove(it) }
        val newObj = JSONObject()
        current.forEach { (g, keys) -> newObj.put(g, JSONArray(keys.toList())) }
        prefs.edit().putString(KEY_GROUPS, newObj.toString()).apply()
    }

    private fun observeStringSet(prefKey: String): Flow<Set<String>> = callbackFlow {
        trySend((prefs.getStringSet(prefKey, emptySet()) ?: emptySet()).toSet())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
            if (k == prefKey) trySend((prefs.getStringSet(prefKey, emptySet()) ?: emptySet()).toSet())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private fun toggleInSet(prefKey: String, key: String) {
        val cur = (prefs.getStringSet(prefKey, emptySet()) ?: emptySet()).toMutableSet()
        if (key in cur) cur.remove(key) else cur.add(key)
        prefs.edit().putStringSet(prefKey, cur).apply()
    }

    private companion object {
        const val PREFS_NAME = "awlaunch_prefs"
        const val KEY_PINNED = "pinned_keys"
        const val KEY_HIDDEN = "hidden_keys"
        const val KEY_NAMES = "name_overrides"
        const val KEY_GROUPS = "groups"
    }
}
