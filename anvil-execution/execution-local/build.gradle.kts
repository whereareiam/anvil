plugins {
	alias(libs.plugins.toolkit.architecture)
	id("unit")
}

dependencies {
	compileOnly(projects.anvilExecution.executionApi)
	compileOnly(projects.anvilProvisioning.provisioningJava.api)

	testImplementation(projects.anvilExecution.executionApi)
	testImplementation(projects.anvilProvisioning.provisioningCache)
}
