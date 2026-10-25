package me.whereareiam.anvil.workbench.protocol.model.file;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * The content of a text file.
 */
@Value
public class FileContent {
	/**
	 * The file's text.
	 */
	@NotNull String content;
}
