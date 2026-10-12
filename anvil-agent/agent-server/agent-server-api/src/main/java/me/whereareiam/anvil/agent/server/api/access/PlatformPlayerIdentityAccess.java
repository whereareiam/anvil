package me.whereareiam.anvil.agent.server.api.access;

import me.whereareiam.anvil.agent.api.model.AgentIdentity;
import me.whereareiam.anvil.agent.server.api.PlatformAgent;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

/**
 * Provides player identities observed by a platform agent.
 *
 * <p>This is a required part of the current {@link PlatformAgent} contract. Additional agent
 * features should use their own focused interfaces rather than adding unrelated methods here.</p>
 */
public interface PlatformPlayerIdentityAccess {
	/**
	 * Looks up a player known by the platform by the UUID its client was granted at login, never by its name:
	 * platforms match names without regard to letter case. A platform that keeps another UUID for the player than
	 * the one it shows to the player's client matches the client's UUID and reports its own in the identity.
	 *
	 * @param uniqueId UUID the player's client was granted
	 * @return observed identity carrying the platform's own UUID, or empty when the player is absent
	 */
	@NotNull Optional<AgentIdentity> identity(@NotNull UUID uniqueId);
}
