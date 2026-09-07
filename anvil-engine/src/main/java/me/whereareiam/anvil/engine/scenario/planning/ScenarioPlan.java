package me.whereareiam.anvil.engine.scenario.planning;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.engine.scenario.topology.ForwardingPlan;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable decisions produced by scenario planning before resources are acquired.
 */
@RequiredArgsConstructor
public final class ScenarioPlan {
    private final AnvilScenario scenario;
    private final ForwardingPlan forwarding;

    /**
     * Returns the validated scenario declaration.
     */
    public @NotNull AnvilScenario definition() {
        return scenario;
    }

    /**
     * Returns forwarding assignments for every declared process.
     */
    public @NotNull ForwardingPlan forwarding() {
        return forwarding;
    }
}
