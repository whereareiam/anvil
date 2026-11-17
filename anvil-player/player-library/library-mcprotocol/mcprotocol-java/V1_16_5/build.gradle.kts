plugins {
	id("module-adapter-segment")
}

dependencies {
	implementation(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolAdventure)
	implementation(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolApi)
	implementation(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolNetty)

	testImplementation(testFixtures(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolApi))
}
