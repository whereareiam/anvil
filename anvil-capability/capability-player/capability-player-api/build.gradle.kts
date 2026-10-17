plugins {
	id("module-api")
}

// The player group only organizes: its API stays in the capability family, whose common code composes the
// capabilities of every participant. Its contexts hand providers the player's own channel.
architecture {
	family = ":anvil-capability"
	dependsOnApis = setOf(projects.anvilPlayer.playerApi.path)
}

description = "Protocol-backed player capability providers and their contexts"

toolkitPublish {
	artifactId.set("capability-protocol-api")
}

dependencies {
	api(projects.anvilCapability.capabilityApi)
	api(projects.anvilPlayer.playerApi)
}
