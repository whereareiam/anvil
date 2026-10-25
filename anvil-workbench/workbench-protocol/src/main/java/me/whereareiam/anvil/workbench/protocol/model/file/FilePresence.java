package me.whereareiam.anvil.workbench.protocol.model.file;

import lombok.Value;

/**
 * Whether something exists at a path.
 */
@Value
public class FilePresence {
	/**
	 * True when it exists.
	 */
	boolean exists;
}
