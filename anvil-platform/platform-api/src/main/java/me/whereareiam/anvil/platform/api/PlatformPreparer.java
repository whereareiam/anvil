package me.whereareiam.anvil.platform.api;

import me.whereareiam.anvil.platform.api.model.PlatformRequest;
import me.whereareiam.anvil.platform.api.model.ProcessPlan;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Prepares software and applies platform-owned settings after workspace assets are installed.
 * Runtime endpoints are supplied by the execution provider, independently of the platform.
 */
public interface PlatformPreparer {
	/**
	 * Resolves the pinned executable into a prepared workspace.
	 *
	 * @param process validated process plan
	 * @param request workspace and execution-selected endpoints
	 * @return executable JAR
	 * @throws IOException when acquisition or installation fails
	 */
	@NotNull Path resolve(@NotNull ProcessPlan process, @NotNull PlatformRequest request) throws IOException;

	/**
	 * Applies runtime-owned settings, preserving unrelated platform configuration.
	 * Called after preparation and again before a replacement process generation starts.
	 *
	 * @param process validated process plan
	 * @param request workspace and execution-selected endpoints
	 * @throws IOException when configuration cannot be written
	 */
	void configure(@NotNull ProcessPlan process, @NotNull PlatformRequest request) throws IOException;
}
