package me.whereareiam.anvil.agent.api.platform.access;

import me.whereareiam.anvil.agent.api.model.AgentIdentity;
import me.whereareiam.anvil.agent.api.platform.PlatformAgent;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Provides player identities observed by a platform agent.
 *
 * <p>This is a required part of the current {@link PlatformAgent} contract. Additional agent
 * features should use their own focused interfaces rather than adding unrelated methods here.</p>
 */
public interface PlatformPlayerIdentityAccess {
	/**
	 * Looks up a player known by the platform.
	 *
	 * @param username player name
	 * @return observed identity, or empty when the player is absent
	 */
	@NotNull Optional<AgentIdentity> identity(@NotNull String username);
}
