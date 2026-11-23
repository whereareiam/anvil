plugins {
	id("packaging-publication")
	id("module-platform-provider")
}

description = "BungeeCord platform provider for Anvil"

toolkitPublish {
	artifactId.set("platform-bungeecord-provider")
}

dependencies {
	api(projects.anvilProcess.processApi)

	implementation(libs.jackson.yaml)

	platformAgent(projects.anvilProcess.processPlatform.platformBungeecord.bungeecordAgent)

	testImplementation(projects.anvilEnvironment.provisioning.provisioningWorkspace)
}
