plugins {
	id("module-api")
}

architecture {
	sharedApis = setOf(projects.anvilPlayer.playerApi.path)
}

description = "Java Edition worker kit that protocol libraries embed: the player's protocol over their connection"

dependencies {
	api(projects.anvilPlayer.playerApi)
	api(projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaApi)
}
