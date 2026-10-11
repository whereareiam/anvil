package me.whereareiam.anvil.api.model.player;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Minecraft profile of a simulated player at one point of its connection: the profile its client asked for,
 * the one the joined process granted at login, or the one a server reports.
 *
 * <pre>{@code
 * PlayerProfile profile = new PlayerProfile("Alice", UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"));
 * }</pre>
 */
@Value
public class PlayerProfile {
	/**
	 * Profile name.
	 */
	@NotNull String username;

	/**
	 * Profile identity.
	 */
	@NotNull UUID uniqueId;
}
