package me.whereareiam.anvil.environment.yggdrasil;

import me.whereareiam.anvil.api.model.player.SessionIdentity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YggdrasilMockTest {
	private final YggdrasilMock yggdrasil = YggdrasilMock.start();
	private final HttpClient client = HttpClient.newHttpClient();

	@AfterEach
	void stop() {
		client.close();
		yggdrasil.close();
	}

	@Test
	void looksUpOnlyRegisteredUsernamesWhateverTheirCase() throws Exception {
		UUID uniqueId = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
		yggdrasil.register("Alice", uniqueId);

		HttpResponse<String> found = get(yggdrasil.profileLookup() + "alice");
		assertEquals(200, found.statusCode());
		assertEquals("{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Alice\",\"properties\":[]}", found.body());
		assertEquals(404, get(yggdrasil.profileLookup() + "Bob").statusCode());
	}

	@Test
	void verifiesALoginOnceAfterTheProfileReportedItWithItsOwnToken() throws Exception {
		SessionIdentity alice = yggdrasil.register("Alice");
		String verification = alice.sessionServer() + "/hasJoined?username=Alice&serverId=hash&ip=127.0.0.1";

		assertEquals(204, get(verification).statusCode());
		assertEquals(403, join(alice, "another-token", "hash").statusCode());
		assertEquals(204, get(verification).statusCode());

		assertEquals(204, join(alice, alice.accessToken(), "hash").statusCode());
		assertEquals(204, get(alice.sessionServer() + "/hasJoined?username=Alice&serverId=other").statusCode());
		HttpResponse<String> verified = get(verification);
		assertEquals(200, verified.statusCode());
		assertTrue(verified.body().contains("\"name\":\"Alice\""), verified.body());
		assertEquals(204, get(verification).statusCode());
	}

	@Test
	void renamesAProfileKeepingItsIdentity() throws Exception {
		UUID uniqueId = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
		SessionIdentity alice = yggdrasil.register("Alice", uniqueId);

		SessionIdentity alicia = yggdrasil.rename(uniqueId, "Alicia");

		assertEquals("Alicia", alicia.username());
		assertEquals(uniqueId, alicia.uniqueId());
		assertEquals(alice.accessToken(), alicia.accessToken());
		assertEquals(alice.sessionServer(), alicia.sessionServer());
		assertEquals(404, get(yggdrasil.profileLookup() + "Alice").statusCode());
		HttpResponse<String> found = get(yggdrasil.profileLookup() + "aLICIA");
		assertEquals(200, found.statusCode());
		assertEquals("{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Alicia\",\"properties\":[]}", found.body());
	}

	@Test
	void verifiesALoginOnlyUnderTheProfilesCurrentName() throws Exception {
		SessionIdentity alice = yggdrasil.register("Alice");
		assertEquals(204, join(alice, alice.accessToken(), "hash").statusCode());

		SessionIdentity alicia = yggdrasil.rename(alice.uniqueId(), "Alicia");

		assertEquals(204, get(alice.sessionServer() + "/hasJoined?username=Alice&serverId=hash").statusCode());
		HttpResponse<String> verified = get(alicia.sessionServer() + "/hasJoined?username=ALICIA&serverId=hash");
		assertEquals(200, verified.statusCode());
		assertTrue(verified.body().contains("\"name\":\"Alicia\""), verified.body());
	}

	@Test
	void renamesOnlyInLetterCaseOrToAFreeName() {
		SessionIdentity alice = yggdrasil.register("Alice");
		yggdrasil.register("Bob");

		assertEquals("ALICE", yggdrasil.rename(alice.uniqueId(), "ALICE").username());
		assertThrows(IllegalArgumentException.class, () -> yggdrasil.rename(alice.uniqueId(), "bob"));
		assertThrows(IllegalArgumentException.class, () -> yggdrasil.rename(UUID.randomUUID(), "Carol"));
	}

	@Test
	void failsEveryEndpointDuringAnOutageUntilReset() throws Exception {
		yggdrasil.register("Alice");
		yggdrasil.available(false);
		assertEquals(503, get(yggdrasil.profileLookup() + "Alice").statusCode());

		yggdrasil.reset();
		assertEquals(404, get(yggdrasil.profileLookup() + "Alice").statusCode());
	}

	private HttpResponse<String> get(String address) throws Exception {
		return client.send(HttpRequest.newBuilder(URI.create(address)).build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> join(SessionIdentity identity, String token, String serverId) throws Exception {
		String body = "{\"accessToken\":\"" + token + "\",\"selectedProfile\":\""
				+ identity.uniqueId().toString().replace("-", "") + "\",\"serverId\":\"" + serverId + "\"}";
		return client.send(HttpRequest.newBuilder(URI.create(identity.sessionServer() + "/join"))
				.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
	}
}
