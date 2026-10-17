package me.whereareiam.anvil.platform.api;

import me.whereareiam.anvil.platform.api.model.PlatformAgentDescriptor;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;

/**
 * Locates the packaged agent requested by a platform provider, and the agent extensions installed with it, for
 * workspace installation. Agent packaging and classpath discovery remain outside platform preparation.
 */
public interface PlatformAgentSource {
	/**
	 * Resolves the exact agent artifact described by the platform.
	 * @param agent platform agent installation descriptor
	 * @return packaged local agent JAR
	 */
	@NotNull Path locate(@NotNull PlatformAgentDescriptor agent);

	/**
	 * Locates the agent extension JARs that the processes of a platform install next to its agent, such as the
	 * platform half of an agent-backed capability.
	 *
	 * @param platformId platform provider ID declared by the process, such as {@code paper}
	 * @return packaged local extension JARs; none by default
	 */
	default @NotNull List<Path> extensions(@NotNull String platformId) {
		return List.of();
	}
}
