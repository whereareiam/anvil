package me.whereareiam.anvil.workbench.protocol.model.store;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Asks where the workbench keeps content it may hold already.
 */
@Value
public class FindStored implements StoreRequest<StoredFile> {
	/**
	 * SHA-256 of the content.
	 */
	@NotNull String sha256;

	/**
	 * File or directory name of the content.
	 */
	@NotNull String name;

	@Override
	public @NotNull Class<StoredFile> responseType() {
		return StoredFile.class;
	}
}
