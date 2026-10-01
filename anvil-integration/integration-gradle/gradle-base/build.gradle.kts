plugins {
	`kotlin-dsl`
	alias(libs.plugins.toolkit.architecture)
	id("gradle-plugin")
}

description = "Shared Anvil Gradle declarations, runtime inputs, and engine configuration"

dependencies {
	api(projects.anvilIntegration.integrationGradle.gradleArtifacts)
}
