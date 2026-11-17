package me.whereareiam.anvil.protocol.java.api.packet.overlay;

import lombok.Value;
import lombok.experimental.FieldNameConstants;
import me.whereareiam.anvil.protocol.java.api.model.overlay.BossEventOperation;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Adds, removes or changes one boss bar shown to the player. An update carries only the part of the bar it changes,
 * and the client ignores an update or removal of a bar the server never added.
 */
@Value
@FieldNameConstants
public class ClientboundBossEvent implements ClientboundPacket {
	/**
	 * The ID the server gave the bar.
	 */
	@NotNull UUID id;
	/**
	 * What the packet does to the bar.
	 */
	@NotNull BossEventOperation operation;
}
