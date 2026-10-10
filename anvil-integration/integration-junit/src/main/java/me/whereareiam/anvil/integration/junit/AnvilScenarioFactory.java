package me.whereareiam.anvil.integration.junit;

import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;

/**
 * Builds the scenario that one environment annotation declares. The annotation's members are the
 * environment's parameters, so a test reads its environment where it is declared and one factory
 * serves every combination.
 *
 * <p>Give equal declarations equal scenario names and different declarations different names: the
 * name keys the scenario's workspaces and caches.</p>
 *
 * @param <A> environment annotation carrying {@link AnvilEnvironment}
 */
public interface AnvilScenarioFactory<A extends Annotation> {
	/**
	 * Builds the scenario for one declaration. Implement this method when the scenario consists of its
	 * processes only.
	 *
	 * @param declaration environment annotation as written on the test method or class
	 * @return scenario started fresh around the test
	 */
	default @NotNull AnvilScenario create(@NotNull A declaration) {
		throw new UnsupportedOperationException(getClass().getName() + " must implement one of the create methods");
	}

	/**
	 * Builds the scenario for one declaration together with the objects it needs besides its processes.
	 * Implement this method instead of {@link #create(Annotation)} to start such an object, name it in the
	 * scenario and hand it to the test; see {@link ScenarioResources}. The default builds the scenario alone.
	 *
	 * @param declaration environment annotation as written on the test method or class
	 * @param resources receives the objects the scenario owns
	 * @return scenario started fresh around the test
	 */
	default @NotNull AnvilScenario create(@NotNull A declaration, @NotNull ScenarioResources resources) {
		return create(declaration);
	}
}
