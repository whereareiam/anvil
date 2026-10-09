package me.whereareiam.anvil.integration.intellij.model;

import java.util.UUID;

import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One ordered raw output entry retained for presentation by an attached session view.
 */
@Value
public class SessionLogEntry {
	@Nullable String process;
	@Nullable UUID executionId;
	long sequence;
	@NotNull String text;
	boolean error;
}
