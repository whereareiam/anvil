package me.whereareiam.anvil.api.model.process.console;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A bounded console read through a {@link me.whereareiam.anvil.api.process.ConsoleWatch}. Truncated indicates
 * earlier unread output was evicted. Closed indicates no further lines will be captured for this process
 * generation.
 */
@Value
public class ConsoleOutput {
	boolean truncated;
	boolean closed;
	@NotNull List<ConsoleLine> lines;
}
