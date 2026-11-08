package me.whereareiam.anvil.protocol.api.model.text.content;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * A score on the scoreboard, which the server normally resolves before it sends the text.
 */
@Value
public class ScoreContent implements TextContent {
	/**
	 * The score holder, a player name or a selector.
	 */
	@NotNull String name;
	/**
	 * The objective.
	 */
	@NotNull String objective;
}
