package me.whereareiam.anvil.protocol.cloudburst.identity;

import com.google.gson.JsonObject;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.protocol.cloudburst.client.ClientIdentity;
import me.whereareiam.anvil.protocol.cloudburst.client.model.ClientProfile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.security.KeyPair;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The identity of one Bedrock login, offline or of an Xbox account.
 *
 * <p>An offline identity generates its own key pair and signs a one-link chain that names the profile with an empty
 * XUID, as a client does when Xbox authentication is off; servers that require Xbox authentication refuse it. An
 * Xbox identity uses the session key pair and the chain that Xbox services certified, prefixed by the link that
 * hands the chain to the session key, and the account's multiplayer token.</p>
 *
 * <pre>{@code
 * ClientIdentity offline = LoginIdentity.offline(profile);
 * ClientIdentity online = LoginIdentity.xbox(profile, multiplayerToken, XboxCredentials.parse(libraryCredentials));
 * }</pre>
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class LoginIdentity implements ClientIdentity {
	private static final Duration VALIDITY = Duration.ofDays(2);
	private static final Duration CLOCK_SKEW = Duration.ofMinutes(1);
	private static final int WINDOWS = 7;
	private static final int ANDROID = 1;

	private final @NotNull KeyPair keyPair;
	private final @NotNull List<String> chain;
	private final @Nullable String token;
	private final @NotNull ClientData device;

	/**
	 * Creates an offline identity of a profile with a new key pair.
	 *
	 * @param profile profile the chain names
	 * @return self-signed identity
	 */
	public static @NotNull LoginIdentity offline(@NotNull ClientProfile profile) {
		KeyPair keys = LoginSigner.generateKeyPair();
		JsonObject extraData = new JsonObject();
		extraData.addProperty("XUID", "");
		extraData.addProperty("identity", profile.getUniqueId().toString());
		extraData.addProperty("displayName", profile.getName());

		JsonObject claims = validity();
		claims.addProperty("iss", "self");
		claims.add("extraData", extraData);
		claims.addProperty("identityPublicKey", LoginSigner.publicKey(keys));
		claims.addProperty("randomNonce", ThreadLocalRandom.current().nextLong());

		return new LoginIdentity(keys, List.of(LoginSigner.sign(claims, keys)), null, device(UUID.randomUUID().toString(), WINDOWS, profile));
	}

	/**
	 * Creates the identity of an Xbox account from the secrets its account workflow resolved.
	 *
	 * @param profile profile of the account
	 * @param token the account's multiplayer token
	 * @param xbox the account's Xbox credentials
	 * @return Xbox identity
	 */
	public static @NotNull LoginIdentity xbox(@NotNull ClientProfile profile, @NotNull String token, @NotNull XboxCredentials xbox) {
		KeyPair keys = xbox.getSessionKeys();

		// The first link hands the key Mojang certified to the session key that signs everything else.
		JsonObject claims = validity();
		claims.addProperty("certificateAuthority", true);
		claims.addProperty("identityPublicKey", LoginSigner.header(xbox.getMojangJwt()).get("x5u").getAsString());
		List<String> chain = List.of(LoginSigner.sign(claims, keys), xbox.getMojangJwt(), xbox.getIdentityJwt());

		return new LoginIdentity(keys, chain, token, device(xbox.getDeviceId(), ANDROID, profile));
	}

	@Override
	public @NotNull KeyPair keyPair() {
		return keyPair;
	}

	@Override
	public @NotNull List<String> chain() {
		return chain;
	}

	@Override
	public @Nullable String token() {
		return token;
	}

	@Override
	public boolean authenticated() {
		return token != null;
	}

	@Override
	public @NotNull String clientData(@NotNull String gameVersion, @NotNull String serverAddress) {
		return LoginSigner.sign(device.claims(gameVersion, serverAddress), keyPair);
	}

	private static JsonObject validity() {
		Instant now = Instant.now();
		JsonObject claims = new JsonObject();
		claims.addProperty("nbf", now.minus(CLOCK_SKEW).getEpochSecond());
		claims.addProperty("iat", now.getEpochSecond());
		claims.addProperty("exp", now.plus(VALIDITY).getEpochSecond());
		return claims;
	}

	private static ClientData device(String deviceId, int deviceOs, ClientProfile profile) {
		return ClientData.builder()
				.deviceId(deviceId)
				.deviceOs(deviceOs)
				.clientRandomId(ThreadLocalRandom.current().nextLong())
				.thirdPartyName(profile.getName())
				.build();
	}
}
