package me.whereareiam.anvil.workbench.protocol.model.file;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The entries of a directory.
 */
@Value
public class FileNames {
	/**
	 * Entry names in name order.
	 */
	@NotNull List<String> names;
}
