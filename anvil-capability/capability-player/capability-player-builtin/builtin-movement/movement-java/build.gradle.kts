plugins {
	id("module-java")
	id("packaging-publication")
}

description = "Java Edition side of the built-in movement capability: its worker extension over the player's protocol"

dependencies {
	implementation(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinMovement.movementApi)
	implementation(projects.anvilPlayer.playerApi)
	implementation(projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaApi)
}
