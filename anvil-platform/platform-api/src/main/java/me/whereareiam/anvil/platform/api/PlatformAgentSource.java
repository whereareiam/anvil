package me.whereareiam.anvil.platform.api;

import me.whereareiam.anvil.platform.api.model.PlatformAgentDescriptor;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Locates the packaged agent requested by a platform provider for workspace installation.
 * Agent packaging and classpath discovery remain outside platform preparation.
 */

public interface PlatformAgentSource {
	/**
	 * Resolves the exact agent artifact described by the platform.
	 * @param agent platform agent installation descriptor
	 * @return packaged local agent JAR
	 */
	@NotNull Path locate(@NotNull PlatformAgentDescriptor agent);
}
