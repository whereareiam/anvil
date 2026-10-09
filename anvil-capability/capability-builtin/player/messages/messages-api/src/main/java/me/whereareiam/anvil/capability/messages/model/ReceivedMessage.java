package me.whereareiam.anvil.capability.messages.model;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * A message found by a wait, with the history position directly after it.
 */
@Value
public class ReceivedMessage {
	/**
	 * The message flattened to plain text.
	 */
	@NotNull String text;
	/**
	 * Checkpoint directly after this message; pass it to the next wait to look only at later messages.
	 */
	int checkpoint;
}
