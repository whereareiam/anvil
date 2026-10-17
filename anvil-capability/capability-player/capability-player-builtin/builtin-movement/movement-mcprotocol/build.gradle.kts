plugins {
	id("module-assembly")
	id("module-adapter")
}

description = "MCProtocolLib worker side of the built-in movement capability, with one segment per release range"

dependencies {
	implementation(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinMovement.movementApi)
	implementation(projects.anvilPlayer.playerApi)

	// The family root embeds the common code into its shaded JAR, which every worker class path holds.
	compileOnly(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinMovement.movementCommon)

	testImplementation(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinMovement.movementCommon)
}
