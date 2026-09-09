plugins {
	alias(libs.plugins.toolkit.architecture)
	id("unit")
}

description = "HTTP artifact acquisition and verification for Anvil"

dependencies {
	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilEnvironment.provisioning.provisioningArtifact.artifactApi)

	testImplementation(projects.anvilApi)
	testImplementation(projects.anvilEnvironment.cache)
	testImplementation(projects.anvilEnvironment.cache.cacheApi)
	testImplementation(projects.anvilEnvironment.provisioning.provisioningArtifact.artifactApi)
}
