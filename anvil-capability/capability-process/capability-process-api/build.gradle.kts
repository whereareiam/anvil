plugins {
	id("module-api")
}

// The process group only organizes: its API stays in the capability family, whose common code composes the
// capabilities of every participant.
architecture {
	family = ":anvil-capability"
}

description = "Agent-backed process capability provider contracts"

toolkitPublish {
	artifactId.set("capability-agent-api")
}

dependencies {
	api(projects.anvilCapability.capabilityApi)
}
