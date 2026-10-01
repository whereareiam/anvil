package me.whereareiam.anvil.api.model.process.console;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A bounded console read. The next checkpoint is the last returned line, or the input
 * checkpoint for an empty result. Truncated indicates earlier unread output was evicted.
 * Closed indicates no further lines will be captured for this process generation.
 */
@Value
public class ConsoleOutput {
	long nextCheckpoint;
	boolean truncated;
	boolean closed;
	@NotNull List<ConsoleLine> lines;
}
