plugins {
	id("module-api")
}

description = "Contracts of the workbench a scenario's processes run on, and the facts it reports"

dependencies {
	api(projects.anvilApi)
	api(projects.anvilEnvironment.execution.executionApi)
	api(projects.anvilEnvironment.provisioning.provisioningArtifact.artifactApi)
	api(projects.anvilEnvironment.provisioning.provisioningJava.javaApi)
	api(projects.anvilEnvironment.provisioning.provisioningWorkspace.workspaceApi)
}
