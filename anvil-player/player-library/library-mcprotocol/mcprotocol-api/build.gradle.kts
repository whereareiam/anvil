plugins {
	id("module-api")
}

description = "Release-neutral client port that MCProtocolLib client segments implement for the Anvil worker"

dependencies {
	api(projects.anvilApi)
	api(projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaApi)

	compileOnly(libs.adventure.api)

	testImplementation(libs.adventure.api)
}
