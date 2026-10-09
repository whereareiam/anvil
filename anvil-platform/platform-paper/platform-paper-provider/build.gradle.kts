plugins {
	id("platform-provider")
	id("publication")
}

description = "Paper platform provider for Anvil"

dependencies {
	api(projects.anvilPlatform.platformApi)

	implementation(libs.jackson.databind)
	implementation(libs.jackson.yaml)

	platformAgent(projects.anvilPlatform.platformBukkit.platformBukkitAgent)
}
