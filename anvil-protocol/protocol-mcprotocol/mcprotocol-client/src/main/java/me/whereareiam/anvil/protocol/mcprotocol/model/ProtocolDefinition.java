package me.whereareiam.anvil.protocol.mcprotocol.model;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.protocol.api.model.ProtocolSupport;
import org.jetbrains.annotations.NotNull;

import java.net.URI;

/**
 * Internal immutable protocol artifact catalog entry.
 */
@Value
@Builder
public class ProtocolDefinition {
	@NotNull ProtocolSupport support;
	@NotNull URI artifact;
	@NotNull String sha256;
	@NotNull String coordinate;
}
