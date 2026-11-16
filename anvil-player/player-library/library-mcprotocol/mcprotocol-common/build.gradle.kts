import me.whereareiam.anvil.buildlogic.matrix.WriteVerifiedReleases

plugins {
	id("module-java")
	// Worker contract tests start real workers on each release's locked closure instead of downloading it.
	id("test-library-closures")
}

description = "MCProtocolLib library provider, release data, worker host and the MCProtocolLib-free worker shell"

dependencies {
	implementation(libs.jackson.databind)
	implementation(libs.jackson.parameters)
	implementation(libs.jackson.toml)
	implementation(libs.minecraft.auth)
	implementation(libs.slf4j.api)

	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilPlayer.playerApi)
	compileOnly(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolApi)
	compileOnly(projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaApi)
	compileOnly(projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaWorker)
	compileOnly(projects.anvilPlayer.playerWorker)

	testImplementation(projects.anvilApi)
	testImplementation(projects.anvilCapability.capabilityCommon)
	testImplementation(projects.anvilCapability.capabilityDefault)
	testImplementation(projects.anvilCapability.capabilityPlayer.capabilityPlayerApi)
	testImplementation(projects.anvilLauncher)
	testImplementation(projects.anvilPlayer.playerApi)
	testImplementation(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolApi)
	testImplementation(projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaApi)
	testImplementation(projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaWorker)
	testImplementation(projects.anvilPlayer.playerWorker)

	testRuntimeOnly(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolJava)
	testRuntimeOnly(libs.slf4j.simple)
}

// The release data lives at the family root, where build-logic reads it for segments. The runtime reads this copy,
// with each release's verified versions written from the live matrix at the repository root.
val writeReleaseData = tasks.register<WriteVerifiedReleases>("writeReleaseData") {
	description = "Writes the release data with the Minecraft versions live-matrix.toml verifies."
	releases.set(layout.projectDirectory.file("../mcprotocol-releases.toml"))
	matrix.set(layout.settingsDirectory.file("live-matrix.toml"))
	destination.set(layout.buildDirectory.file("generated/release-data/mcprotocol-releases.toml"))
}

tasks.named<ProcessResources>("processResources") {
	from(writeReleaseData) {
		into("me/whereareiam/anvil/protocol/mcprotocol")
	}
}
