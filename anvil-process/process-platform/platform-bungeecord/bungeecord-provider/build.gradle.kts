plugins {
	id("module-platform-provider")
	id("packaging-publication")
}

description = "BungeeCord platform provider for Anvil"

toolkitPublish {
	artifactId.set("platform-bungeecord-provider")
}

dependencies {
	api(projects.anvilProcess.processApi)

	implementation(libs.jackson.yaml)

	platformAgent(projects.anvilProcess.processPlatform.platformBungeecord.bungeecordAgent)
}
