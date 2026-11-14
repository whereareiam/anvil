package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import com.github.steveice10.mc.auth.data.GameProfile;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientCredentials;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientLogin;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientProfile;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientSettings;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The protocol of a player's sessions of the MCProtocolLib 1.20.6 release, built from the player's login. MCProtocolLib hands each
 * session that connects to its protocol, which adds the library's own listener: that listener answers the login and
 * switches the session from one phase to the next. The {@link ClientConfiguration} starts the configuration once the
 * library entered it, so it must see every packet after the library's listener, and the protocol adds it right after.
 */
final class ClientProtocol extends MinecraftProtocol {
	private final ClientSettings settings;

	/**
	 * Creates the protocol of a player's sessions.
	 *
	 * @param login the player's login, whose profile and credentials the library logs in with
	 */
	ClientProtocol(@NotNull ClientLogin login) {
		super(profile(login.getProfile()), accessToken(login.getCredentials()));
		this.settings = login.getSettings();
	}

	@Override
	public void newClientSession(Session session, boolean transferring) {
		super.newClientSession(session, transferring);
		session.addListener(new ClientConfiguration(settings));
	}

	private static GameProfile profile(ClientProfile profile) {
		return new GameProfile(profile.getUniqueId(), profile.getName());
	}

	private static @Nullable String accessToken(@Nullable ClientCredentials credentials) {
		return credentials == null ? null : credentials.getAccessToken();
	}
}
