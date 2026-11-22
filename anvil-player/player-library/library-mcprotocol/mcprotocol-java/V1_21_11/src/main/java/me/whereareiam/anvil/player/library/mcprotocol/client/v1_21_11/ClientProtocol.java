package me.whereareiam.anvil.player.library.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.player.library.mcprotocol.client.model.ClientCredentials;
import me.whereareiam.anvil.player.library.mcprotocol.client.model.ClientLogin;
import me.whereareiam.anvil.player.library.mcprotocol.client.model.ClientProfile;
import me.whereareiam.anvil.player.library.mcprotocol.client.model.ClientSettings;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6.KnownPacksAnswer;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The protocol of a player's sessions of the MCProtocolLib releases from 1.21.11 on, built from the player's login. MCProtocolLib hands each
 * session that connects to its protocol, which adds the library's own listener: that listener answers the login and
 * switches the session from one phase to the next. The {@link ClientConfiguration} starts the configuration once the
 * library entered it, so it must see every packet after the library's listener, and the protocol adds it right after,
 * with the {@link KnownPacksAnswer} that names the vanilla data pack of the player's Minecraft version.
 */
final class ClientProtocol extends MinecraftProtocol {
	private final String minecraftVersion;
	private final ClientSettings settings;

	/**
	 * Creates the protocol of a player's sessions.
	 *
	 * @param login the player's login, whose profile and credentials the library logs in with
	 */
	ClientProtocol(@NotNull ClientLogin login) {
		super(profile(login.getProfile()), accessToken(login.getCredentials()));
		this.minecraftVersion = login.getMinecraftVersion();
		this.settings = login.getSettings();
	}

	@Override
	public void newClientSession(Session session) {
		super.newClientSession(session);
		session.addListener(new ClientConfiguration(settings));
		KnownPacksAnswer.install(session, minecraftVersion);
	}

	private static GameProfile profile(ClientProfile profile) {
		return new GameProfile(profile.getUniqueId(), profile.getName());
	}

	private static @Nullable String accessToken(@Nullable ClientCredentials credentials) {
		return credentials == null ? null : credentials.getAccessToken();
	}
}
