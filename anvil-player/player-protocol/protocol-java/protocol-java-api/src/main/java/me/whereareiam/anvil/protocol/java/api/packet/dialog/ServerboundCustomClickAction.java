package me.whereareiam.anvil.protocol.java.api.packet.dialog;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.model.dialog.DialogInputValue;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A custom click the player sends, such as a dialog button's custom action, for the server to handle by its ID. Its
 * payload is the compound the action declared, with the value of each input put in under the input's key, replacing
 * a declared entry of the same key, as the client builds it for a dynamic custom click. Releases before Minecraft
 * 1.21.6 have no custom clicks.
 */
@Value
public class ServerboundCustomClickAction implements ServerboundPacket {
	/**
	 * The click's ID with its namespace, such as {@code example:submit}.
	 */
	@NotNull String id;
	/**
	 * The payload or additions the action declared, as SNBT exactly as {@code DialogAction#getPayload()} carries it,
	 * or {@code null} when it declared none.
	 */
	@Nullable String payload;
	/**
	 * The input values put into the payload, in input order; empty for a static custom click.
	 */
	@NotNull List<DialogInputValue> values;
}
