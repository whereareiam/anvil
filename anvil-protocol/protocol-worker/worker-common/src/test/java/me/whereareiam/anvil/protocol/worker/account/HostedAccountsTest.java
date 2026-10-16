package me.whereareiam.anvil.protocol.worker.account;

import me.whereareiam.anvil.api.model.Edition;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.player.AccountAccess;
import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import me.whereareiam.anvil.api.type.AccountAvailability;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import me.whereareiam.anvil.protocol.api.type.ProtocolFeature;
import me.whereareiam.anvil.protocol.api.worker.WorkerAuthentication;
import me.whereareiam.anvil.protocol.api.worker.model.AccountCredentials;
import me.whereareiam.anvil.protocol.api.worker.model.AuthenticationSession;
import me.whereareiam.anvil.protocol.api.worker.model.DerivedSession;
import me.whereareiam.anvil.protocol.api.worker.model.MicrosoftToken;
import me.whereareiam.anvil.protocol.api.worker.model.WorkerLibrary;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static me.whereareiam.anvil.protocol.worker.account.AccountStoreTest.BEDROCK;
import static me.whereareiam.anvil.protocol.worker.account.AccountStoreTest.JAVA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostedAccountsTest {
	private static final MinecraftVersion JAVA_NEWEST = MinecraftVersion.parse("1.21.11");
	private static final MinecraftVersion BEDROCK_NEWEST = MinecraftVersion.parse("1.26.60");
	private static final UUID PROFILE = UUID.fromString("00000000-0000-0000-0000-00000000000a");

	@TempDir
	Path temporary;

	@Test
	void signsInOnceAndChecksEachEditionWithItsFirstLibrary() {
		RecordingDerivation java = new RecordingDerivation("Alice", null);
		RecordingDerivation otherJava = new RecordingDerivation("Alice", null);
		RecordingDerivation bedrock = new RecordingDerivation(null, "Account 'alice' owns no Bedrock profile");
		List<String> signIns = new ArrayList<>();
		HostedAccounts accounts = accounts(signIns,
				library("javalib", java, JAVA, JAVA_NEWEST),
				library("otherjavalib", otherJava, JAVA, JAVA_NEWEST),
				library("bedrocklib", bedrock, BEDROCK, BEDROCK_NEWEST));
		List<String> output = new ArrayList<>();

		accounts.login("alice", output::add);

		assertEquals(List.of("alice"), signIns);
		assertEquals(List.of("derive 1.21.11 refresh-0 null"), java.calls);
		assertEquals(List.of(), otherJava.calls);
		assertEquals(List.of("derive 1.26.60 refresh-1 null"), bedrock.calls);
		assertEquals(List.of(
				"Open https://example.invalid/device",
				"Signed in account 'alice'.",
				"java: available as Alice (" + PROFILE + ")",
				"bedrock: unavailable, Account 'alice' owns no Bedrock profile"
		), output);
		AuthenticationAccount account = accounts.accounts().iterator().next();
		assertEquals(AccountAvailability.AVAILABLE, account.access(JAVA).getAvailability());
		assertEquals(AccountAvailability.UNAVAILABLE, account.access(BEDROCK).getAvailability());
	}

	@Test
	void derivesAPlayersSessionWithItsLibrarysCachedSessionAndRecordsItForTheEdition() {
		RecordingDerivation java = new RecordingDerivation("Alice", null);
		RecordingDerivation otherJava = new RecordingDerivation("AliceToo", null);
		HostedAccounts accounts = accounts(new ArrayList<>(),
				library("javalib", java, JAVA, JAVA_NEWEST),
				library("otherjavalib", otherJava, JAVA, JAVA_NEWEST));
		accounts.login("alice", ignored -> { });

		AuthenticationSession session = accounts.resolve("javalib", JAVA, "alice", MinecraftVersion.parse("1.20.4"));
		accounts.resolve("otherjavalib", JAVA, "alice", MinecraftVersion.parse("1.21.4"));

		assertEquals("Alice", session.getUsername());
		assertEquals("derive 1.20.4 refresh-1 cache-1", java.calls.getLast());
		assertEquals("derive 1.21.4 refresh-2 null", otherJava.calls.getLast());
		assertEquals("AliceToo", accounts.accounts().iterator().next().access(JAVA).getUsername());
	}

	@Test
	void refusesAPlayerWhoseEditionRefusesTheAccountWithTheLibrarysReasonAndRecordsIt() {
		RecordingDerivation java = new RecordingDerivation("Alice", null);
		HostedAccounts accounts = accounts(new ArrayList<>(), library("javalib", java, JAVA, JAVA_NEWEST));
		accounts.login("alice", ignored -> { });
		java.refusal = "Account 'alice' has no profile any more";

		var failure = assertThrows(IllegalStateException.class, () -> accounts.resolve("javalib", JAVA, "alice", JAVA_NEWEST));

		assertEquals("Account 'alice' has no profile any more", failure.getMessage());
		AccountAccess access = accounts.accounts().iterator().next().access(JAVA);
		assertEquals(AccountAccess.unavailable(JAVA, "Account 'alice' has no profile any more"), access);
	}

	@Test
	void recordsUnknownAvailabilityWhenALibraryCannotTellAtSignIn() {
		RecordingDerivation java = new RecordingDerivation("Alice", null);
		java.failure = "Could not reach the services for account 'alice'";
		HostedAccounts accounts = accounts(new ArrayList<>(), library("javalib", java, JAVA, JAVA_NEWEST));
		List<String> output = new ArrayList<>();

		accounts.login("alice", output::add);

		assertEquals("java: unknown, Could not reach the services for account 'alice'", output.getLast());
		assertEquals(AccountAvailability.UNKNOWN, accounts.accounts().iterator().next().access(JAVA).getAvailability());
	}

	@Test
	void checksAnAccountForOneEditionAndRecordsTheResult() {
		RecordingDerivation java = new RecordingDerivation("Alice", null);
		java.failure = "Could not reach the services for account 'alice'";
		HostedAccounts accounts = accounts(new ArrayList<>(), library("javalib", java, JAVA, JAVA_NEWEST));
		accounts.login("alice", ignored -> { });
		java.failure = null;

		AccountAccess checked = accounts.check("alice", JAVA);
		AccountAccess other = accounts.check("alice", Edition.of("other"));

		assertEquals(AccountAccess.available(JAVA, "Alice", PROFILE), checked);
		assertEquals(checked, accounts.accounts().iterator().next().access(JAVA));
		assertEquals(AccountAccess.unknown(Edition.of("other"), "no installed protocol library of edition 'other' plays online"), other);
	}

	@Test
	void readsAnAccountOfTheSchemaThatRecordedLibrariesAsTheEditionsOfTheInstalledLibraries() throws Exception {
		Files.writeString(temporary.resolve("alice.json"), "{\"schemaVersion\":2,\"accountId\":\"alice\","
				+ "\"microsoft\":{\"clientId\":\"client\",\"scope\":\"scope\",\"accessToken\":\"a\",\"refreshToken\":\"r\",\"expiresAt\":1000},"
				+ "\"libraries\":{\"javalib\":{\"availability\":\"AVAILABLE\",\"username\":\"Alice\",\"uuid\":\"" + PROFILE + "\"}}}");
		HostedAccounts accounts = accounts(new ArrayList<>(), library("javalib", new RecordingDerivation("Alice", null), JAVA, JAVA_NEWEST));

		assertEquals(AccountAccess.available(JAVA, "Alice", PROFILE), accounts.accounts().iterator().next().access(JAVA));
	}

	@Test
	void refusesALibraryThatDerivesNoSessions() {
		HostedAccounts accounts = accounts(new ArrayList<>(), library("javalib", new RecordingDerivation("Alice", null), JAVA, JAVA_NEWEST));

		var failure = assertThrows(IllegalArgumentException.class, () -> accounts.resolve("other", JAVA, "alice", JAVA_NEWEST));

		assertTrue(failure.getMessage().contains("'other'"), failure.getMessage());
	}

	@Test
	void reportsRemovedAndUnknownAccountsOnLogout() {
		HostedAccounts accounts = accounts(new ArrayList<>(), library("javalib", new RecordingDerivation("Alice", null), JAVA, JAVA_NEWEST));
		accounts.login("alice", ignored -> { });
		List<String> output = new ArrayList<>();

		accounts.logout("alice", output::add);
		accounts.logout("alice", output::add);

		assertEquals(List.of("Removed account 'alice'.", "Account 'alice' did not exist."), output);
	}

	private HostedAccounts accounts(List<String> signIns, HostedAccounts.Library... libraries) {
		return new HostedAccounts(List.of(libraries), temporary, (accountId, output) -> {
			signIns.add(accountId);
			output.accept("Open https://example.invalid/device");
			return AccountStoreTest.TOKEN.toBuilder().refreshToken("refresh-0").build();
		});
	}

	private static HostedAccounts.Library library(
			String id,
			WorkerAuthentication derivation,
			Edition edition,
			MinecraftVersion newest
	) {
		WorkerLibrary library = WorkerLibrary.builder()
				.id(id)
				.name(id)
				.clientPort(Object.class)
				.releases("releases.toml")
				.pinTask("pin")
				.offlineUniqueIdPrefix("Offline:")
				.build();
		MinecraftVersion older = MinecraftVersion.parse("1.20.4");
		ProtocolRelease release = ProtocolRelease.builder()
				.libraryVersion(id + "-1")
				.edition(edition)
				.minecraftVersion(older)
				.minecraftVersion(newest)
				.protocolNumber(older, 1)
				.protocolNumber(newest, 2)
				.javaVersion(21)
				.feature(ProtocolFeature.ONLINE_AUTHENTICATION)
				.build();
		ProtocolRelease offline = ProtocolRelease.builder()
				.libraryVersion(id + "-offline")
				.edition(Edition.of("offline"))
				.minecraftVersion(newest)
				.protocolNumber(newest, 3)
				.javaVersion(21)
				.build();
		return new HostedAccounts.Library(library, derivation, () -> List.of(release, offline));
	}

	/**
	 * Derives without a service: each derivation refreshes the token's generation and caches a session generation.
	 */
	private static final class RecordingDerivation implements WorkerAuthentication {
		private final List<String> calls = new ArrayList<>();
		private final String username;
		private String refusal;
		private String failure;

		private RecordingDerivation(String username, String refusal) {
			this.username = username;
			this.refusal = refusal;
		}

		@Override
		public @NotNull DerivedSession derive(@NotNull AccountCredentials account, @NotNull MinecraftVersion version) {
			MicrosoftToken token = account.getToken();
			calls.add("derive " + version + " " + token.getRefreshToken() + " " + account.getCachedSession());
			if (failure != null) throw new IllegalStateException(failure);

			int generation = Integer.parseInt(token.getRefreshToken().replaceAll("\\D", "")) + 1;
			MicrosoftToken refreshed = token.toBuilder().refreshToken("refresh-" + generation).build();
			if (refusal != null) return DerivedSession.unavailable(refusal, refreshed);

			return DerivedSession.available(AuthenticationSession.builder()
					.username(username)
					.uuid(PROFILE)
					.accessToken("game-" + generation)
					.build(), "cache-1", refreshed);
		}
	}
}
