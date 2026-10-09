plugins {
	`kotlin-dsl`
	id("gradle-plugin")
}

description = "Shared Anvil Gradle declarations, runtime inputs, and engine configuration"

dependencies {
	api(projects.anvilIntegration.integrationGradle.gradleArtifacts)
}
