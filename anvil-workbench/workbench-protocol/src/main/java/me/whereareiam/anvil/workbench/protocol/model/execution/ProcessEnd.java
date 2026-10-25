package me.whereareiam.anvil.workbench.protocol.model.execution;

import lombok.Value;

/**
 * Whether a process ended within a wait.
 */
@Value
public class ProcessEnd {
	/**
	 * True when it ended.
	 */
	boolean ended;
}
