package me.whereareiam.anvil.capability.messages.model;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable messages operation request shared by host and native bindings.
 */
@Value
public class MessageText {
	@NotNull String text;
}
