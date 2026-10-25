package me.whereareiam.anvil.workbench.protocol.model.file;

import lombok.Value;

/**
 * Whether a deletion removed anything.
 */
@Value
public class FileDeletion {
	/**
	 * True when something was deleted.
	 */
	boolean deleted;
}
