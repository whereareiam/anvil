plugins {
	alias(libs.plugins.toolkit.architecture)
	id("unit")
}

description = "Workspace preparation, persistence, snapshots, and cleanup for Anvil"

dependencies {
	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilEnvironment.provisioning.provisioningWorkspace.workspaceApi)

	testImplementation(projects.anvilApi)
	testImplementation(projects.anvilEnvironment.cache)
	testImplementation(projects.anvilEnvironment.cache.cacheApi)
	testImplementation(projects.anvilEnvironment.provisioning.provisioningWorkspace.workspaceApi)
}
