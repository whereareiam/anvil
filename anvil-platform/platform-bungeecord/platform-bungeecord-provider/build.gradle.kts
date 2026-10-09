plugins {
	id("platform-provider")
	id("publication")
}

description = "BungeeCord platform provider for Anvil"

dependencies {
	api(projects.anvilPlatform.platformApi)

	implementation(libs.jackson.yaml)

	platformAgent(projects.anvilPlatform.platformBungeecord.platformBungeecordAgent)
}
