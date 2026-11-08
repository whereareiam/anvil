package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.protocol.api.model.text.Text;
import me.whereareiam.anvil.protocol.api.model.text.TextColor;
import me.whereareiam.anvil.protocol.api.model.text.TextStyle;
import me.whereareiam.anvil.protocol.api.model.text.click.ChangePageClick;
import me.whereareiam.anvil.protocol.api.model.text.click.CopyToClipboardClick;
import me.whereareiam.anvil.protocol.api.model.text.click.CustomClick;
import me.whereareiam.anvil.protocol.api.model.text.click.RunCommandClick;
import me.whereareiam.anvil.protocol.api.model.text.content.KeybindContent;
import me.whereareiam.anvil.protocol.api.model.text.content.LiteralContent;
import me.whereareiam.anvil.protocol.api.model.text.content.PlayerHeadContent;
import me.whereareiam.anvil.protocol.api.model.text.content.ScoreContent;
import me.whereareiam.anvil.protocol.api.model.text.content.SelectorContent;
import me.whereareiam.anvil.protocol.api.model.text.content.SpriteContent;
import me.whereareiam.anvil.protocol.api.model.text.content.TranslatedContent;
import me.whereareiam.anvil.protocol.java.api.model.chat.MessageSignature;
import me.whereareiam.anvil.protocol.java.api.model.chat.PackedMessageSignature;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundDeleteChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundDisguisedChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundPlayerChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChatCommand;
import me.whereareiam.anvil.protocol.java.api.type.FilterMaskType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.object.ObjectContents;
import org.geysermc.mcprotocollib.protocol.data.game.Holder;
import org.geysermc.mcprotocollib.protocol.data.game.chat.ChatFilterType;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundDeleteChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundDisguisedChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.BitSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static me.whereareiam.anvil.protocol.api.type.TextDecoration.BOLD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatPacketsTest {
	private final ChatPackets packets = new ChatPackets(new TextTranslator());

	@Test
	void decodesSystemChatWithTranslationArgumentsAndChildren() {
		Component message = Component.translatable("multiplayer.player.joined", Component.text("Alice")).append(Component.text("!"));

		assertEquals(new ClientboundSystemChat(Text.builder()
				.content(new TranslatedContent("multiplayer.player.joined", null, List.of(Text.literal("Alice"))))
				.child(Text.literal("!"))
				.build(), false), packets.decode(new ClientboundSystemChatPacket(message, false)));
	}

	@Test
	void decodesSystemChatWithTheStyleOfEachPartAndTheValueOfItsClick() {
		Component message = Component.text("Welcome to ", NamedTextColor.GRAY)
				.append(Component.text("Arbor", NamedTextColor.GOLD, TextDecoration.BOLD)
						.clickEvent(ClickEvent.runCommand("/arbor help")));

		var decoded = (ClientboundSystemChat) packets.decode(new ClientboundSystemChatPacket(message, false));

		assertEquals(Text.builder()
				.content(new LiteralContent("Welcome to "))
				.style(TextStyle.builder().color(TextColor.GRAY).build())
				.child(Text.builder()
						.content(new LiteralContent("Arbor"))
						.style(TextStyle.builder()
								.color(TextColor.GOLD)
								.decoration(BOLD, true)
								.click(new RunCommandClick("/arbor help"))
								.build())
						.build())
				.build(), decoded.getText());
	}

	@Test
	void readsEveryClickPayloadTheShadowColourAndObjects() {
		UUID owner = UUID.randomUUID();
		Component message = Component.text("1").clickEvent(ClickEvent.changePage(3))
				.append(Component.text("2").clickEvent(ClickEvent.custom(Key.key("arbor", "vote"), BinaryTagHolder.binaryTagHolder("{}"))))
				.append(Component.text("3").clickEvent(ClickEvent.copyToClipboard("copied"))
						.shadowColor(ShadowColor.shadowColor(0x80123456)))
				.append(Component.object(ObjectContents.sprite(Key.key("minecraft", "blocks"), Key.key("minecraft", "block/stone"))))
				.append(Component.object(ObjectContents.playerHead(owner)));

		var decoded = (ClientboundSystemChat) packets.decode(new ClientboundSystemChatPacket(message, false));

		List<Text> parts = decoded.getText().getChildren();
		assertEquals(new ChangePageClick(3), decoded.getText().getStyle().getClick());
		assertEquals(new CustomClick("arbor:vote"), parts.get(0).getStyle().getClick());
		assertEquals(new CopyToClipboardClick("copied"), parts.get(1).getStyle().getClick());
		assertEquals(new me.whereareiam.anvil.protocol.api.model.text.ShadowColor(0x80123456), parts.get(1).getStyle().getShadowColor());
		assertEquals(new SpriteContent("minecraft:blocks", "minecraft:block/stone"), parts.get(2).getContent());
		assertEquals(new PlayerHeadContent(null, owner, null, true), parts.get(3).getContent());
	}

	@Test
	void decodesOverlaySystemChatShownAboveTheHotbar() {
		ClientboundSystemChatPacket overlay = new ClientboundSystemChatPacket(Component.text("anvil:action-bar"), true);

		assertEquals(new ClientboundSystemChat(Text.literal("anvil:action-bar"), true), packets.decode(overlay));
	}

	@Test
	void decodesPlayerChatAsItsSignedContentWithoutUnsignedContent() {
		assertEquals(ClientboundPlayerChat.unsigned(Text.literal("signed hello")), packets.decode(playerChat("signed hello", null)));
	}

	@Test
	void decodesPlayerChatAsItsUnsignedContentWhenTheServerDecoratedIt() {
		Component decorated = Component.text("decorated ").append(Component.text("hello"));

		assertEquals(ClientboundPlayerChat.unsigned(Text.builder().content(new LiteralContent("decorated ")).child(Text.literal("hello")).build()), packets.decode(playerChat("signed hello", decorated)));
	}

	@Test
	void decodesPlayerChatWithItsSignatureFilterAndTheMessagesItsSenderHadSeen() {
		byte[] signature = signature(7);
		ClientboundPlayerChatPacket chat = new ClientboundPlayerChatPacket(0, new UUID(0, 1), 0, signature, "hello", 0L, 0L,
				List.of(new org.geysermc.mcprotocollib.protocol.data.game.chat.MessageSignature(3, null),
						new org.geysermc.mcprotocollib.protocol.data.game.chat.MessageSignature(-1, signature(9))),
				null, ChatFilterType.FULLY_FILTERED, Holder.ofId(0), Component.text("Alice"), null);

		assertEquals(new ClientboundPlayerChat(Text.literal("hello"), FilterMaskType.FULLY_FILTERED, new MessageSignature(signature(7)),
				List.of(PackedMessageSignature.cached(3), PackedMessageSignature.full(new MessageSignature(signature(9))))),
				packets.decode(chat));
	}

	@Test
	void decodesDeletionsByCachedOrWholeSignature() {
		assertEquals(new ClientboundDeleteChat(PackedMessageSignature.cached(5)), packets.decode(
				new ClientboundDeleteChatPacket(new org.geysermc.mcprotocollib.protocol.data.game.chat.MessageSignature(5, null))));
		assertEquals(new ClientboundDeleteChat(PackedMessageSignature.full(new MessageSignature(signature(2)))),
				packets.decode(new ClientboundDeleteChatPacket(
						new org.geysermc.mcprotocollib.protocol.data.game.chat.MessageSignature(-1, signature(2)))));
	}

	@Test
	void decodesDisguisedChat() {
		ClientboundDisguisedChatPacket disguised = new ClientboundDisguisedChatPacket(Component.text("journey-ready-1"), Holder.ofId(1),
				Component.text("Server"), null);

		assertEquals(new ClientboundDisguisedChat(Text.literal("journey-ready-1")), packets.decode(disguised));
	}

	@Test
	void keepsKeybindsSelectorsAndScores() {
		Component message = Component.text("press ").append(Component.keybind("key.jump"))
				.append(Component.text(" near ")).append(Component.selector("@p"))
				.append(Component.score("Alice", "kills")).append(Component.text("!"));

		assertEquals(new ClientboundSystemChat(Text.builder()
				.content(new LiteralContent("press "))
				.child(Text.builder().content(new KeybindContent("key.jump")).build())
				.child(Text.literal(" near "))
				.child(Text.builder().content(new SelectorContent("@p", null)).build())
				.child(Text.builder().content(new ScoreContent("Alice", "kills")).build())
				.child(Text.literal("!"))
				.build(), false), packets.decode(new ClientboundSystemChatPacket(message, false)));
	}

	@Test
	void encodesChatUnsignedWithNothingAcknowledgedAndAnUnverifiedChecksum() {
		long before = Instant.now().toEpochMilli();

		ServerboundChatPacket chat = assertInstanceOf(ServerboundChatPacket.class, packets.encode(new ServerboundChat("hello there")));

		assertEquals("hello there", chat.getMessage());
		assertTrue(chat.getTimeStamp() >= before, "timestamp " + chat.getTimeStamp());
		assertEquals(0L, chat.getSalt());
		assertNull(chat.getSignature());
		assertEquals(0, chat.getOffset());
		assertEquals(new BitSet(20), chat.getAcknowledgedMessages());
		assertEquals(0, chat.getChecksum());
	}

	@Test
	void encodesCommandsThroughTheUnsignedCommandPacket() {
		Object command = packets.encode(new ServerboundChatCommand("anvil-fixture ping"));

		assertEquals("anvil-fixture ping", assertInstanceOf(ServerboundChatCommandPacket.class, command).getCommand());
	}

	@Test
	void leavesPacketsOfOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ClientboundKeepAlivePacket(7)));
		assertNull(packets.encode(new UnknownPacket()));
	}

	@Test
	void carriesEveryChatPacket() {
		assertEquals(Set.of(
				ClientboundSystemChat.class,
				ClientboundPlayerChat.class,
				ClientboundDisguisedChat.class,
				ClientboundDeleteChat.class,
				ServerboundChat.class,
				ServerboundChatCommand.class
		), packets.packets());
	}

	private static ClientboundPlayerChatPacket playerChat(String content, @Nullable Component unsignedContent) {
		return new ClientboundPlayerChatPacket(0, new UUID(0, 1), 0, null, content, 0L, 0L, List.of(), unsignedContent,
				ChatFilterType.PASS_THROUGH, Holder.ofId(0), Component.text("Alice"), null);
	}

	private static byte[] signature(int seed) {
		byte[] bytes = new byte[256];
		bytes[0] = (byte) seed;

		return bytes;
	}

	private static final class UnknownPacket implements ServerboundPacket {
	}
}
