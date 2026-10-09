plugins {
	id("jvm")
}

description = "Scenario validation, setup, and lifecycle coordination through the core API"

dependencies {
	compileOnly(projects.anvilApi)

	testImplementation(projects.anvilApi)
}
