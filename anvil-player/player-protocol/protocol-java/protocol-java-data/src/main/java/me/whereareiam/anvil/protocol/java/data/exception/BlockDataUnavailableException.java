package me.whereareiam.anvil.protocol.java.data.exception;

import lombok.Getter;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import org.jetbrains.annotations.NotNull;

/**
 * Reports that Anvil ships no block data for a Minecraft version: either the version is not one Anvil supports, or
 * the data source has no block data for it yet. The message names the version and the reason.
 */
@Getter
public class BlockDataUnavailableException extends IllegalStateException {
	/**
	 * The Minecraft version without block data.
	 */
	private final @NotNull MinecraftVersion version;

	/**
	 * Creates the failure.
	 *
	 * @param version the Minecraft version without block data
	 * @param reason why there is none, such as the data source that lacks it
	 */
	public BlockDataUnavailableException(@NotNull MinecraftVersion version, @NotNull String reason) {
		super("No block data for Minecraft " + version + ": " + reason);
		this.version = version;
	}
}
