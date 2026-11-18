plugins {
	id("module-capability")
}

architecture {
	sharedApis = setOf(
		projects.anvilCapability.capabilityApi.path,
		projects.anvilCapability.capabilityPlayer.capabilityPlayerApi.path,
	)
	dependsOnApis = setOf(
		projects.anvilEdition.editionJava.javaData.dataBlock.path,
		projects.anvilEdition.editionJava.javaPhysics.physicsEntity.path,
		projects.anvilEdition.editionJava.javaPhysics.physicsGeometry.path,
		projects.anvilEdition.editionJava.javaProtocol.protocolApi.path,
		projects.anvilPlayer.playerApi.path,
	)
}

description = "Public wiring bundle for the built-in movement capability"

dependencies {
	api(projects.anvilCapability.capabilityPlayer.capabilityPlayerApi)
}
