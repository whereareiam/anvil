package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.protocol.mcprotocol.client.SessionServerJoin;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.auth.SessionService;

import java.io.IOException;
import java.net.URI;

/**
 * Reports a login to the session server a scenario chose; MCProtocolLib's own service only addresses Mojang.
 */
@RequiredArgsConstructor
final class RedirectedSessionService extends SessionService {
	private final URI sessionServer;

	@Override
	public void joinServer(GameProfile profile, String authenticationToken, String serverId) throws IOException {
		SessionServerJoin.report(sessionServer, authenticationToken, profile.getId(), serverId);
	}
}
