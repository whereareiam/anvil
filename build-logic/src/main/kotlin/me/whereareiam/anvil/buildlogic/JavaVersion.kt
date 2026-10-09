package me.whereareiam.anvil.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

private const val versionCatalogName = "libs"
private const val javaVersionName = "java"

fun Project.anvilJavaVersion(): Int = extensions
	.getByType<VersionCatalogsExtension>()
	.named(versionCatalogName)
	.findVersion(javaVersionName)
	.get()
	.requiredVersion
	.toInt()
