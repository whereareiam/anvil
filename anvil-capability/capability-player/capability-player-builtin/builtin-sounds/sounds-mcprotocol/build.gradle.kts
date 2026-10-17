plugins {
	id("module-assembly")
	id("module-adapter")
}

description = "MCProtocolLib worker side of the built-in sounds capability, with one segment per release range"

dependencies {
	implementation(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinSounds.soundsApi)
	implementation(projects.anvilPlayer.playerApi)

	// The family root embeds the common code into its shaded JAR, which every worker class path holds.
	compileOnly(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinSounds.soundsCommon)

	testImplementation(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinSounds.soundsCommon)
}
