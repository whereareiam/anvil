package me.whereareiam.anvil.protocol.worker.account;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.api.worker.WorkerAuthentication;
import me.whereareiam.anvil.protocol.api.worker.model.AccountRecord;
import me.whereareiam.anvil.protocol.api.worker.model.AuthenticationSession;
import me.whereareiam.anvil.protocol.api.worker.model.ResolvedAccount;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostedAuthenticationTest {
	private static final MinecraftVersion NEWEST = MinecraftVersion.parse("1.26.60");

	@TempDir
	Path temporary;

	@Test
	void storesTheLoginWithTheNewestVersionAndRefreshesItForThePlayersVersion() {
		RecordingSignIn signIn = new RecordingSignIn();
		HostedAuthentication accounts = new HostedAuthentication(AccountStoreTest.BEDROCK, signIn, temporary, () -> NEWEST);
		List<String> output = new ArrayList<>();

		accounts.login("alice", output::add);
		AuthenticationSession session = accounts.resolve("alice", MinecraftVersion.parse("1.21.50"));

		assertEquals(List.of("login 1.26.60", "resolve 1.21.50 {\"generation\":1}"), signIn.calls);
		assertEquals("token-2", session.getAccessToken());
		assertTrue(output.getLast().startsWith("Authenticated Developer ("), output.toString());
		accounts.resolve("alice", MinecraftVersion.parse("1.26.50"));
		assertEquals("resolve 1.26.50 {\"generation\":2}", signIn.calls.getLast());
		assertEquals(List.of("alice"), accounts.accounts().stream().map(account -> account.getAccountId()).toList());
	}

	@Test
	void refusesAnotherLibrarysAccountBeforeSigningIn() {
		RecordingSignIn bedrock = new RecordingSignIn();
		new HostedAuthentication(AccountStoreTest.BEDROCK, bedrock, temporary, () -> NEWEST).login("shared", ignored -> { });
		RecordingSignIn java = new RecordingSignIn();
		HostedAuthentication accounts = new HostedAuthentication(AccountStoreTest.JAVA, java, temporary, () -> NEWEST);

		var login = assertThrows(IllegalStateException.class, () -> accounts.login("shared", ignored -> { }));
		var resolve = assertThrows(IllegalStateException.class, () -> accounts.resolve("shared", NEWEST));
		var logout = assertThrows(IllegalStateException.class, () -> accounts.logout("shared", ignored -> { }));

		assertTrue(login.getMessage().startsWith("Account 'shared' belongs to protocol library 'bedrocklib'"), login.getMessage());
		assertEquals(login.getMessage(), resolve.getMessage());
		assertEquals(login.getMessage(), logout.getMessage());
		assertTrue(java.calls.isEmpty(), java.calls.toString());
	}

	@Test
	void reportsRemovedAndUnknownAccountsOnLogout() {
		HostedAuthentication accounts = new HostedAuthentication(AccountStoreTest.JAVA, new RecordingSignIn(), temporary, () -> NEWEST);
		accounts.login("alice", ignored -> { });
		List<String> output = new ArrayList<>();

		accounts.logout("alice", output::add);
		accounts.logout("alice", output::add);

		assertEquals(List.of("Removed authentication account 'alice'.", "Authentication account 'alice' did not exist."), output);
	}

	/**
	 * Signs in without a service: each refresh counts a generation in the credentials.
	 */
	private static final class RecordingSignIn implements WorkerAuthentication {
		private final List<String> calls = new ArrayList<>();

		@Override
		public @NotNull AccountRecord login(@NotNull String accountId, @NotNull MinecraftVersion version, @NotNull Consumer<String> output) {
			calls.add("login " + version);
			return AccountStoreTest.record(accountId, UUID.randomUUID(), "{\"generation\":1}");
		}

		@Override
		public @NotNull ResolvedAccount resolve(@NotNull AccountRecord account, @NotNull MinecraftVersion version) {
			calls.add("resolve " + version + " " + account.getCredentials());
			int generation = Integer.parseInt(account.getCredentials().replaceAll("\\D", "")) + 1;
			return ResolvedAccount.builder()
					.account(account.toBuilder().credentials("{\"generation\":" + generation + "}").build())
					.session(AuthenticationSession.builder()
							.username(account.getUsername())
							.uuid(account.getUniqueId())
							.accessToken("token-" + generation)
							.build())
					.build();
		}
	}
}
