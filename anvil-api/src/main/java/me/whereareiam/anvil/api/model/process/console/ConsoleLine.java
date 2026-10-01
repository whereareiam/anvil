package me.whereareiam.anvil.api.model.process.console;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * One captured output line with a process-generation-local sequence number.
 */
@Value
public class ConsoleLine {
	long sequence;
	@NotNull String text;
}
