plugins {
	alias(libs.plugins.toolkit.architecture)
	id("unit")
}

description = "Scenario validation, setup, and lifecycle coordination through the core API"

dependencies {
	compileOnly(projects.anvilApi)

	testImplementation(projects.anvilApi)
}
