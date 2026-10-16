package me.whereareiam.anvil.protocol.cloudburst;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.api.worker.WorkerAuthentication;
import me.whereareiam.anvil.protocol.api.worker.model.AccountCredentials;
import me.whereareiam.anvil.protocol.api.worker.model.AuthenticationSession;
import me.whereareiam.anvil.protocol.api.worker.model.DerivedSession;
import me.whereareiam.anvil.protocol.api.worker.model.MicrosoftToken;
import me.whereareiam.anvil.protocol.cloudburst.identity.XboxCredentials;
import net.lenni0451.commons.httpclient.HttpClient;
import net.lenni0451.commons.httpclient.exceptions.HttpRequestException;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import net.raphimc.minecraftauth.bedrock.model.MinecraftCertificateChain;
import net.raphimc.minecraftauth.bedrock.model.MinecraftMultiplayerToken;
import net.raphimc.minecraftauth.msa.model.MsaApplicationConfig;
import net.raphimc.minecraftauth.msa.model.MsaToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Derives the Bedrock Edition session of an account's Microsoft token: Xbox Live signs the token in, Minecraft's
 * multiplayer service issues the certificate chain a Bedrock server verifies, and a multiplayer session names the
 * player. The cached session is the serialized {@link BedrockAuthManager}, whose key pairs and tokens are reused
 * until they expire.
 *
 * <p>The multiplayer service answers HTTP 401 for an account it does not let sign in to Bedrock Edition servers,
 * such as one that has never played Minecraft: Bedrock Edition; such an account is refused. Every other failure
 * leaves it undetermined.</p>
 *
 * <p>Xbox services start a session for a game version: a freshly signed-in account is checked with the newest
 * version of the built-in releases, and a player's session with the player's version, each spelled as Bedrock spells
 * its versions, with all three parts ({@code 1.21.0}, not {@code 1.21}). A derived session carries the account's
 * multiplayer token as its access token and the session key pair, certificate chain, XUID and device id as
 * {@link XboxCredentials}; the worker receives both only over its private standard input.</p>
 */
public final class XboxAuthentication implements WorkerAuthentication {
	private static final int BEDROCK_VERSION_PARTS = 3;
	private static final int UNAUTHORIZED = 401;
	/**
	 * Device type of the Xbox device token, the platform of the Microsoft application every account signs in through.
	 */
	private static final String DEVICE_TYPE = "Win32";

	/**
	 * Derives the session of a player of the given Bedrock version; never log the returned token or credentials.
	 */
	@Override
	public @NotNull DerivedSession derive(@NotNull AccountCredentials account, @NotNull MinecraftVersion version) {
		return derive(account, version, MinecraftAuth.createHttpClient());
	}

	/**
	 * Derives the session through the given HTTP client, which tests replace to answer for the services.
	 */
	static DerivedSession derive(AccountCredentials account, MinecraftVersion version, HttpClient http) {
		BedrockAuthManager manager = manager(account, gameVersion(version), http);
		try {
			// Xbox Live's own refusals come first, so that only the multiplayer service's answer decides the access.
			manager.getBedrockXstsToken().getUpToDate();
			MinecraftCertificateChain chain;
			try {
				chain = manager.getMinecraftCertificateChain().getUpToDate();
			} catch (HttpRequestException refused) {
				if (refused.getResponse().getStatusCode() != UNAUTHORIZED) throw refused;

				return DerivedSession.unavailable("Account '" + account.getAccountId() + "' was refused by Minecraft's "
						+ "multiplayer service (HTTP 401), so CloudburstMC Protocol players cannot sign in to Bedrock Edition "
						+ "servers with it; the service refuses accounts that do not own Minecraft: Bedrock Edition or have "
						+ "never started it", token(account.getToken(), manager));
			}

			MinecraftMultiplayerToken token = manager.getMinecraftMultiplayerToken().getUpToDate();
			XboxCredentials credentials = XboxCredentials.builder()
					.sessionKeys(manager.getSessionKeyPair())
					.mojangJwt(chain.getMojangJwt())
					.identityJwt(chain.getIdentityJwt())
					.xuid(chain.getIdentityXuid())
					.deviceId(manager.getDeviceId().toString().replace("-", ""))
					.build();
			return DerivedSession.available(
					AuthenticationSession.builder()
							.username(token.getDisplayName())
							.uuid(token.getUuid())
							.accessToken(token.getToken())
							.libraryCredentials(credentials.toText())
							.build(),
					BedrockAuthManager.toJson(manager).toString(),
					token(account.getToken(), manager)
			);
		} catch (IOException exception) {
			throw new IllegalStateException("Could not derive the Bedrock Edition session of account '" + account.getAccountId() + "'",
					exception);
		}
	}

	/**
	 * Restores the cached session with the account's current Microsoft token, which another library may have
	 * refreshed since, or signs the token in afresh when there is no usable cache.
	 */
	static BedrockAuthManager manager(AccountCredentials account, String gameVersion, HttpClient http) {
		JsonObject cached = cached(account.getCachedSession());
		if (cached != null) {
			cached.add("msaApplicationConfig", MsaApplicationConfig.toJson(application(account.getToken())));
			cached.add("msaToken", MsaToken.toJson(msaToken(account.getToken())));
			try {
				return BedrockAuthManager.fromJson(http, gameVersion, cached);
			} catch (RuntimeException invalid) {
				// An unusable cache is signed in afresh; the parser's message may quote tokens, so it is dropped.
			}
		}

		return BedrockAuthManager.create(http, gameVersion)
				.msaApplicationConfig(application(account.getToken()))
				.deviceType(DEVICE_TYPE)
				.login(msaToken(account.getToken()));
	}

	/**
	 * Spells a Bedrock version with its three parts, as Bedrock clients report it: a parsed version drops a trailing
	 * zero, which {@link MinecraftVersion#toString()} does not restore beyond two parts.
	 */
	static String gameVersion(MinecraftVersion version) {
		List<Integer> components = version.getComponents();
		StringBuilder text = new StringBuilder();
		for (int index = 0; index < Math.max(BEDROCK_VERSION_PARTS, components.size()); index++) {
			if (index > 0) text.append('.');
			text.append(index < components.size() ? components.get(index) : 0);
		}

		return text.toString();
	}

	private static @Nullable JsonObject cached(@Nullable String session) {
		if (session == null) return null;

		try {
			return JsonParser.parseString(session).getAsJsonObject();
		} catch (RuntimeException invalid) {
			return null;
		}
	}

	private static MsaApplicationConfig application(MicrosoftToken token) {
		return new MsaApplicationConfig(token.getClientId(), token.getScope());
	}

	private static MsaToken msaToken(MicrosoftToken token) {
		return new MsaToken(token.getExpiresAt().toEpochMilli(), token.getAccessToken(), token.getRefreshToken());
	}

	private static MicrosoftToken token(MicrosoftToken previous, BedrockAuthManager manager) {
		MsaToken current = manager.getMsaToken().getCached();
		return previous.toBuilder()
				.accessToken(current.getAccessToken())
				.refreshToken(current.getRefreshToken())
				.expiresAt(Instant.ofEpochMilli(current.getExpireTimeMs()))
				.build();
	}
}
