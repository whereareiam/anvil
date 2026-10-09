package me.whereareiam.anvil.protocol.player.account;

import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import me.whereareiam.anvil.api.player.account.AccountPool;
import me.whereareiam.anvil.protocol.api.library.ProtocolAuthentication;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibrary;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibraryProvider;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibraryRegistry;
import me.whereareiam.anvil.protocol.api.model.ProtocolLibraryContext;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolAccountManagerTest {
	private static final List<AuthenticationAccount> ACCOUNTS = List.of(
			new AuthenticationAccount("alice", "fixture", "Alice", UUID.randomUUID()),
			new AuthenticationAccount("bob", "fixture", "Bob", UUID.randomUUID())
	);
	private static final List<AuthenticationAccount> OTHER_ACCOUNTS = List.of(
			new AuthenticationAccount("carol", "other", "Carol", UUID.randomUUID()),
			new AuthenticationAccount("alice", "other", "Alice", UUID.randomUUID())
	);

	@TempDir
	Path directory;

	@Test
	void aggregatesTheAccountsOfEveryLibraryOfferingAuthentication() {
		var registry = new ProtocolLibraryRegistry(List.of(
				new FixtureLibrary("fixture", ACCOUNTS),
				new OfflineLibrary(),
				new FixtureLibrary("other", OTHER_ACCOUNTS)
		));
		var manager = new ProtocolAccountManager(registry, directory, new AccountReservations());

		var accounts = manager.list();

		assertEquals(List.of("alice", "bob", "carol", "alice"), accounts.stream().map(AuthenticationAccount::getAccountId).toList());
		assertEquals(List.of("fixture", "fixture", "other", "other"), accounts.stream().map(AuthenticationAccount::getLibraryId).toList());
		AccountPool pool = manager.pool(List.of("carol", "bob"));
		assertEquals(List.of("other", "fixture"), pool.accounts().stream().map(AuthenticationAccount::getLibraryId).toList());
		assertEquals("carol", pool.lease().account().getAccountId());
	}

	@Test
	void poolsRefuseAnIdThatSeveralLibrariesStore() {
		var registry = new ProtocolLibraryRegistry(List.of(
				new FixtureLibrary("fixture", ACCOUNTS),
				new FixtureLibrary("other", OTHER_ACCOUNTS)
		));
		var manager = new ProtocolAccountManager(registry, directory, new AccountReservations());

		var failure = assertThrows(IllegalArgumentException.class, () -> manager.pool(List.of("bob", "alice")));

		assertTrue(failure.getMessage().contains("Account 'alice' is stored by several protocol libraries [fixture, other]"),
				failure.getMessage());
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
		return new ProtocolAccountManager(new ProtocolLibraryRegistry(List.of(new FixtureLibrary("fixture", ACCOUNTS))),
				directory, reservations);
	}

	private void pools(String declarations) throws Exception {
		Files.writeString(directory.resolve(AccountPoolFile.FILE_NAME), declarations);
	}

	private record FixtureLibrary(String id, List<AuthenticationAccount> accounts) implements ProtocolLibraryProvider {
		@Override
		public List<ProtocolRelease> releases(ProtocolLibraryContext context) {
			return List.of();
		}

		@Override
		public ProtocolLibrary create(ProtocolLibraryContext context) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Optional<ProtocolAuthentication> authentication(Path directory) {
			return Optional.of(new ProtocolAuthentication() {
				@Override
				public List<AuthenticationAccount> accounts() {
					return accounts;
				}

				@Override
				public void login(String id, Consumer<String> output) {
				}

				@Override
				public void logout(String id, Consumer<String> output) {
				}
			});
		}
	}

	private static final class OfflineLibrary implements ProtocolLibraryProvider {
		@Override
		public String id() {
			return "offline";
		}

		@Override
		public List<ProtocolRelease> releases(ProtocolLibraryContext context) {
			return List.of();
		}

		@Override
		public ProtocolLibrary create(ProtocolLibraryContext context) {
			throw new UnsupportedOperationException();
		}
	}
}
