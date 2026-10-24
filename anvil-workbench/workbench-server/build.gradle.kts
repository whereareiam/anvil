plugins {
	id("module-assembly")
	id("packaging-shaded-jar")
}

description = "The workbench JAR: serves engines on its own machine and pairs engines with workbenches"

toolkitPublish {
	artifactId.set("workbench-server")
}

dependencies {
	implementation(projects.anvilApi)
	implementation(projects.anvilEnvironment.execution.executionApi)
	implementation(projects.anvilWorkbench)
	implementation(projects.anvilWorkbench.workbenchApi)
	implementation(projects.anvilWorkbench.workbenchProtocol)
	implementation(libs.jackson.databind)
	implementation(libs.jackson.toml)

	compileOnly(projects.anvilEngine)
	compileOnly(projects.anvilWorkbench.workbenchRemote)

	embedded(projects.anvilApi) { isTransitive = false }
	embedded(projects.anvilEngine) { isTransitive = false }
	embedded(projects.anvilEnvironment.cache) { isTransitive = false }
	embedded(projects.anvilEnvironment.cache.cacheApi) { isTransitive = false }
	embedded(projects.anvilEnvironment.execution.executionApi) { isTransitive = false }
	embedded(projects.anvilEnvironment.execution.executionDocker)
	embedded(projects.anvilEnvironment.execution.executionLocal) { isTransitive = false }
	embedded(projects.anvilEnvironment.execution.executionManaged) { isTransitive = false }
	embedded(projects.anvilEnvironment.provisioning.provisioningArtifact) { isTransitive = false }
	embedded(projects.anvilEnvironment.provisioning.provisioningArtifact.artifactApi) { isTransitive = false }
	embedded(projects.anvilEnvironment.provisioning.provisioningJava) { isTransitive = false }
	embedded(projects.anvilEnvironment.provisioning.provisioningJava.javaApi) { isTransitive = false }
	embedded(projects.anvilEnvironment.provisioning.provisioningWorkspace) { isTransitive = false }
	embedded(projects.anvilEnvironment.provisioning.provisioningWorkspace.workspaceApi) { isTransitive = false }
	embedded(projects.anvilWorkbench) { isTransitive = false }
	embedded(projects.anvilWorkbench.workbenchApi) { isTransitive = false }
	embedded(projects.anvilWorkbench.workbenchCommon) { isTransitive = false }
	embedded(projects.anvilWorkbench.workbenchProtocol) { isTransitive = false }
	embedded(projects.anvilWorkbench.workbenchRemote) { isTransitive = false }
	embedded(libs.commons.compress)
	embedded(libs.jackson.databind)
	embedded(libs.jackson.parameters)
	embedded(libs.jackson.toml)
	embedded(libs.slf4j.api)
	embedded(libs.slf4j.simple)

	testImplementation(projects.anvilEngine)
	testImplementation(projects.anvilEnvironment.cache)
	testImplementation(projects.anvilEnvironment.execution.executionLocal)
	testImplementation(projects.anvilEnvironment.execution.executionManaged)
	testImplementation(projects.anvilEnvironment.provisioning.provisioningArtifact)
	testImplementation(projects.anvilEnvironment.provisioning.provisioningJava)
	testImplementation(projects.anvilEnvironment.provisioning.provisioningWorkspace)
	testImplementation(projects.anvilWorkbench.workbenchCommon)
	testImplementation(projects.anvilWorkbench.workbenchRemote)
}

tasks.named<Jar>("shadowJar") {
	archiveBaseName.set("anvil-workbench")
	manifest {
		attributes("Main-Class" to "me.whereareiam.anvil.workbench.server.WorkbenchServerMain")
	}
}
