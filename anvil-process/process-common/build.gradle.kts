plugins {
	id("module-java")
}

description = "Platform selection, distribution preparation, and forwarding plans"

dependencies {
	implementation(libs.jackson.databind)
	implementation(libs.jackson.toml)

	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilProcess.processApi)

	testImplementation(projects.anvilApi)
	testImplementation(projects.anvilEnvironment.provisioning.provisioningWorkspace)
	testImplementation(projects.anvilProcess.processApi)
}
