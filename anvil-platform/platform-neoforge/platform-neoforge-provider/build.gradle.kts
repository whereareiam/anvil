plugins {
	id("module-platform-provider")
	id("packaging-publication")
}

description = "NeoForge platform provider for Anvil"

dependencies {
	api(projects.anvilPlatform.platformApi)

	platformAgent(projects.anvilPlatform.platformNeoforge.platformNeoforgeAgent)
}
