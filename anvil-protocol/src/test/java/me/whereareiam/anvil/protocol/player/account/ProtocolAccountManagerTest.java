package me.whereareiam.anvil.protocol.player.account;

import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import me.whereareiam.anvil.api.player.account.AccountPool;
import me.whereareiam.anvil.protocol.api.provider.ProtocolAuthentication;
import me.whereareiam.anvil.protocol.api.provider.ProtocolBackend;
import me.whereareiam.anvil.protocol.api.provider.ProtocolProvider;
import me.whereareiam.anvil.protocol.api.provider.ProtocolRuntimeResolver;
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

	@TempDir
	Path directory;

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
		return new ProtocolAccountManager(new FixtureProvider(), directory, reservations);
	}

	private void pools(String declarations) throws Exception {
		Files.writeString(directory.resolve(AccountPoolFile.FILE_NAME), declarations);
	}

	private static final class FixtureProvider implements ProtocolProvider {
		@Override
		public String id() {
			return "fixture";
		}

		@Override
		public ProtocolBackend create(Path cache, Path accountDirectory, ProtocolRuntimeResolver artifacts) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Optional<ProtocolAuthentication> authentication(Path directory) {
			return Optional.of(new ProtocolAuthentication() {
				@Override
				public List<AuthenticationAccount> accounts() {
					return ACCOUNTS;
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
}
