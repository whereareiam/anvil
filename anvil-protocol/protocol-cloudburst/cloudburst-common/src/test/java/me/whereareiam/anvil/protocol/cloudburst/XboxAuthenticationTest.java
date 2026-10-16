package me.whereareiam.anvil.protocol.cloudburst;

import com.google.gson.JsonObject;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.api.worker.model.AccountCredentials;
import me.whereareiam.anvil.protocol.api.worker.model.DerivedSession;
import me.whereareiam.anvil.protocol.api.worker.model.MicrosoftToken;
import net.lenni0451.commons.httpclient.HttpClient;
import net.lenni0451.commons.httpclient.HttpResponse;
import net.lenni0451.commons.httpclient.executor.RequestExecutor;
import net.lenni0451.commons.httpclient.requests.HttpRequest;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import net.raphimc.minecraftauth.xbl.model.XblXstsToken;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class XboxAuthenticationTest {
	private static final MinecraftVersion VERSION = MinecraftVersion.parse("1.21.50");

	@Test
	void reportsTheGameVersionWithAllThreePartsAsBedrockSpellsIt() {
		assertEquals("1.21.0", XboxAuthentication.gameVersion(MinecraftVersion.parse("1.21.0")));
		assertEquals("1.20.0", XboxAuthentication.gameVersion(MinecraftVersion.parse("1.20")));
		assertEquals("1.26.50", XboxAuthentication.gameVersion(MinecraftVersion.parse("1.26.50")));
	}

	@Test
	void restoresTheCachedSessionWithTheAccountsCurrentMicrosoftToken() {
		BedrockAuthManager cached = XboxAuthentication.manager(credentials(token("older-access"), null), "1.21.50",
				MinecraftAuth.createHttpClient());

		BedrockAuthManager restored = XboxAuthentication.manager(credentials(token("current-access"),
				BedrockAuthManager.toJson(cached).toString()), "1.21.50", MinecraftAuth.createHttpClient());

		assertEquals("current-access", restored.getMsaToken().getCached().getAccessToken());
		assertEquals(cached.getDeviceId(), restored.getDeviceId());
		assertEquals(cached.getSessionKeyPair().getPublic(), restored.getSessionKeyPair().getPublic());
	}

	@Test
	void signsTheTokenInAfreshAsAWindowsDeviceWhenTheCachedSessionIsUnusable() {
		BedrockAuthManager manager = XboxAuthentication.manager(credentials(token("current-access"), "{\"refresh\":\"private\"}"),
				"1.21.50", MinecraftAuth.createHttpClient());

		assertEquals("current-access", manager.getMsaToken().getCached().getAccessToken());
		assertEquals("Win32", manager.getDeviceType());
	}

	@Test
	void refusesAnAccountTheMultiplayerServiceDoesNotLetSignInNamingIt() {
		DerivedSession derived = XboxAuthentication.derive(credentials(token("current-access"), signedInToXbox()), VERSION,
				services(401, "{\"path\":\"/authentication\",\"error\":\"UNAUTHORIZED\"}"));

		assertFalse(derived.isAvailable());
		assertEquals("Account 'alice' was refused by Minecraft's multiplayer service (HTTP 401), so CloudburstMC Protocol "
				+ "players cannot sign in to Bedrock Edition servers with it; the service refuses accounts that do not own "
				+ "Minecraft: Bedrock Edition or have never started it", derived.getRefusal());
		assertEquals("current-access", derived.getToken().getAccessToken());
	}

	@Test
	void leavesTheAccountUndeterminedWhenTheMultiplayerServiceFailsOtherwise() {
		var failure = assertThrows(IllegalStateException.class, () -> XboxAuthentication.derive(
				credentials(token("current-access"), signedInToXbox()), VERSION, services(503, "{\"error\":\"UNAVAILABLE\"}")));

		assertEquals("Could not derive the Bedrock Edition session of account 'alice'", failure.getMessage());
	}

	/**
	 * A cached session whose Bedrock Xbox token is still valid, so a derivation asks only the multiplayer service.
	 */
	private static String signedInToXbox() {
		BedrockAuthManager manager = XboxAuthentication.manager(credentials(token("current-access"), null), "1.21.50",
				MinecraftAuth.createHttpClient());
		JsonObject cached = BedrockAuthManager.toJson(manager);
		cached.add("bedrockXstsToken", XblXstsToken.toJson(new XblXstsToken(Long.MAX_VALUE, "xsts", "hash")));

		return cached.toString();
	}

	private static HttpClient services(int status, String body) {
		return new HttpClient(client -> new RequestExecutor(client) {
			@Override
			public @NotNull HttpResponse execute(@NotNull HttpRequest request) {
				assertEquals("https://multiplayer.minecraft.net/authentication", request.getURL().toString());
				return new HttpResponse(request.getURL(), status, body.getBytes(StandardCharsets.UTF_8),
						Map.of("Content-Type", List.of("application/json")));
			}
		});
	}

	private static AccountCredentials credentials(MicrosoftToken token, String cachedSession) {
		return AccountCredentials.builder()
				.accountId("alice")
				.token(token)
				.cachedSession(cachedSession)
				.build();
	}

	private static MicrosoftToken token(String accessToken) {
		return MicrosoftToken.builder()
				.clientId("00000000402b5328")
				.scope("service::user.auth.xboxlive.com::MBI_SSL")
				.accessToken(accessToken)
				.refreshToken("refresh")
				.expiresAt(Instant.parse("3000-01-01T00:00:00Z"))
				.build();
	}
}
