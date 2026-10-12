package me.whereareiam.anvil.protocol.player.account;

import me.whereareiam.anvil.api.model.player.AccountAccess;
import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import me.whereareiam.anvil.api.player.account.AccountPool;
import me.whereareiam.anvil.protocol.api.library.ProtocolAuthentication;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolAccountManagerTest {
	private static final List<AuthenticationAccount> ACCOUNTS = List.of(
			new AuthenticationAccount("alice", List.of(AccountAccess.available("fixture", "Alice", UUID.randomUUID()))),
			new AuthenticationAccount("bob", List.of(AccountAccess.available("fixture", "Bob", UUID.randomUUID())))
	);

	@TempDir
	Path directory;

	@Test
	void listsTheAccountsOfTheLibrariesAccountWorkflowAndNoneWithoutOne() {
		assertEquals(List.of("alice", "bob"), manager().list().stream().map(AuthenticationAccount::getAccountId).toList());
		assertEquals(List.of(), new ProtocolAccountManager(null, directory, new AccountReservations()).list());
	}

	@Test
	void poolsRefuseUnknownAndRepeatedAccounts() {
		var unknown = assertThrows(IllegalArgumentException.class, () -> manager().pool(List.of("bob", "carol")));
		var repeated = assertThrows(IllegalArgumentException.class, () -> manager().pool(List.of("bob", "bob")));

		assertEquals("Unknown account: carol", unknown.getMessage());
		assertEquals("Duplicate account in pool: bob", repeated.getMessage());
	}

	@Test
	void leasesForALibraryOnlyAccountsItRecordedAsAvailableReadingTheCurrentRecord() {
		StoredAccounts stored = new StoredAccounts(List.of(
				new AuthenticationAccount("alice", List.of(AccountAccess.unavailable("java", "Account 'alice' owns no Java profile"))),
				new AuthenticationAccount("bob", List.of(AccountAccess.available("bedrock", "Bob", UUID.randomUUID()))),
				new AuthenticationAccount("carol", List.of(AccountAccess.available("java", "Carol", UUID.randomUUID())))
		));
		AccountPool pool = new ProtocolAccountManager(stored, directory, new AccountReservations()).pool(List.of("alice", "bob", "carol"));

		assertEquals("carol", pool.lease("java").account().getAccountId());
		assertEquals("bob", pool.lease("bedrock").account().getAccountId());
		var none = assertThrows(IllegalStateException.class, () -> pool.lease("java"));
		assertEquals("No account of the pool is available to protocol library 'java': alice (java: unavailable, Account 'alice' "
				+ "owns no Java profile), bob (java: unknown), carol (in use)", none.getMessage());

		stored.accounts = List.of(new AuthenticationAccount("alice", List.of(AccountAccess.available("java", "Alice", UUID.randomUUID()))));
		assertEquals("alice", pool.lease("java").account().getAccountId());
	}

	@Test
	void leasesEachAccountOnceAndReleasesItWhenClosed() {
		AccountPool pool = manager().pool(List.of("alice", "bob"));
		var lease = pool.lease();
		assertEquals("alice", lease.account().getAccountId());
		assertEquals("bob", pool.lease().account().getAccountId());
		assertThrows(IllegalStateException.class, pool::lease);

		lease.close();
		assertEquals("alice", pool.lease().account().getAccountId());
	}

	@Test
	void poolsOfOneEngineNeverLeaseTheSameAccountTwice() {
		AccountReservations reservations = new AccountReservations();
		AccountPool first = manager(reservations).pool(List.of("alice", "bob"));
		AccountPool second = manager(reservations).pool(List.of("alice", "bob"));

		var alice = first.lease();
		assertEquals("alice", alice.account().getAccountId());
		assertEquals("bob", second.lease().account().getAccountId());
		assertThrows(IllegalStateException.class, first::lease);

		assertTrue(reservations.reserve("charlie"));
		assertFalse(reservations.reserve("alice"));
		alice.close();
		assertTrue(reservations.reserve("alice"));
	}

	@Test
	void closingAPoolKeepsClaimedLeasesUntilTheyAreClosed() {
		AccountReservations reservations = new AccountReservations();
		AccountPool pool = manager(reservations).pool(List.of("alice", "bob"));
		var claimed = pool.lease();
		pool.lease();
		assertTrue(claimed.claim());
		assertFalse(claimed.claim());

		pool.close();
		assertTrue(reservations.reserve("bob"));
		assertFalse(reservations.reserve("alice"));

		claimed.close();
		assertFalse(claimed.claim());
		assertTrue(reservations.reserve("alice"));
	}

	@Test
	void namedPoolsLeaseInTheDeclaredOrder() throws Exception {
		pools("schemaVersion=1\npool.ci=bob, alice\n");

		AccountPool pool = manager().pool("ci");
		assertEquals("bob", pool.lease().account().getAccountId());
		assertEquals("alice", pool.lease().account().getAccountId());
	}

	@Test
	void undeclaredPoolsAndMissingFilesAreUnknown() throws Exception {
		assertThrows(IllegalArgumentException.class, () -> manager().pool("ci"));

		pools("schemaVersion=1\npool.other=alice\n");
		assertThrows(IllegalArgumentException.class, () -> manager().pool("ci"));
	}

	@Test
	void rejectsDuplicateAccountsAndUnsupportedSchemas() throws Exception {
		pools("schemaVersion=1\npool.ci=alice,alice\n");
		var duplicate = assertThrows(IllegalStateException.class, () -> manager().pool("ci"));
		assertTrue(duplicate.getMessage().contains("lists account 'alice' twice"));

		pools("schemaVersion=2\npool.ci=alice\n");
		var schema = assertThrows(IllegalStateException.class, () -> manager().pool("ci"));
		assertTrue(schema.getMessage().contains("schema version 2 is unsupported"));
	}

	private ProtocolAccountManager manager() {
		return manager(new AccountReservations());
	}

	private ProtocolAccountManager manager(AccountReservations reservations) {
		return new ProtocolAccountManager(new StoredAccounts(ACCOUNTS), directory, reservations);
	}

	private void pools(String declarations) throws Exception {
		Files.writeString(directory.resolve(AccountPoolFile.FILE_NAME), declarations);
	}

	/**
	 * Stored accounts without files, as the libraries' account workflow lists them now.
	 */
	private static final class StoredAccounts implements ProtocolAuthentication {
		private List<AuthenticationAccount> accounts;

		private StoredAccounts(List<AuthenticationAccount> accounts) {
			this.accounts = accounts;
		}

		@Override
		public @NotNull Collection<AuthenticationAccount> accounts() {
			return accounts;
		}

		@Override
		public void login(@NotNull String accountId, @NotNull Consumer<String> output) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void logout(@NotNull String accountId, @NotNull Consumer<String> output) {
			throw new UnsupportedOperationException();
		}
	}
}
