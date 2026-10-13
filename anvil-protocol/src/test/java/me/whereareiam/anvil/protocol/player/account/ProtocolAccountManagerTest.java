package me.whereareiam.anvil.protocol.player.account;

import me.whereareiam.anvil.api.model.Edition;
import me.whereareiam.anvil.api.model.player.AccountAccess;
import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import me.whereareiam.anvil.api.player.account.AccountPool;
import me.whereareiam.anvil.protocol.api.library.ProtocolAuthentication;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolAccountManagerTest {
	private static final Edition JAVA = Edition.of("java");
	private static final Edition BEDROCK = Edition.of("bedrock");
	private static final List<AuthenticationAccount> ACCOUNTS = List.of(
			new AuthenticationAccount("alice", List.of(AccountAccess.available(JAVA, "Alice", UUID.randomUUID()))),
			new AuthenticationAccount("bob", List.of(AccountAccess.available(JAVA, "Bob", UUID.randomUUID())))
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
	void leasesForAnEditionOnlyAccountsRecordedAsAvailableReadingTheCurrentRecord() {
		StoredAccounts stored = new StoredAccounts(List.of(
				new AuthenticationAccount("alice", List.of(AccountAccess.unavailable(JAVA, "Account 'alice' owns no Java profile"))),
				new AuthenticationAccount("bob", List.of(AccountAccess.available(BEDROCK, "Bob", UUID.randomUUID()),
						AccountAccess.unavailable(JAVA, "Account 'bob' owns no Java profile"))),
				new AuthenticationAccount("carol", List.of(AccountAccess.available(JAVA, "Carol", UUID.randomUUID())))
		));
		AccountPool pool = new ProtocolAccountManager(stored, directory, new AccountReservations()).pool(List.of("alice", "bob", "carol"));

		assertEquals("carol", pool.lease(JAVA).account().getAccountId());
		assertEquals("bob", pool.lease(BEDROCK).account().getAccountId());
		var none = assertThrows(IllegalStateException.class, () -> pool.lease(JAVA));
		assertEquals("No account of the pool is available for edition 'java': alice (java: unavailable, Account 'alice' "
				+ "owns no Java profile), bob (java: unavailable, Account 'bob' owns no Java profile), carol (in use)", none.getMessage());

		stored.accounts = List.of(new AuthenticationAccount("alice", List.of(AccountAccess.available(JAVA, "Alice", UUID.randomUUID()))));
		assertEquals("alice", pool.lease(JAVA).account().getAccountId());
		assertEquals(List.of(), stored.checks);
	}

	@Test
	void checksAccountsOfUnknownAvailabilityOnlyWhenNoneIsAvailableAndLendsTheFirstThatIs() {
		UUID carol = UUID.randomUUID();
		StoredAccounts stored = new StoredAccounts(List.of(
				new AuthenticationAccount("alice", List.of()),
				new AuthenticationAccount("bob", List.of()),
				new AuthenticationAccount("carol", List.of(AccountAccess.unknown(BEDROCK, "Could not reach the services")))
		));
		stored.results.put("alice", AccountAccess.unavailable(BEDROCK, "Account 'alice' owns no Bedrock profile"));
		stored.results.put("carol", AccountAccess.available(BEDROCK, "Carol", carol));
		AccountReservations reservations = new AccountReservations();
		assertTrue(reservations.reserve("bob"));
		AccountPool pool = new ProtocolAccountManager(stored, directory, reservations).pool(List.of("alice", "bob", "carol"));

		AccountPool.AccountLease lease = pool.lease(BEDROCK);

		assertEquals("carol", lease.account().getAccountId());
		assertEquals(AccountAccess.available(BEDROCK, "Carol", carol), lease.account().access(BEDROCK));
		assertEquals(List.of("alice bedrock", "carol bedrock"), stored.checks);
		var none = assertThrows(IllegalStateException.class, () -> pool.lease(BEDROCK));
		assertEquals("No account of the pool is available for edition 'bedrock': alice (bedrock: unavailable, Account 'alice' "
				+ "owns no Bedrock profile), bob (in use), carol (in use)", none.getMessage());
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
	 * Stored accounts without files, as the libraries' account workflow lists them now; a check records the result
	 * the test prepared for the account.
	 */
	private static final class StoredAccounts implements ProtocolAuthentication {
		private final Map<String, AccountAccess> results = new HashMap<>();
		private final List<String> checks = new ArrayList<>();
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
		public @NotNull AccountAccess check(@NotNull String accountId, @NotNull Edition edition) {
			checks.add(accountId + " " + edition);
			AccountAccess result = results.get(accountId);
			accounts = accounts.stream()
					.map(account -> account.getAccountId().equals(accountId) ? new AuthenticationAccount(accountId, List.of(result)) : account)
					.toList();

			return result;
		}

		@Override
		public void logout(@NotNull String accountId, @NotNull Consumer<String> output) {
			throw new UnsupportedOperationException();
		}
	}
}
