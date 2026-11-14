plugins {
	id("module-java")
	id("packaging-publication")
}

description = "Java Edition side of the built-in vitals capability: its worker extension over the player's protocol"

dependencies {
	implementation(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinVitals.vitalsApi)
	implementation(projects.anvilPlayer.playerApi)
	implementation(projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaApi)
}
