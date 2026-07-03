package com.android.smarthome.firebase

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.math.BigDecimal

/** Semantic JSON comparison for resolving idempotent RTDB conditional-write collisions. */
object JsonPayloadComparator {
    fun equivalentIgnoringRootFields(
        first: String,
        second: String,
        ignoredRootFields: Set<String>
    ): Boolean {
        return try {
            equivalent(
                JSONTokener(first).nextValue(),
                JSONTokener(second).nextValue(),
                ignoredRootFields
            )
        } catch (_: Exception) {
            false
        }
    }

    private fun equivalent(first: Any?, second: Any?, ignoredFields: Set<String>): Boolean {
        if (first === JSONObject.NULL || first == null) {
            return second === JSONObject.NULL || second == null
        }
        if (second === JSONObject.NULL || second == null) return false
        if (first is JSONObject && second is JSONObject) {
            val firstKeys = first.keys().asSequence().map { it.toString() }
                .filterNot(ignoredFields::contains).toSet()
            val secondKeys = second.keys().asSequence().map { it.toString() }
                .filterNot(ignoredFields::contains).toSet()
            if (firstKeys != secondKeys) return false
            return firstKeys.all { key ->
                equivalent(first.get(key), second.get(key), emptySet())
            }
        }
        if (first is JSONArray && second is JSONArray) {
            if (first.length() != second.length()) return false
            return (0 until first.length()).all { index ->
                equivalent(first.get(index), second.get(index), emptySet())
            }
        }
        if (first is Number && second is Number) {
            return try {
                BigDecimal(first.toString()).compareTo(BigDecimal(second.toString())) == 0
            } catch (_: NumberFormatException) {
                first.toDouble() == second.toDouble()
            }
        }
        return first == second
    }
}
