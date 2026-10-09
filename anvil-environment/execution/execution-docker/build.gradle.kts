plugins {
	id("module-java")
	id("test-fixtures-consumer")
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
