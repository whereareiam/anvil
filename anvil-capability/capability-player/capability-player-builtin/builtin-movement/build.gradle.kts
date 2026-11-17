plugins {
	id("module-capability")
}

architecture {
	sharedApis = setOf(
		projects.anvilCapability.capabilityApi.path,
		projects.anvilCapability.capabilityPlayer.capabilityPlayerApi.path,
	)
	dependsOnApis = setOf(
		projects.anvilPlayer.playerApi.path,
		projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaApi.path,
		projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaBlock.path,
		projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaGeometry.path,
		projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaPhysics.path,
	)
}

description = "Public wiring bundle for the built-in movement capability"

dependencies {
	api(projects.anvilCapability.capabilityPlayer.capabilityPlayerApi)
}
