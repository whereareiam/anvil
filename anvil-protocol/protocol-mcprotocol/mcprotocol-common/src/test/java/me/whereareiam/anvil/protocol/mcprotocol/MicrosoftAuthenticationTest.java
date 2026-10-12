package me.whereareiam.anvil.protocol.mcprotocol;

import com.google.gson.JsonObject;
import me.whereareiam.anvil.api.model.MinecraftEdition;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.player.AccountAccess;
import me.whereareiam.anvil.protocol.api.worker.model.AccountCredentials;
import me.whereareiam.anvil.protocol.api.worker.model.AuthenticationSession;
import me.whereareiam.anvil.protocol.api.worker.model.DerivedSession;
import me.whereareiam.anvil.protocol.api.worker.model.MicrosoftToken;
import me.whereareiam.anvil.protocol.worker.account.HostedAccounts;
import me.whereareiam.anvil.protocol.worker.catalog.ReleaseCatalog;
import net.lenni0451.commons.httpclient.HttpClient;
import net.lenni0451.commons.httpclient.HttpResponse;
import net.lenni0451.commons.httpclient.executor.RequestExecutor;
import net.lenni0451.commons.httpclient.requests.HttpRequest;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.java.JavaAuthManager;
import net.raphimc.minecraftauth.java.model.MinecraftToken;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MicrosoftAuthenticationTest {
	private static final UUID PROFILE = UUID.fromString("5f8eb73b-25be-4c5a-a50f-d27d65e30ca0");

	@TempDir
	Path temporary;

	/**
	 * The fixture is an account file as Anvil 1.0.0 stored it, with a game token and profile that never expire, so
	 * the derivation needs no service.
	 */
	@Test
	void derivesTheSessionOfAnAccountStoredBeforeAccountsWereMicrosoftAccountsWithoutANewSignIn() throws Exception {
		try (InputStream legacy = getClass().getResourceAsStream("legacy-account.json")) {
			Files.write(temporary.resolve("legacy.json"), legacy.readAllBytes());
		}
		ReleaseCatalog releases = ReleaseCatalog.load(McProtocolWorkerLibrary.LIBRARY, null);
		HostedAccounts accounts = new HostedAccounts(List.of(new HostedAccounts.Library(
				McProtocolWorkerLibrary.LIBRARY,
				new MicrosoftAuthentication(),
				releases::releases
		)), temporary);
		MinecraftVersion version = MinecraftVersion.parse("1.21.11");
		MinecraftEdition edition = releases.releases().stream()
				.filter(release -> release.getMinecraftVersions().contains(version))
				.findFirst()
				.orElseThrow()
				.getEdition();

		AuthenticationSession session = accounts.resolve(McProtocolWorkerLibrary.LIBRARY.getId(), edition, "legacy", version);

		assertEquals("Fixture", session.getUsername());
		assertEquals(PROFILE, session.getUuid());
		assertEquals("fixture-minecraft-access-token", session.getAccessToken());
		assertEquals(AccountAccess.available(edition, "Fixture", PROFILE), accounts.accounts().iterator().next().access(edition));
		String stored = Files.readString(temporary.resolve("legacy.json"));
		assertTrue(stored.startsWith("{\"schemaVersion\":3,"), stored);
		assertTrue(stored.contains("\"refreshToken\":\"fixture-msa-refresh-token\""), stored);
	}

	@Test
	void restoresTheCachedSessionWithTheAccountsCurrentMicrosoftToken() {
		MicrosoftToken older = token("older-access");
		JavaAuthManager cached = MicrosoftAuthentication.manager(AccountCredentials.builder()
				.accountId("alice")
				.token(older)
				.build(), MinecraftAuth.createHttpClient());
		MicrosoftToken current = token("current-access");

		JavaAuthManager restored = MicrosoftAuthentication.manager(AccountCredentials.builder()
				.accountId("alice")
				.token(current)
				.cachedSession(JavaAuthManager.toJson(cached).toString())
				.build(), MinecraftAuth.createHttpClient());

		assertEquals("current-access", restored.getMsaToken().getCached().getAccessToken());
		assertEquals(cached.getDeviceId(), restored.getDeviceId());
	}

	@Test
	void signsTheTokenInAfreshWhenTheCachedSessionIsUnusable() {
		JavaAuthManager manager = MicrosoftAuthentication.manager(AccountCredentials.builder()
				.accountId("alice")
				.token(token("current-access"))
				.cachedSession("{\"refresh\":\"private-token\"}")
				.build(), MinecraftAuth.createHttpClient());

		assertEquals("current-access", manager.getMsaToken().getCached().getAccessToken());
		assertEquals("00000000402b5328", manager.getMsaApplicationConfig().getClientId());
	}

	@Test
	void refusesAnAccountWithoutAJavaEditionProfileNamingIt() {
		JavaAuthManager signedIn = MicrosoftAuthentication.manager(AccountCredentials.builder()
				.accountId("bedrock-only")
				.token(token("current-access"))
				.build(), MinecraftAuth.createHttpClient());
		JsonObject cached = JavaAuthManager.toJson(signedIn);
		cached.add("minecraftToken", MinecraftToken.toJson(new MinecraftToken(Long.MAX_VALUE, "Bearer", "game-token")));
		HttpClient services = new HttpClient(client -> new RequestExecutor(client) {
			@Override
			public @NotNull HttpResponse execute(@NotNull HttpRequest request) {
				assertEquals("https://api.minecraftservices.com/minecraft/profile", request.getURL().toString());
				return new HttpResponse(request.getURL(), 404, ("{\"path\":\"/minecraft/profile\",\"error\":\"NOT_FOUND\","
						+ "\"errorMessage\":\"Not Found\"}").getBytes(StandardCharsets.UTF_8),
						Map.of("Content-Type", List.of("application/json")));
			}
		});

		DerivedSession derived = MicrosoftAuthentication.derive(AccountCredentials.builder()
				.accountId("bedrock-only")
				.token(token("current-access"))
				.cachedSession(cached.toString())
				.build(), MinecraftVersion.parse("1.21.11"), services);

		assertFalse(derived.isAvailable());
		assertEquals("Account 'bedrock-only' has no Minecraft: Java Edition profile, so MCProtocolLib players cannot sign in "
				+ "with it", derived.getRefusal());
		assertEquals("current-access", derived.getToken().getAccessToken());
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
