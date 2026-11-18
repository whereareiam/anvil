package me.whereareiam.anvil.agent.api.model;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.agent.api.type.AgentRole;
import org.jetbrains.annotations.NotNull;

/**
 * Runtime information reported by a platform agent during readiness checks.
 */
@Value
@Builder
public class AgentInfo {
    /**
     * Stable Anvil platform identifier.
     */
    @NotNull String platform;
    /**
     * Platform-reported version.
     */
    @NotNull String version;
    /**
     * Kind of process hosting the agent.
     */
    @NotNull AgentRole role;
}
