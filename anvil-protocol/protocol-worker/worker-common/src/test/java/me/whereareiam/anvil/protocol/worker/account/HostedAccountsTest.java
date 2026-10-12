package me.whereareiam.anvil.protocol.worker.account;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.player.AccountAccess;
import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import me.whereareiam.anvil.api.type.AccountAvailability;
import me.whereareiam.anvil.protocol.api.worker.WorkerAuthentication;
import me.whereareiam.anvil.protocol.api.worker.model.AccountCredentials;
import me.whereareiam.anvil.protocol.api.worker.model.AuthenticationSession;
import me.whereareiam.anvil.protocol.api.worker.model.DerivedSession;
import me.whereareiam.anvil.protocol.api.worker.model.MicrosoftToken;
import me.whereareiam.anvil.protocol.api.worker.model.WorkerLibrary;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostedAccountsTest {
	private static final MinecraftVersion NEWEST = MinecraftVersion.parse("1.26.60");
	private static final UUID JAVA_PROFILE = UUID.fromString("00000000-0000-0000-0000-00000000000a");

	@TempDir
	Path temporary;

	@Test
	void signsInOnceAndRecordsWhatEveryLibraryCanDoWithTheAccount() {
		RecordingDerivation java = new RecordingDerivation("Alice", null);
		RecordingDerivation bedrock = new RecordingDerivation(null, "Account 'alice' owns no Bedrock profile");
		List<String> signIns = new ArrayList<>();
		HostedAccounts accounts = accounts(signIns, library("javalib", java), library("bedrocklib", bedrock));
		List<String> output = new ArrayList<>();

		accounts.login("alice", output::add);

		assertEquals(List.of("alice"), signIns);
		assertEquals(List.of("derive 1.26.60 refresh-0 null"), java.calls);
		assertEquals(List.of("derive 1.26.60 refresh-1 null"), bedrock.calls);
		assertEquals(List.of(
				"Open https://example.invalid/device",
				"Signed in account 'alice'.",
				"javalib: available as Alice (" + JAVA_PROFILE + ")",
				"bedrocklib: unavailable, Account 'alice' owns no Bedrock profile"
		), output);
		AuthenticationAccount account = accounts.accounts().iterator().next();
		assertEquals(AccountAvailability.AVAILABLE, account.access("javalib").getAvailability());
		assertEquals(AccountAvailability.UNAVAILABLE, account.access("bedrocklib").getAvailability());
	}

	@Test
	void derivesAPlayersSessionWithTheCachedSessionAndKeepsTheRefreshedToken() {
		RecordingDerivation java = new RecordingDerivation("Alice", null);
		RecordingDerivation bedrock = new RecordingDerivation("AliceXbox", null);
		HostedAccounts accounts = accounts(new ArrayList<>(), library("javalib", java), library("bedrocklib", bedrock));
		accounts.login("alice", ignored -> { });

		AuthenticationSession session = accounts.resolve("javalib", "alice", MinecraftVersion.parse("1.21.11"));
		accounts.resolve("bedrocklib", "alice", MinecraftVersion.parse("1.21.50"));

		assertEquals("Alice", session.getUsername());
		assertEquals("derive 1.21.11 refresh-2 cache-1", java.calls.getLast());
		assertEquals("derive 1.21.50 refresh-3 cache-1", bedrock.calls.getLast());
	}

	@Test
	void refusesAPlayerWhoseLibraryRefusesTheAccountWithTheLibrarysReasonAndRecordsIt() {
		RecordingDerivation java = new RecordingDerivation("Alice", null);
		HostedAccounts accounts = accounts(new ArrayList<>(), library("javalib", java));
		accounts.login("alice", ignored -> { });
		java.refusal = "Account 'alice' has no profile any more";

		var failure = assertThrows(IllegalStateException.class, () -> accounts.resolve("javalib", "alice", NEWEST));

		assertEquals("Account 'alice' has no profile any more", failure.getMessage());
		AccountAccess access = accounts.accounts().iterator().next().access("javalib");
		assertEquals(AccountAccess.unavailable("javalib", "Account 'alice' has no profile any more"), access);
	}

	@Test
	void recordsUnknownAvailabilityWhenALibraryCannotTellAtSignIn() {
		RecordingDerivation java = new RecordingDerivation("Alice", null);
		java.failure = "Could not reach the services for account 'alice'";
		HostedAccounts accounts = accounts(new ArrayList<>(), library("javalib", java));
		List<String> output = new ArrayList<>();

		accounts.login("alice", output::add);

		assertEquals("javalib: unknown, Could not reach the services for account 'alice'", output.getLast());
		assertEquals(AccountAvailability.UNKNOWN, accounts.accounts().iterator().next().access("javalib").getAvailability());
	}

	@Test
	void refusesALibraryThatDerivesNoSessions() {
		HostedAccounts accounts = accounts(new ArrayList<>(), library("javalib", new RecordingDerivation("Alice", null)));

		var failure = assertThrows(IllegalArgumentException.class, () -> accounts.resolve("other", "alice", NEWEST));

		assertTrue(failure.getMessage().contains("'other'"), failure.getMessage());
	}

	@Test
	void reportsRemovedAndUnknownAccountsOnLogout() {
		HostedAccounts accounts = accounts(new ArrayList<>(), library("javalib", new RecordingDerivation("Alice", null)));
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

	private static HostedAccounts.Library library(String id, WorkerAuthentication derivation) {
		WorkerLibrary library = WorkerLibrary.builder()
				.id(id)
				.name(id)
				.clientPort(Object.class)
				.releases("releases.toml")
				.pinTask("pin")
				.offlineUniqueIdPrefix("Offline:")
				.build();
		return new HostedAccounts.Library(library, derivation, () -> NEWEST);
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
					.uuid(JAVA_PROFILE)
					.accessToken("game-" + generation)
					.build(), "cache-1", refreshed);
		}
	}
}
