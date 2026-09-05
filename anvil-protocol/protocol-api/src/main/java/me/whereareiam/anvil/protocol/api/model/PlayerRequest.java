package me.whereareiam.anvil.protocol.api.model;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.api.type.AuthenticationMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;

/**
 * Connection request supplied by the engine to a protocol backend.
 */
@Value
@Builder
public class PlayerRequest {
	/**
	 * Unique player name within its scenario.
	 */
	@NotNull String name;
	/**
	 * Exact native Minecraft version selected before backend creation.
	 */
	@NotNull String clientVersion;
	/**
	 * Game listener of the selected server or proxy.
	 */
	@NotNull InetSocketAddress address;
	/**
	 * Login mode; automated tests use offline authentication by default.
	 */
	@NotNull
	@Builder.Default
	AuthenticationMode authentication = AuthenticationMode.OFFLINE;
	/**
	 * Provider-owned profile name when online authentication is selected.
	 */
	@Nullable String authenticationProfile;
}
