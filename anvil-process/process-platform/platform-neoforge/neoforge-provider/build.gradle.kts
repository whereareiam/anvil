plugins {
	id("module-platform-provider")
	id("packaging-publication")
}

description = "NeoForge platform provider for Anvil"

toolkitPublish {
	artifactId.set("platform-neoforge-provider")
}

dependencies {
	api(projects.anvilProcess.processApi)

	platformAgent(projects.anvilProcess.processPlatform.platformNeoforge.neoforgeAgent)
}
