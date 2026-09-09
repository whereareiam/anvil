plugins {
	alias(libs.plugins.toolkit.architecture)
	id("unit")
	id("fixtures")
}

dependencies {
	implementation(libs.docker.java)
	implementation(libs.docker.java.transport.zerodep)
	implementation(libs.jackson.databind)

	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilEnvironment.execution.executionApi)

	testImplementation(projects.anvilEnvironment.execution.executionApi)
}

fixtures {
	process()
}
