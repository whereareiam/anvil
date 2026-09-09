plugins {
	alias(libs.plugins.toolkit.architecture)
	alias(libs.plugins.toolkit.publish.maven)
	id("unit")
}

description = "Protocol-backed player capability providers, channels, and native worker contracts"

dependencies {
	api(projects.anvilCapability.capabilityApi)
}
