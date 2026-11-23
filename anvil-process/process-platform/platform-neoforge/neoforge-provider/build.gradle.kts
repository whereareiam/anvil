plugins {
	id("packaging-publication")
	id("module-platform-provider")
}

description = "NeoForge platform provider for Anvil"

toolkitPublish {
	artifactId.set("platform-neoforge-provider")
}

dependencies {
	api(projects.anvilProcess.processApi)

	platformAgent(projects.anvilProcess.processPlatform.platformNeoforge.neoforgeAgent)

	testImplementation(projects.anvilEnvironment.provisioning.provisioningWorkspace)
}
