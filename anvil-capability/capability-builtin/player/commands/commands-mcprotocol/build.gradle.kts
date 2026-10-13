plugins {
	id("module-assembly")
	id("module-adapter")
}

description = "MCProtocolLib worker side of the built-in commands capability, with one segment per release range"

dependencies {
	implementation(projects.anvilCapability.capabilityProtocolApi)
	implementation(projects.anvilCapability.capabilityBuiltin.player.commands.commandsApi)

	// The family root embeds the common code into its shaded JAR, which every worker class path holds.
	compileOnly(projects.anvilCapability.capabilityBuiltin.player.commands.commandsCommon)

	testImplementation(projects.anvilCapability.capabilityBuiltin.player.commands.commandsCommon)
}
