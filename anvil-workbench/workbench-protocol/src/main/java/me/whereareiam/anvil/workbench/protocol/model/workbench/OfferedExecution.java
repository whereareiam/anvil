package me.whereareiam.anvil.workbench.protocol.model.workbench;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * One execution provider a workbench offers.
 */
@Value
public class OfferedExecution {
	/**
	 * Identifier scenarios select it by.
	 */
	@NotNull String id;

	/**
	 * Whether processes see a client's own source address.
	 */
	boolean preservesClientAddress;

	/**
	 * Whether processes of separate environments reach each other.
	 */
	boolean connectsEnvironments;
}
