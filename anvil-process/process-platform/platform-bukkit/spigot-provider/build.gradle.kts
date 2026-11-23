plugins {
	id("packaging-publication")
	id("module-platform-provider")
}

description = "Spigot platform provider for Anvil"

toolkitPublish {
	artifactId.set("platform-spigot-provider")
}

dependencies {
	api(projects.anvilProcess.processApi)

	implementation(libs.jackson.yaml)

	platformAgent(projects.anvilProcess.processPlatform.platformBukkit.bukkitAgent)

	testImplementation(projects.anvilEnvironment.provisioning.provisioningWorkspace)
}
