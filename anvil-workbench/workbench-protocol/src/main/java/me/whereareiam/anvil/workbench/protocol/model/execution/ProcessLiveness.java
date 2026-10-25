package me.whereareiam.anvil.workbench.protocol.model.execution;

import lombok.Value;

/**
 * Whether a process runs.
 */
@Value
public class ProcessLiveness {
	/**
	 * True while it runs.
	 */
	boolean alive;
}
