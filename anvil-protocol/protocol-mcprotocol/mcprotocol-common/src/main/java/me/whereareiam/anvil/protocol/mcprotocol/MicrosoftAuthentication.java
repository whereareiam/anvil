package me.whereareiam.anvil.protocol.mcprotocol;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.api.worker.WorkerAuthentication;
import me.whereareiam.anvil.protocol.api.worker.model.AccountCredentials;
import me.whereareiam.anvil.protocol.api.worker.model.AuthenticationSession;
import me.whereareiam.anvil.protocol.api.worker.model.DerivedSession;
import me.whereareiam.anvil.protocol.api.worker.model.MicrosoftToken;
import net.lenni0451.commons.httpclient.HttpClient;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.java.JavaAuthManager;
import net.raphimc.minecraftauth.java.exception.MinecraftProfileNotFoundException;
import net.raphimc.minecraftauth.java.model.MinecraftProfile;
import net.raphimc.minecraftauth.java.model.MinecraftToken;
import net.raphimc.minecraftauth.msa.model.MsaApplicationConfig;
import net.raphimc.minecraftauth.msa.model.MsaToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.time.Instant;

/**
 * Derives the Java Edition session of an account's Microsoft token: Xbox Live and Minecraft services sign the token
 * in, and the account's Java Edition profile names the player. The cached session is the serialized
 * {@link JavaAuthManager}, whose game token is reused until it expires. An account without a Java Edition profile is
 * refused.
 */
public final class MicrosoftAuthentication implements WorkerAuthentication {
	/**
	 * Derives the session of a player; Java Edition sessions do not depend on the game version. Never log the
	 * returned token.
	 */
	@Override
	public @NotNull DerivedSession derive(@NotNull AccountCredentials account, @NotNull MinecraftVersion version) {
		return derive(account, version, MinecraftAuth.createHttpClient());
	}

	/**
	 * Derives the session through the given HTTP client, which tests replace to answer for the services.
	 */
	static DerivedSession derive(AccountCredentials account, MinecraftVersion version, HttpClient http) {
		JavaAuthManager manager = manager(account, http);
		try {
			MinecraftProfile profile;
			try {
				profile = manager.getMinecraftProfile().getUpToDate();
			} catch (MinecraftProfileNotFoundException missing) {
				return DerivedSession.unavailable("Account '" + account.getAccountId() + "' has no Minecraft: Java Edition "
						+ "profile, so MCProtocolLib players cannot sign in with it", token(account.getToken(), manager));
			}

			MinecraftToken token = manager.getMinecraftToken().getUpToDate();
			return DerivedSession.available(
					AuthenticationSession.builder()
							.username(profile.getName())
							.uuid(profile.getId())
							.accessToken(token.getToken())
							.build(),
					JavaAuthManager.toJson(manager).toString(),
					token(account.getToken(), manager)
			);
		} catch (IOException exception) {
			throw new IllegalStateException("Could not derive the Java Edition session of account '" + account.getAccountId() + "'",
					exception);
		}
	}

	/**
	 * Restores the cached session with the account's current Microsoft token, which another library may have
	 * refreshed since, or signs the token in afresh when there is no usable cache.
	 */
	static JavaAuthManager manager(AccountCredentials account, HttpClient http) {
		JsonObject cached = cached(account.getCachedSession());
		if (cached != null) {
			cached.add("msaApplicationConfig", MsaApplicationConfig.toJson(application(account.getToken())));
			cached.add("msaToken", MsaToken.toJson(msaToken(account.getToken())));
			try {
				return JavaAuthManager.fromJson(http, cached);
			} catch (RuntimeException invalid) {
				// An unusable cache is signed in afresh; the parser's message may quote tokens, so it is dropped.
			}
		}

		return JavaAuthManager.create(http)
				.msaApplicationConfig(application(account.getToken()))
				.login(msaToken(account.getToken()));
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

	private static MicrosoftToken token(MicrosoftToken previous, JavaAuthManager manager) {
		MsaToken current = manager.getMsaToken().getCached();
		return previous.toBuilder()
				.accessToken(current.getAccessToken())
				.refreshToken(current.getRefreshToken())
				.expiresAt(Instant.ofEpochMilli(current.getExpireTimeMs()))
				.build();
	}
}
