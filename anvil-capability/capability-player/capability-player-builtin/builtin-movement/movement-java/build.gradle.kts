plugins {
	id("module-java")
	id("packaging-publication")
}

description = "Java Edition side of the built-in movement capability: its worker extension over the player's protocol, with the vanilla client's own player on the physics kit"

dependencies {
	implementation(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinMovement.movementApi)
	implementation(projects.anvilPlayer.playerApi)
	implementation(projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaApi)
	implementation(projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaBlock)
	implementation(projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaPhysics)

	testImplementation(testFixtures(projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaPhysics))
	testImplementation(testFixtures(projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaWorker))
}
