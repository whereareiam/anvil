plugins {
	id("module-platform-provider")
	id("packaging-publication")
}

description = "Lumi platform provider for Anvil"

dependencies {
	api(projects.anvilPlatform.platformApi)

	implementation(libs.jackson.toml)
	implementation(libs.jackson.yaml)

	platformAgent(projects.anvilPlatform.platformLumi.platformLumiAgent)
}
