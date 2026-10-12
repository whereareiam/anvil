package me.whereareiam.anvil.agent.api.model;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.agent.api.model.location.AgentLocation;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Identity reported by one running platform for a player.
 */
@Value
@Builder
public class AgentIdentity {
    /**
     * Player name reported by the platform.
     */
    @NotNull String username;
    /**
     * The platform's own UUID of the player. It may differ from the UUID the player was looked up by, the one its
     * client was granted, when the platform keeps another UUID for the player than it shows to clients.
     */
    @NotNull UUID uniqueId;
    /**
     * Explicit server or proxy location reported by the observing platform.
     */
    @NotNull AgentLocation location;
}
