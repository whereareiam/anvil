plugins {
	id("module-api")
}

architecture {
	sharedApis = setOf(projects.anvilPlayer.playerApi.path)
}

description = "Java Edition protocol of simulated players: the packets a player receives and sends"

dependencies {
	api(projects.anvilPlayer.playerApi)
}
