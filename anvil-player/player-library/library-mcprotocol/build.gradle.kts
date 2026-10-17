plugins {
	id("module-assembly")
	id("packaging-shaded-root")
	id("module-library")
}

// Owning mcprotocol-api makes this family its own API family, so the protocol library API it implements is shared
// explicitly, and the Java Edition protocol its client segments translate is declared as a sibling family's API.
architecture {
	sharedApis = setOf(projects.anvilPlayer.playerApi.path)
	dependsOnApis = setOf(projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaApi.path)
}

description = "Version-isolated MCProtocolLib clients and workers for Anvil"

toolkitPublish {
	artifactId.set("protocol-mcprotocol")
}

dependencies {
	api(projects.anvilPlayer.playerApi)

	implementation(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolApi)
	implementation(libs.jackson.databind)
	implementation(libs.jackson.parameters)
	implementation(libs.jackson.toml)
	implementation(libs.minecraft.auth)
	implementation(libs.slf4j.api)

	embedded(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolCommon) { isTransitive = false }

	runtimeOnly(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolJava)
	runtimeOnly(libs.slf4j.simple)
}
