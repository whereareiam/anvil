package me.whereareiam.anvil.api.scenario;

import org.jetbrains.annotations.NotNull;

/**
 * Adds functionality to each ready scenario before its setup hook executes.
 * Each returned attachment owns only the resources acquired for that particular scenario.
 */

public interface ScenarioExtension {
	/**
	 * Installs a contribution against the public scenario contract.
	 * Roll back acquisitions if attachment fails before a result can be returned.
	 * The supplied context is borrowed; its lifecycle remains owned by the engine.
	 *
	 * @param scenario borrowed ready process and player access
	 * @return contribution finalized before the underlying scenario is released
	 */
	@NotNull ScenarioAttachment attach(@NotNull ScenarioContext scenario);
}
