plugins {
	id("module-assembly")
}

// A workbench holds the machine side of a scenario, so its contracts carry the environment's: the execution
// providers it runs processes with and the provisioning it plans against.
architecture {
	dependsOnApis = setOf(
		projects.anvilEnvironment.cache.cacheApi.path,
		projects.anvilEnvironment.execution.executionApi.path,
		projects.anvilEnvironment.provisioning.provisioningArtifact.artifactApi.path,
		projects.anvilEnvironment.provisioning.provisioningJava.javaApi.path,
		projects.anvilEnvironment.provisioning.provisioningWorkspace.workspaceApi.path,
	)
}

description = "The local workbench: a scenario's machine side assembled in the engine's own JVM"

toolkitPublish {
	artifactId.set("workbench")
}

dependencies {
	api(projects.anvilWorkbench.workbenchApi)

	implementation(projects.anvilEnvironment.cache.cacheApi)

	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilEnvironment.cache)
	compileOnly(projects.anvilEnvironment.execution.executionManaged)
	compileOnly(projects.anvilEnvironment.provisioning.provisioningArtifact)
	compileOnly(projects.anvilEnvironment.provisioning.provisioningJava)
	compileOnly(projects.anvilEnvironment.provisioning.provisioningWorkspace)
	compileOnly(projects.anvilWorkbench.workbenchCommon)

	testImplementation(projects.anvilApi)
	testImplementation(projects.anvilEnvironment.cache)
	testImplementation(projects.anvilEnvironment.execution.executionManaged)
	testImplementation(projects.anvilEnvironment.provisioning.provisioningArtifact)
	testImplementation(projects.anvilEnvironment.provisioning.provisioningJava)
	testImplementation(projects.anvilEnvironment.provisioning.provisioningWorkspace)
	testImplementation(projects.anvilWorkbench.workbenchCommon)
}
