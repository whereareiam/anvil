plugins {
	id("module-assembly")
	id("module-adapter")
}

description = "MCProtocolLib worker side of the built-in messages capability, with one segment per release range"

dependencies {
	implementation(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinMessages.messagesApi)
	implementation(projects.anvilPlayer.playerApi)

	// The family root embeds the common code into its shaded JAR, which every worker class path holds.
	compileOnly(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinMessages.messagesCommon)

	testImplementation(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinMessages.messagesCommon)
}
