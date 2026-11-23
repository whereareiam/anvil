package me.whereareiam.anvil.capability.packets.exception;

import lombok.Getter;
import me.whereareiam.anvil.capability.packets.model.PacketDifference;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Reports that two recorded packet streams differ beyond what the comparison's mask leaves out. It is an assertion
 * failure, so a test framework reports the test as failed; its message lists the first differences, and
 * {@link #differences()} holds them all.
 */
@Getter
public class RecordingMismatchException extends AssertionError {
	private static final int LISTED = 10;

	/**
	 * Every difference, in the order of the streams.
	 */
	private final @NotNull List<PacketDifference> differences;

	/**
	 * Creates the failure.
	 *
	 * @param differences every difference, at least one
	 */
	public RecordingMismatchException(@NotNull List<PacketDifference> differences) {
		super(message(differences));
		this.differences = List.copyOf(differences);
	}

	private static String message(List<PacketDifference> differences) {
		StringBuilder message = new StringBuilder("The packet streams differ in ").append(differences.size())
				.append(differences.size() == 1 ? " place:" : " places:");
		for (PacketDifference difference : differences.subList(0, Math.min(LISTED, differences.size())))
			message.append("\n  expected #").append(difference.expected()).append(", actual #")
					.append(difference.actual()).append(": ").append(difference.description());
		if (differences.size() > LISTED) message.append("\n  and ").append(differences.size() - LISTED).append(" more");

		return message.toString();
	}
}
