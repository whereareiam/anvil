plugins {
	id("module-java")
	id("packaging-publication")
}

description = "Java Edition side of the built-in world capability: its worker extension over the player's protocol"

dependencies {
	implementation(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinWorld.worldApi)
	implementation(projects.anvilPlayer.playerApi)
	implementation(projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaApi)
}
