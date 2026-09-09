package me.whereareiam.anvil.api.engine;

import org.jetbrains.annotations.NotNull;

/**
 * Installs global engine functionality and transfers its shared resource ownership during assembly.
 * Scoped service contracts remain in their owning APIs and are bound by the extension implementation.
 */

public interface EngineExtension {
	/**
	 * Registers engine contributions before any scenario can start.
	 *
	 * @param registration typed global registration boundary
	 */
	void install(@NotNull EngineRegistration registration);
}
