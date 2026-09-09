package me.whereareiam.anvil.api.engine;

import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import org.jetbrains.annotations.NotNull;

/**
 * Collects global engine options and extensions before acquiring their shared resources.
 */
public interface EngineBuilder {
	/**
	 * Selects immutable engine options without starting services.
	 *
	 * @param options scenario validation and assembly configuration
	 * @return this builder
	 */
	@NotNull EngineBuilder options(@NotNull EngineOptions options);

	/**
	 * Adds an extension to install when building this engine.
	 *
	 * @param extension global contribution
	 * @return this builder
	 */
	@NotNull EngineBuilder extension(@NotNull EngineExtension extension);

	/**
	 * Installs contributions and transfers their resources to a caller-owned engine.
	 * A builder can be consumed only once. Failed construction closes all transferred resources.
	 *
	 * @return engine ready to accept scenarios
	 */
	@NotNull ScenarioEngine build();
}
