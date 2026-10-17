plugins {
	id("me.whereareiam.toolkit.architecture")
}

// Player capabilities run over the player's protocol: the capability family composes them on its channel, and
// capability-player-api exposes it to their providers.
architecture {
	dependsOnApis = setOf(projects.anvilPlayer.playerApi.path)
}
