package me.whereareiam.anvil.protocol.api.model;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.api.model.MinecraftEdition;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.player.PlayerLogin;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibrary;
import org.jetbrains.annotations.NotNull;

/**
 * Connection request supplied by the engine to the {@link ProtocolLibrary} selected for one player: who logs in,
 * with which client, and where to. The engine validates it first; the login suits the joined process, and the
 * source address of the connection can be used.
 *
 * <pre>{@code
 * PlayerRequest request = PlayerRequest.builder()
 *         .name("alice-again")
 *         .login(PlayerLogin.offline("Alice"))
 *         .edition(MinecraftEdition.of("example"))
 *         .clientVersion(MinecraftVersion.parse("1.21.11"))
 *         .connection(GameConnection.builder().address(new InetSocketAddress("127.0.0.1", 25565)).build())
 *         .build();
 * }</pre>
 */
@Value
@Builder
public class PlayerRequest {
	/**
	 * Unique player name within its scenario.
	 */
	@NotNull String name;

	/**
	 * Login the player uses; never a {@link PlayerLogin#isLeased() leased} one, which the engine completes with
	 * the leased account first. A library that cannot redirect its session service must refuse a login with a
	 * session identity.
	 */
	@NotNull
	@Builder.Default
	PlayerLogin login = PlayerLogin.offline();

	/**
	 * Minecraft edition of the servers the client reaches, as their platform declares it.
	 */
	@NotNull MinecraftEdition edition;

	/**
	 * Exact native Minecraft version the client speaks. Within one library a version of an edition belongs to
	 * exactly one release, so the library resolves the release from the {@link #edition} and this version through
	 * its own release data.
	 */
	@NotNull MinecraftVersion clientVersion;

	/**
	 * Game listener the client connects to, with the host it announces and the address it connects from. A library
	 * that cannot announce the virtual host or bind the source address must refuse the request.
	 */
	@NotNull GameConnection connection;

	/**
	 * Returns the Minecraft username an offline player logs in with.
	 *
	 * @return the login's username, or the player name when the login declares none
	 */
	public @NotNull String getUsername() {
		return login.getUsername() == null ? name : login.getUsername();
	}
}
