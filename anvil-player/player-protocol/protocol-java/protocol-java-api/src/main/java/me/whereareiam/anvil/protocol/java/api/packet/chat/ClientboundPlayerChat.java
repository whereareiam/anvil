package me.whereareiam.anvil.protocol.java.api.packet.chat;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.model.chat.MessageSignature;
import me.whereareiam.anvil.protocol.java.api.model.chat.PackedMessageSignature;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.type.FilterMaskType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A chat message a player sent, as the server relays it. Releases before Minecraft 1.19 neither sign nor filter chat:
 * their messages pass through, without a signature and with nothing last seen.
 */
@Value
public class ClientboundPlayerChat implements ClientboundPacket {
	/**
	 * The message as plain text: the server's unsigned replacement when it sent one, otherwise the signed content.
	 */
	@NotNull String text;
	/**
	 * How much of the message the server's text filter hides.
	 */
	@NotNull FilterMaskType filterMask;
	/**
	 * The message's signature, or {@code null} when the sender did not sign it.
	 */
	@Nullable MessageSignature signature;
	/**
	 * The signatures of the messages the sender had seen when it sent this one, oldest first, packed against the
	 * client's signature cache.
	 */
	@NotNull List<PackedMessageSignature> lastSeen;

	/**
	 * Creates a message with a copy of the last seen signatures.
	 *
	 * @param text the message as plain text
	 * @param filterMask how much of the message the server's text filter hides
	 * @param signature the message's signature, or {@code null}
	 * @param lastSeen the signatures the sender had seen, oldest first
	 */
	public ClientboundPlayerChat(
			@NotNull String text,
			@NotNull FilterMaskType filterMask,
			@Nullable MessageSignature signature,
			@NotNull List<PackedMessageSignature> lastSeen
	) {
		this.text = text;
		this.filterMask = filterMask;
		this.signature = signature;
		this.lastSeen = List.copyOf(lastSeen);
	}

	/**
	 * Creates a message that passes through unsigned, as every message of a release before Minecraft 1.19 does.
	 *
	 * @param text the message as plain text
	 * @return the message
	 */
	public static @NotNull ClientboundPlayerChat unsigned(@NotNull String text) {
		return new ClientboundPlayerChat(text, FilterMaskType.PASS_THROUGH, null, List.of());
	}
}
