plugins {
	id("module-assembly")
	id("module-adapter")
}

description = "MCProtocolLib worker side of the built-in screen capability, with one segment per release range"

dependencies {
	implementation(projects.anvilCapability.capabilityPlayer.capabilityPlayerApi)
	implementation(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinScreen.screenApi)

	// The family root embeds the common code into its shaded JAR, which every worker class path holds.
	compileOnly(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinScreen.screenCommon)

	testImplementation(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinScreen.screenCommon)
}
