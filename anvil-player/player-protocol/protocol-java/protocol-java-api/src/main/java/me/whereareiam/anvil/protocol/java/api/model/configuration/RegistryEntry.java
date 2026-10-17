package me.whereareiam.anvil.protocol.java.api.model.configuration;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtCompound;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One entry of a synchronized registry the server sends during configuration.
 */
@Value
public class RegistryEntry {
	/**
	 * The entry's key with its namespace, such as {@code minecraft:server_links}.
	 */
	@NotNull String key;
	/**
	 * The entry's data as the registry's codec writes it, or {@code null} when the server left it out because the
	 * client knows it from a built-in pack.
	 */
	@Nullable NbtCompound data;
}
