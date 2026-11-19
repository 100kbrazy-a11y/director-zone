package com.directorzone.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object Store {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun dir(ctx: Context) = File(ctx.filesDir, "projects").apply { mkdirs() }

    fun save(ctx: Context, p: Project) { File(dir(ctx), "${p.id}.json").writeText(json.encodeToString(p.copy(updated = System.currentTimeMillis()))) }

    fun list(ctx: Context): List<Project> = (dir(ctx).listFiles() ?: emptyArray()).mapNotNull {
        try { json.decodeFromString<Project>(it.readText()) } catch (e: Exception) { null }
    }.sortedByDescending { it.updated }

    fun delete(ctx: Context, id: String) { File(dir(ctx), "$id.json").delete() }
}
