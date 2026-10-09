plugins {
	id("module-platform-provider")
	id("packaging-publication")
}

description = "Spigot platform provider for Anvil"

dependencies {
	api(projects.anvilPlatform.platformApi)

	implementation(libs.jackson.yaml)

	platformAgent(projects.anvilPlatform.platformBukkit.platformBukkitAgent)
}
