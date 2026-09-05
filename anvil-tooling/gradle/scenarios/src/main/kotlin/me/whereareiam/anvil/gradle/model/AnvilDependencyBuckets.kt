package me.whereareiam.anvil.gradle.model

import me.whereareiam.anvil.gradle.type.AnvilDependencyBucket
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

internal class AnvilDependencyBuckets(
    val framework: Configuration,
    val launcher: Configuration,
    val capabilities: Configuration,
    val protocols: Configuration,
    val platforms: Configuration,
) {
    companion object {
        fun create(project: Project): AnvilDependencyBuckets = AnvilDependencyBuckets(
            project.createBucket(AnvilDependencyBucket.FRAMEWORK),
            project.createBucket(AnvilDependencyBucket.LAUNCHER),
            project.createBucket(AnvilDependencyBucket.CAPABILITIES),
            project.createBucket(AnvilDependencyBucket.PROTOCOLS),
            project.createBucket(AnvilDependencyBucket.PLATFORMS),
        )
    }
}

private fun Project.createBucket(bucket: AnvilDependencyBucket): Configuration =
    configurations.create(bucket.configurationName) {
        description = bucket.description
        isCanBeConsumed = false
        isCanBeResolved = false
    }
