package me.whereareiam.anvil.api.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

/**
 * Edition of Minecraft a server speaks and a protocol release implements, identified by an opaque identifier.
 *
 * <p>Anvil compares editions only for equality: a player reaches a server only with a protocol release of the
 * server's edition, even when both list the same {@link MinecraftVersion}. The core names no edition and
 * defines no default. Each platform declares the edition of its servers and each protocol library the edition
 * of each of its releases, both in their own data files.</p>
 *
 * <pre>{@code
 * MinecraftEdition server = MinecraftEdition.of("example");
 * boolean reachable = server.equals(release.getEdition());
 * }</pre>
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MinecraftEdition {
	private static final Pattern IDENTIFIER = Pattern.compile("[a-z][a-z0-9-]*");

	/**
	 * Lower-case identifier, unique among editions.
	 */
	@NotNull String id;

	/**
	 * Returns the edition with an identifier.
	 *
	 * @param id lower-case identifier that starts with a letter and continues with letters, digits or hyphens
	 * @return the edition, equal to every other edition with the same identifier
	 * @throws IllegalArgumentException when the identifier is not of that form
	 */
	public static @NotNull MinecraftEdition of(@NotNull String id) {
		if (!IDENTIFIER.matcher(id).matches())
			throw new IllegalArgumentException("Not a Minecraft edition identifier: '" + id + "'");

		return new MinecraftEdition(id);
	}

	/**
	 * Returns the identifier, as data files and messages name the edition.
	 *
	 * @return edition identifier
	 */
	@Override
	public @NotNull String toString() {
		return id;
	}
}
