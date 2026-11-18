plugins {
	id("module-java")
	id("packaging-publication")
}

description = "Java Edition side of the built-in movement capability: its worker extension over the player's protocol, with the vanilla client's own player on the physics kit"

dependencies {
	implementation(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinMovement.movementApi)
	implementation(projects.anvilEdition.editionJava.javaData.dataBlock)
	implementation(projects.anvilEdition.editionJava.javaPhysics.physicsEntity)
	implementation(projects.anvilEdition.editionJava.javaPhysics.physicsGeometry)
	implementation(projects.anvilEdition.editionJava.javaProtocol.protocolApi)
	implementation(projects.anvilPlayer.playerApi)

	testImplementation(testFixtures(projects.anvilEdition.editionJava.javaPhysics.physicsEntity))
	testImplementation(testFixtures(projects.anvilEdition.editionJava.javaProtocol.protocolWorker))
}
