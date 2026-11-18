package dev.kyralabs.pingogram.core.data.backup

import org.json.JSONArray
import org.json.JSONObject

/**
 * The `settings` section of a backup's body: each setting by its key, with its type, which JSON's numbers would lose,
 * and its value. A value of another type is left out, and one that does not read back is skipped.
 */
internal fun Map<String, Any>.toSettingsJson(): JSONObject {
    val json = JSONObject()
    forEach { (name, value) ->
        val type =
            when (value) {
                is Boolean -> BOOLEAN
                is Int -> INT
                is Long -> LONG
                is Float -> FLOAT
                is Double -> DOUBLE
                is String -> STRING
                is Set<*> -> STRING_SET
                else -> null
            }
        val encoded = if (value is Set<*>) JSONArray(value.filterIsInstance<String>().sorted()) else value
        if (type != null) json.put(name, JSONObject().put(TYPE, type).put(VALUE, encoded))
    }
    return json
}

/** The settings of a backup's `settings` section ([toSettingsJson]). */
internal fun JSONObject.toSettings(): Map<String, Any> =
    keys().asSequence().mapNotNull { name -> optJSONObject(name)?.setting()?.let { name to it } }.toMap()

private fun JSONObject.setting(): Any? =
    when (optString(TYPE)) {
        BOOLEAN -> opt(VALUE) as? Boolean
        INT -> (opt(VALUE) as? Number)?.toInt()
        LONG -> (opt(VALUE) as? Number)?.toLong()
        FLOAT -> (opt(VALUE) as? Number)?.toFloat()
        DOUBLE -> (opt(VALUE) as? Number)?.toDouble()
        STRING -> opt(VALUE) as? String
        STRING_SET -> optJSONArray(VALUE)?.let { values -> (0 until values.length()).map(values::optString).toSet() }
        else -> null
    }

private const val TYPE = "type"
private const val VALUE = "value"
private const val BOOLEAN = "boolean"
private const val INT = "int"
private const val LONG = "long"
private const val FLOAT = "float"
private const val DOUBLE = "double"
private const val STRING = "string"
private const val STRING_SET = "string_set"
