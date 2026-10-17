package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtByte;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtCompound;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtFloat;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtInt;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtList;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtString;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.dialog.ClientboundClearDialog;
import me.whereareiam.anvil.protocol.java.api.packet.dialog.ClientboundShowDialog;
import me.whereareiam.anvil.protocol.java.api.packet.dialog.ServerboundCustomClickAction;
import net.kyori.adventure.key.Key;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.geysermc.mcprotocollib.protocol.data.game.Holder;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundClearDialogPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundCustomClickActionPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundRegistryDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundShowDialogConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundShowDialogGamePacket;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class DialogPacketsTest {
	private static final NbtMap NOTICE = NbtMap.builder()
			.putString("type", "minecraft:notice")
			.putCompound("title", NbtMap.builder().putString("text", "Rules").putList("extra", NbtType.STRING, "!").build())
			.putList("body", NbtType.COMPOUND, NbtMap.builder().putString("type", "minecraft:plain_message")
					.putString("contents", "Be kind").build())
			.putBoolean("can_close_with_escape", false)
			.build();
	private static final NbtCompound NOTICE_TAGS = NbtCompound.builder()
			.put("type", new NbtString("minecraft:notice"))
			.put("title", NbtCompound.builder()
					.put("text", new NbtString("Rules"))
					.put("extra", new NbtList(List.of(new NbtString("!"))))
					.build())
			.put("body", new NbtList(List.of(NbtCompound.builder()
					.put("type", new NbtString("minecraft:plain_message"))
					.put("contents", new NbtString("Be kind"))
					.build())))
			.put("can_close_with_escape", NbtByte.of(false))
			.build();

	private final DialogPackets packets = new DialogPackets();

	@Test
	void decodesADialogShownInTheGameAsItsNbt() {
		assertEquals(new ClientboundShowDialog(null, NOTICE_TAGS, false),
				packets.decode(new ClientboundShowDialogGamePacket(Holder.ofCustom(NOTICE))));
	}

	@Test
	void decodesADialogShownByRegistryIdWithItsIdOnly() {
		assertEquals(new ClientboundShowDialog(3, null, false), packets.decode(new ClientboundShowDialogGamePacket(Holder.ofId(3))));
	}

	@Test
	void decodesConfigurationDialogsAndClearedDialogs() {
		assertEquals(new ClientboundShowDialog(null, NOTICE_TAGS, true),
				packets.decode(new ClientboundShowDialogConfigurationPacket(NOTICE)));
		assertEquals(new ClientboundClearDialog(), packets.decode(new ClientboundClearDialogPacket()));
	}

	@Test
	void encodesACustomClickWithItsPayload() {
		NbtCompound payload = NbtCompound.builder()
				.put("source", new NbtString("dialog"))
				.put("page", new NbtInt(2))
				.put("amount", new NbtFloat(2.5F))
				.build();

		ServerboundCustomClickActionPacket click = encode(new ServerboundCustomClickAction("example:submit", payload));

		assertEquals(Key.key("example:submit"), click.getId());
		assertEquals(NbtMap.builder()
				.putString("source", "dialog")
				.putInt("page", 2)
				.putFloat("amount", 2.5F)
				.build(), click.getPayload());
	}

	@Test
	void encodesACustomClickWithoutAPayload() {
		ServerboundCustomClickActionPacket click = encode(new ServerboundCustomClickAction("example:ping", null));

		assertEquals(Key.key("example:ping"), click.getId());
		assertNull(click.getPayload());
	}

	@Test
	void leavesPacketsOfOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ClientboundKeepAlivePacket(7)));
		assertNull(packets.decode(new ClientboundRegistryDataPacket(Key.key("minecraft:dialog"), List.of())));
		assertNull(packets.encode(new UnknownPacket()));
	}

	@Test
	void carriesEveryDialogPacket() {
		assertEquals(Set.of(
				ClientboundShowDialog.class,
				ClientboundClearDialog.class,
				ServerboundCustomClickAction.class
		), packets.packets());
	}

	private ServerboundCustomClickActionPacket encode(ServerboundCustomClickAction click) {
		return assertInstanceOf(ServerboundCustomClickActionPacket.class, packets.encode(click));
	}

	private static final class UnknownPacket implements ServerboundPacket {
	}
}
