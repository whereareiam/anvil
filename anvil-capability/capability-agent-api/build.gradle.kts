plugins {
	alias(libs.plugins.toolkit.architecture)
	alias(libs.plugins.toolkit.publish.maven)
	id("unit")
}

description = "Agent-backed process and player capability provider contracts"

dependencies {
	api(projects.anvilCapability.capabilityApi)
}
