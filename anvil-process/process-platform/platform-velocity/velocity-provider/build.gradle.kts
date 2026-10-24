plugins {
	id("module-platform-provider")
	id("packaging-publication")
}

description = "Velocity platform provider for Anvil"

toolkitPublish {
	artifactId.set("platform-velocity-provider")
}

dependencies {
	api(projects.anvilProcess.processApi)

	implementation(libs.jackson.databind)
	implementation(libs.jackson.toml)

	platformAgent(projects.anvilProcess.processPlatform.platformVelocity.velocityAgent)

	testImplementation(projects.anvilEnvironment.provisioning.provisioningWorkspace)
}
