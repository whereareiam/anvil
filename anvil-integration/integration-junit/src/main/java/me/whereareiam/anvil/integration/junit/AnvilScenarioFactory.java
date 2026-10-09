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
	 * Builds the scenario for one declaration.
	 *
	 * @param declaration environment annotation as written on the test method or class
	 * @return scenario started fresh around the test
	 */
	@NotNull AnvilScenario create(@NotNull A declaration);
}
