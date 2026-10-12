package me.whereareiam.anvil.protocol.api.worker.model;

import lombok.Builder;
import lombok.ToString;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * What the native worker stores of one signed-in account: the profile that tooling lists, and the library's
 * credentials, which only the library reads. The worker writes it into the account's private file, labelled with
 * the library that owns it.
 *
 * <pre>{@code
 * AccountRecord account = AccountRecord.builder()
 *         .accountId("alice")
 *         .username("Alice")
 *         .uniqueId(uuid)
 *         .credentials(manager.toJson().toString())
 *         .build();
 * }</pre>
 */
@Value
@Builder(toBuilder = true)
public class AccountRecord {
	/**
	 * Owner-local account ID, the name of the account's file.
	 */
	@NotNull String accountId;
	/**
	 * Minecraft username of the signed-in profile.
	 */
	@NotNull String username;
	/**
	 * Profile UUID of the signed-in profile.
	 */
	@NotNull UUID uniqueId;
	/**
	 * The library's credentials as the text of a JSON object, such as a serialized token chain with its refresh
	 * token. Only the library that wrote them reads them.
	 */
	@ToString.Exclude
	@NotNull String credentials;
}
