plugins {
	id("api")
}

description = "Protocol-backed player capability providers, channels, and native worker contracts"

dependencies {
	api(projects.anvilCapability.capabilityApi)
}
