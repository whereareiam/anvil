plugins {
	id("module-java")
	id("packaging-publication")
}

description = "Java Edition side of the built-in messages capability: its worker extension over the player's protocol"

dependencies {
	implementation(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinMessages.messagesApi)
	implementation(projects.anvilPlayer.playerApi)
	implementation(projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaApi)
}
