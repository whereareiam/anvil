package me.whereareiam.anvil.protocol.api.model;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.player.SessionIdentity;
import me.whereareiam.anvil.api.type.AuthenticationMode;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibrary;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;

/**
 * Connection request supplied by the engine to the {@link ProtocolLibrary} selected for one player.
 */
@Value
@Builder
public class PlayerRequest {
	/**
	 * Unique player name within its scenario.
	 */
	@NotNull String name;

	/**
	 * Minecraft username an offline player logs in with; {@link #getName() name} when none was declared.
	 */
	@Nullable String username;
	/**
	 * Exact native Minecraft version selected before library creation.
	 */
	@NotNull MinecraftVersion clientVersion;
	/**
	 * Library release listing {@link #getClientVersion() clientVersion}, selected by the engine.
	 */
	@NotNull ProtocolRelease release;
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
	 * Local account identifier when online authentication is selected.
	 */
	@Nullable String accountId;
	/**
	 * Identity verified by a session server the scenario chose, replacing {@link #getAccountId() accountId}.
	 * A library that cannot redirect its session service must refuse the request.
	 */
	@Nullable SessionIdentity sessionIdentity;

	/**
	 * Returns the Minecraft username an offline player logs in with.
	 *
	 * @return declared username, or the player name when none was declared
	 */
	public @NotNull String getUsername() {
		return username == null ? name : username;
	}
}
