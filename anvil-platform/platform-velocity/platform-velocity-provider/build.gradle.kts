plugins {
	id("platform-provider")
	id("publication")
}

description = "Velocity platform provider for Anvil"

dependencies {
	api(projects.anvilPlatform.platformApi)

	implementation(libs.jackson.databind)
	implementation(libs.jackson.toml)

	platformAgent(projects.anvilPlatform.platformVelocity.platformVelocityAgent)
}
