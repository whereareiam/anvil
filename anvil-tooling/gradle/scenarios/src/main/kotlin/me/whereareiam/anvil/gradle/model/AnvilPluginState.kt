package me.whereareiam.anvil.gradle.model

import me.whereareiam.anvil.gradle.AnvilExtension
import me.whereareiam.anvil.gradle.internal.AnvilCoordinates
import org.gradle.api.file.FileCollection
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet

class AnvilPluginState internal constructor(
    val capability: AnvilExtension,
    val sourceSet: SourceSet,
    internal val buckets: AnvilDependencyBuckets,
    val frameworkVersion: String,
) {
    val framework get() = buckets.framework
    val launcher get() = buckets.launcher
    val capabilities get() = buckets.capabilities
    val protocols get() = buckets.protocols
    val platforms get() = buckets.platforms

    fun coordinate(artifact: String): String =
        AnvilCoordinates.module(artifact, frameworkVersion)

    fun onArtifact(listener: (String, FileCollection) -> Unit) {
        capability.onArtifact(listener)
    }
}

fun state(project: Project): AnvilPluginState =
    project.extensions.getByType(AnvilPluginState::class.java)
