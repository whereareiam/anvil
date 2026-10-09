package me.whereareiam.anvil.buildlogic.jvm

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

private const val catalogName = "libs"

/**
 * The build's shared version catalog. Precompiled convention plugins have no generated `libs` accessor,
 * so every convention reads it here.
 */
internal val Project.catalog: VersionCatalog
	get() = extensions.getByType<VersionCatalogsExtension>().named(catalogName)

/**
 * Reads a catalog version that names a Java feature release, such as `java` or `java-in-server`.
 */
internal fun VersionCatalog.featureVersion(alias: String): Int = findVersion(alias)
	.orElseThrow { GradleException("The $catalogName catalog has no '$alias' version") }
	.requiredVersion
	.toInt()

/**
 * Reads a catalog library by alias.
 */
internal fun VersionCatalog.library(alias: String): Provider<MinimalExternalModuleDependency> = findLibrary(alias)
	.orElseThrow { GradleException("The $catalogName catalog has no '$alias' library") }
