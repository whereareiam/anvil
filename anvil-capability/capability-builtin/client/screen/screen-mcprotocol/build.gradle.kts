plugins {
	id("module-assembly")
	id("module-adapter")
}

description = "MCProtocolLib worker side of the built-in screen capability, with one segment per release range"

dependencies {
	implementation(projects.anvilCapability.capabilityProtocolApi)
	implementation(projects.anvilCapability.capabilityBuiltin.client.screen.screenApi)

	// The family root embeds the common code into its shaded JAR, which every worker class path holds.
	compileOnly(projects.anvilCapability.capabilityBuiltin.client.screen.screenCommon)

	testImplementation(projects.anvilCapability.capabilityBuiltin.client.screen.screenCommon)
}
