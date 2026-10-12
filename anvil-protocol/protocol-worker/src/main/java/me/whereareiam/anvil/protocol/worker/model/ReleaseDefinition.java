package me.whereareiam.anvil.protocol.worker.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * One release of a protocol library from release data: the release offered to player selection, the coordinate
 * its segments compile against and the worker runtime closure, in classpath order.
 */
@Value
@Builder(toBuilder = true)
public class ReleaseDefinition {
	/**
	 * Release offered to player selection.
	 */
	@NotNull ProtocolRelease release;
	/**
	 * Maven coordinate of the library release itself.
	 */
	@NotNull String module;
	/**
	 * Worker runtime closure in classpath order, including the release module.
	 */
	@Singular
	@NotNull List<ReleaseArtifact> artifacts;
}
