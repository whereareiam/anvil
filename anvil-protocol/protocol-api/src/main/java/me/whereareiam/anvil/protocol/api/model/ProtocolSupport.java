package me.whereareiam.anvil.protocol.api.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.protocol.api.type.ProtocolCapability;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Immutable entry in a player's verified protocol support catalog.
 */
@Value
@Builder
public class ProtocolSupport {
	/**
	 * Exact Minecraft version exposed by this support entry.
	 */
	@NotNull String minecraftVersion;
	/**
	 * Native Minecraft wire-protocol number.
	 */
	int protocolNumber;
	/**
	 * Exact client-library runtime identifier used by the provider.
	 */
	@NotNull String libraryVersion;
	/**
	 * Compatible packet/API binding family, shared where library shapes are identical.
	 */
	@NotNull String bindingFamily;
	/**
	 * Minimum Java feature version required by the client runtime.
	 */
	int javaVersion;
	/**
	 * Backend protocol support flags; these do not select player capability adapters.
	 */
	@NotNull
	@Singular
	Set<ProtocolCapability> capabilities;
}
