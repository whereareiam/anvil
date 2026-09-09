plugins {
	alias(libs.plugins.toolkit.architecture)
	id("unit")
}

description = "Managed Minecraft process generations and workspace lifecycle"

dependencies {
	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilEnvironment.execution.executionApi)

	testImplementation(projects.anvilApi)
	testImplementation(projects.anvilEnvironment.execution.executionApi)
	testImplementation(projects.anvilEnvironment.execution.executionLocal)
}
