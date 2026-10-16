package me.whereareiam.anvil.protocol.cloudburst.client;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.security.KeyPair;
import java.util.List;

/**
 * How a Bedrock login proves who logs in, independent of a protocol release: the client's key pair, the identity
 * certificate chain and, for an Xbox account, the multiplayer token, and the signed client data that a login
 * carries besides them.
 *
 * <p>An offline identity signs a one-link chain with its own key, which a server accepts only without Xbox
 * authentication. An Xbox identity carries the chain and token issued for its key. A segment builds the login from
 * these values and signs the client data through {@link #clientData(String, String)}; it never creates keys or
 * tokens itself.</p>
 *
 * <pre>{@code
 * LoginPacket login = new LoginPacket();
 * login.setAuthPayload(identity.token() == null
 *         ? new CertificateChainPayload(identity.chain(), type)
 *         : new TokenPayload(identity.token(), type));
 * login.setClientJwt(identity.clientData(codec.getMinecraftVersion(), "127.0.0.1:19132"));
 * }</pre>
 */
public interface ClientIdentity {
	/**
	 * Returns the client's P-384 key pair: the chain names its public key, and the client derives the encryption key
	 * from its private key and the server's handshake.
	 *
	 * @return key pair of the login
	 */
	@NotNull KeyPair keyPair();

	/**
	 * Returns the identity certificate chain, the last link naming the profile.
	 *
	 * @return signed chain links in order
	 */
	@NotNull List<String> chain();

	/**
	 * Returns the multiplayer token of an Xbox identity, which logins of newer protocols send instead of the chain.
	 *
	 * @return multiplayer token, or null for an offline identity
	 */
	@Nullable String token();

	/**
	 * Tests whether Xbox services issued the chain, so that the login claims full authentication; an offline
	 * identity claims a self-signed one.
	 *
	 * @return whether the identity belongs to an Xbox account
	 */
	boolean authenticated();

	/**
	 * Signs the client data a login carries: device, skin and input properties of a client of the given game
	 * version that connects to the given address.
	 *
	 * @param gameVersion game version the client reports, such as {@code 1.21.130}
	 * @param serverAddress address the client reports it connects to, as {@code host:port}
	 * @return compact ES384-signed client data
	 */
	@NotNull String clientData(@NotNull String gameVersion, @NotNull String serverAddress);
}
