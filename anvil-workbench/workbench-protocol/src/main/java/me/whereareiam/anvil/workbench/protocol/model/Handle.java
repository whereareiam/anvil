package me.whereareiam.anvil.workbench.protocol.model;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Names an object the workbench holds for the engine, such as an execution session or a running process.
 */
@Value
public class Handle {
	/**
	 * Handle the engine names the object by in later requests.
	 */
	@NotNull String handle;
}
