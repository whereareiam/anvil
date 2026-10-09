plugins {
	`kotlin-dsl`
	id("module-gradle-plugin")
}

description = "Shared Anvil Gradle declarations, runtime inputs, and engine configuration"

dependencies {
	api(projects.anvilIntegration.integrationGradle.gradleArtifacts)
}
