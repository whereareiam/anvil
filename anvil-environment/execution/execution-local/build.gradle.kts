plugins {
	alias(libs.plugins.toolkit.architecture)
	id("unit")
}

dependencies {
	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilEnvironment.execution.executionApi)

	testImplementation(projects.anvilEnvironment.execution.executionApi)
}
