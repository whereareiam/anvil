package me.whereareiam.anvil.engine.scenario.topology;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.platform.api.type.ForwardingMode;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * Immutable forwarding assignments derived from the validated process topology.
 */
@RequiredArgsConstructor
public final class ForwardingPlan {
    private final List<Group> groups;

    /**
     * Returns the deterministic connected-component assignments.
     */
    public @NotNull List<Group> groups() {
        return groups;
    }

    /**
     * One forwarding assignment shared by all processes in a connected component.
     */
    public record Group(@NotNull Set<String> processes, @NotNull ForwardingMode mode, boolean proxyOnlineMode) {
    }
}
