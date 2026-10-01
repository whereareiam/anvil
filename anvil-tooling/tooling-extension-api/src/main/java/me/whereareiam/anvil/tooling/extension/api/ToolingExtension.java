package me.whereareiam.anvil.tooling.extension.api;

import org.jetbrains.annotations.NotNull;

/**
 * Registers portable tooling features discovered through ServiceLoader on the project runtime.
 * Registration declares handlers without creating scenarios or acquiring process resources.
 */
public interface ToolingExtension {
	/**
	 * Contributes actions and observations before the first environment starts.
	 *
	 * @param registration registration scope, closed when this call returns
	 */
	void register(@NotNull ToolingRegistration registration);
}
