plugins {
	id("module-api")
}

// The player group only organizes: its API stays in the capability family, whose common code composes the
// capabilities of every participant.
architecture {
	family = ":anvil-capability"
}

description = "Protocol-backed player capability providers, channels, and native worker contracts"

toolkitPublish {
	artifactId.set("capability-protocol-api")
}

dependencies {
	api(projects.anvilCapability.capabilityApi)
}
