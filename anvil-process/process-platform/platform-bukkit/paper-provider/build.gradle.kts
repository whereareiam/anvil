plugins {
	id("module-platform-provider")
	id("packaging-publication")
}

description = "Paper platform provider for Anvil"

toolkitPublish {
	artifactId.set("platform-paper-provider")
}

dependencies {
	api(projects.anvilProcess.processApi)

	implementation(libs.jackson.databind)
	implementation(libs.jackson.yaml)

	platformAgent(projects.anvilProcess.processPlatform.platformBukkit.bukkitAgent)

	testImplementation(projects.anvilEnvironment.provisioning.provisioningWorkspace)
}
