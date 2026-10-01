plugins {
	`kotlin-dsl`
	alias(libs.plugins.toolkit.architecture)
	id("gradle-plugin")
}

description = "Maps the Anvil Gradle configuration to engine properties for execution adapters"

dependencies {
	api(projects.anvilIntegration.integrationGradle.gradleBase)

	compileOnly(projects.anvilLauncher)
}
