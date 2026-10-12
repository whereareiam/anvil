package me.whereareiam.anvil.protocol.cloudburst.client.model;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.protocol.cloudburst.client.ClientIdentity;
import me.whereareiam.anvil.protocol.cloudburst.client.CloudburstClient;
import org.jetbrains.annotations.NotNull;

/**
 * Everything a {@link CloudburstClient} needs to log one player in: who logs in, how the login proves it, and
 * where to.
 *
 * <pre>{@code
 * ClientLogin login = ClientLogin.builder()
 *         .profile(ClientProfile.builder().name("Alice").uniqueId(uuid).build())
 *         .identity(identity)
 *         .connection(ClientConnection.builder().host("127.0.0.1").port(19132).protocol(2193).build())
 *         .build();
 * }</pre>
 */
@Value
@Builder
public class ClientLogin {
	/**
	 * Profile the identity claims.
	 */
	@NotNull ClientProfile profile;

	/**
	 * Key pair, certificate chain and client data of the login.
	 */
	@NotNull ClientIdentity identity;

	/**
	 * Game listener, source address and protocol of the connection.
	 */
	@NotNull ClientConnection connection;
}
