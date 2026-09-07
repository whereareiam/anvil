plugins {
	alias(libs.plugins.toolkit.architecture)
	id("unit")
}

dependencies {
	implementation(libs.jackson.databind)
	implementation(libs.docker.java)
	implementation(libs.docker.java.transport.zerodep)

	compileOnly(projects.anvilExecution.executionApi)
	compileOnly(projects.anvilProvisioning.provisioningJava.api)

	testImplementation(projects.anvilExecution.executionApi)
	testImplementation(projects.anvilProvisioning.provisioningCache)
}
