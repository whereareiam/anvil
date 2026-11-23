plugins {
	id("packaging-publication")
	id("module-platform-provider")
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
