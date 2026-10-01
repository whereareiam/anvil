package me.whereareiam.anvil.integration.intellij.view.window.main.environment.players

import me.whereareiam.anvil.integration.intellij.view.window.main.component.status.StatusPresentation

/**
 * Presence of a retained player in its environment.
 */
enum class PlayerStatus(val label: String, val tone: StatusPresentation.Tone) {
	/**
	 * The environment currently reports the player.
	 */
	PRESENT("In this environment", StatusPresentation.Tone.RUNNING),

	/**
	 * The environment is still active but no longer reports the player.
	 */
	LEFT("No longer in this environment", StatusPresentation.Tone.NEUTRAL),

	/**
	 * The session has ended; its players remain inspectable.
	 */
	COMPLETED("Session completed", StatusPresentation.Tone.NEUTRAL)
}
