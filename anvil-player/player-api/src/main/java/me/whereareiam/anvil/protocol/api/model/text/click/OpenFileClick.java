package me.whereareiam.anvil.protocol.api.model.text.click;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Opens a file; only the game itself sends it, such as for a screenshot.
 */
@Value
public class OpenFileClick implements TextClick {
	/**
	 * The file.
	 */
	@NotNull String path;
}
