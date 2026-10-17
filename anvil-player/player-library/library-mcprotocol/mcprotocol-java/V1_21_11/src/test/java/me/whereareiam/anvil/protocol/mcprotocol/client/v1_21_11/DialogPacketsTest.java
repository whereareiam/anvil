package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.protocol.java.api.model.dialog.Dialog;
import me.whereareiam.anvil.protocol.java.api.model.dialog.DialogAction;
import me.whereareiam.anvil.protocol.java.api.model.dialog.DialogButton;
import me.whereareiam.anvil.protocol.java.api.model.dialog.DialogInput;
import me.whereareiam.anvil.protocol.java.api.model.dialog.DialogInputValue;
import me.whereareiam.anvil.protocol.java.api.model.dialog.DialogOption;
import me.whereareiam.anvil.protocol.java.api.model.dialog.RegistryDialog;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.dialog.ClientboundClearDialog;
import me.whereareiam.anvil.protocol.java.api.packet.dialog.ClientboundRegistryData;
import me.whereareiam.anvil.protocol.java.api.packet.dialog.ClientboundShowDialog;
import me.whereareiam.anvil.protocol.java.api.packet.dialog.ServerboundCustomClickAction;
import me.whereareiam.anvil.protocol.java.api.type.DialogActionType;
import me.whereareiam.anvil.protocol.java.api.type.DialogAfterAction;
import me.whereareiam.anvil.protocol.java.api.type.DialogInputType;
import net.kyori.adventure.key.Key;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.geysermc.mcprotocollib.protocol.data.game.Holder;
import org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogPacketsTest {
	private final DialogPackets packets = new DialogPackets();

	@Test
	void readsAMultiActionFormWithEveryInputType() {
		NbtMap form = NbtMap.builder()
				.putString("type", "minecraft:multi_action")
				.putCompound("title", NbtMap.builder().putString("text", "Identica").putList("extra", NbtType.STRING, ": Log in").build())
				.putCompound("body", NbtMap.builder().putString("type", "minecraft:plain_message").putString("contents", "Welcome back").build())
				.putList("inputs", NbtType.COMPOUND,
						NbtMap.builder().putString("type", "minecraft:text").putString("key", "password").putString("label", "Password")
								.putInt("max_length", 64).build(),
						NbtMap.builder().putString("type", "boolean").putString("key", "remember").putString("label", "Remember")
								.putBoolean("initial", true).putString("on_true", "yes").build(),
						NbtMap.builder().putString("type", "minecraft:single_option").putString("key", "mode").putString("label", "Mode")
								.putList("options", NbtType.COMPOUND,
										NbtMap.builder().putString("id", "easy").putString("display", "Easy").build(),
										NbtMap.builder().putString("id", "hard").putString("display", "Hard").putBoolean("initial", true).build())
								.build(),
						NbtMap.builder().putString("type", "minecraft:number_range").putString("key", "amount").putString("label", "Amount")
								.putFloat("start", 1F).putFloat("end", 4F).putFloat("step", 0.5F).build(),
						NbtMap.builder().putString("type", "example:colour").putString("key", "colour").build())
				.putList("actions", NbtType.COMPOUND,
						NbtMap.builder().putString("label", "Log in").putCompound("action", NbtMap.builder()
								.putString("type", "minecraft:dynamic/run_command").putString("template", "login $(password)").build()).build(),
						NbtMap.builder().putString("label", "Help").putCompound("action", NbtMap.builder()
								.putString("type", "minecraft:dynamic/custom").putString("id", "example:help")
								.putCompound("additions", NbtMap.builder().putString("source", "dialog").putInt("page", 2).build()).build()).build(),
						NbtMap.builder().putString("label", "Rules").putCompound("action", NbtMap.builder()
								.putString("type", "minecraft:open_url").putString("url", "https://example.com/rules").build()).build())
				.putCompound("exit_action", NbtMap.builder().putString("label", "Leave").putCompound("action", NbtMap.builder()
						.putString("type", "minecraft:run_command").putString("command", "/hub").build()).build())
				.putBoolean("can_close_with_escape", false)
				.putString("after_action", "wait_for_response")
				.build();

		Dialog dialog = shown(Holder.ofCustom(form));

		assertEquals(Dialog.builder()
				.type("minecraft:multi_action")
				.title("Identica: Log in")
				.externalTitle("Identica: Log in")
				.body("Welcome back")
				.input(DialogInput.builder().key("password").type(DialogInputType.TEXT).label("Password").initial("").maxLength(64).build())
				.input(DialogInput.builder().key("remember").type(DialogInputType.BOOLEAN).label("Remember").initial("true")
						.onTrue("yes").onFalse("false").build())
				.input(DialogInput.builder().key("mode").type(DialogInputType.SINGLE_OPTION).label("Mode").initial("hard")
						.option(new DialogOption("easy", "Easy", false)).option(new DialogOption("hard", "Hard", true)).build())
				.input(DialogInput.builder().key("amount").type(DialogInputType.NUMBER_RANGE).label("Amount").initial("2.5")
						.start(1F).end(4F).step(0.5F).build())
				.input(DialogInput.builder().key("colour").type(DialogInputType.OTHER).label("").initial("").build())
				.button(new DialogButton("Log in", action(DialogActionType.DYNAMIC_RUN_COMMAND, "login $(password)", null), false))
				.button(new DialogButton("Help", action(DialogActionType.DYNAMIC_CUSTOM, "example:help", "{source:\"dialog\",page:2}"), false))
				.button(new DialogButton("Rules", action(DialogActionType.OPEN_URL, "https://example.com/rules", null), false))
				.button(new DialogButton("Leave", action(DialogActionType.RUN_COMMAND, "/hub", null), true))
				.closeWithEscape(false)
				.afterAction(DialogAfterAction.WAIT_FOR_RESPONSE)
				.build(), dialog);
	}

	@Test
	void readsTheDefaultsOfNoticesAndTheButtonsOfConfirmations() {
		NbtMap notice = NbtMap.builder()
				.putString("type", "minecraft:notice")
				.putCompound("title", NbtMap.builder().putString("translate", "menu.paused").build())
				.putList("body", NbtType.COMPOUND,
						NbtMap.builder().putString("type", "minecraft:plain_message").putString("contents", "First").build(),
						NbtMap.builder().putString("type", "minecraft:item").putCompound("item", NbtMap.builder().putString("id", "minecraft:diamond").build())
								.putCompound("description", NbtMap.builder().putString("contents", "Shiny").build()).build())
				.build();
		NbtMap confirmation = NbtMap.builder()
				.putString("type", "minecraft:confirmation")
				.putString("title", "Leave?")
				.putCompound("yes", NbtMap.builder().putString("label", "Yes").putCompound("action", NbtMap.builder()
						.putString("type", "minecraft:custom").putString("id", "example:leave").build()).build())
				.putCompound("no", NbtMap.builder().putString("label", "No").build())
				.build();

		Dialog shownNotice = shown(Holder.ofCustom(notice));
		Dialog shownConfirmation = shown(Holder.ofCustom(confirmation));

		assertEquals("menu.paused", shownNotice.getTitle());
		assertEquals(List.of("First", "Shiny"), shownNotice.getBody());
		assertEquals(List.of(new DialogButton("gui.ok", null, true)), shownNotice.getButtons());
		assertTrue(shownNotice.isCloseWithEscape());
		assertEquals(DialogAfterAction.CLOSE, shownNotice.getAfterAction());
		assertEquals(List.of(
				new DialogButton("Yes", action(DialogActionType.CUSTOM, "example:leave", null), false),
				new DialogButton("No", null, true)
		), shownConfirmation.getButtons());
	}

	@Test
	void decodesADialogShownByRegistryIdWithItsIdOnly() {
		assertEquals(new ClientboundShowDialog(3, null, false), packets.decode(new ClientboundShowDialogGamePacket(Holder.ofId(3))));
	}

	@Test
	void decodesConfigurationDialogsAndClearedDialogs() {
		ClientboundShowDialog configuration = assertInstanceOf(ClientboundShowDialog.class, packets.decode(
				new ClientboundShowDialogConfigurationPacket(NbtMap.builder().putString("type", "minecraft:notice")
						.putString("title", "Terms").build())));

		assertTrue(configuration.isConfiguration());
		assertNull(configuration.getRegistryId());
		assertEquals("Terms", configuration.getDialog().getTitle());
		assertEquals(new ClientboundClearDialog(), packets.decode(new ClientboundClearDialogPacket()));
	}

	@Test
	void decodesTheDialogRegistryOnly() {
		assertNull(packets.decode(new ClientboundRegistryDataPacket(Key.key("minecraft:dimension_type"), List.of())));

		ClientboundRegistryData registry = assertInstanceOf(ClientboundRegistryData.class, packets.decode(
				new ClientboundRegistryDataPacket(Key.key("minecraft:dialog"), List.of(
						new RegistryEntry(Key.key("minecraft:server_links"), null),
						new RegistryEntry(Key.key("example:rules"), NbtMap.builder().putString("type", "minecraft:notice")
								.putString("title", "Rules").build())
				))));

		assertEquals(List.of("minecraft:server_links", "example:rules"), registry.getDialogs().stream()
				.map(RegistryDialog::getName)
				.toList());
		assertNull(registry.getDialogs().getFirst().getDialog());
		assertEquals("Rules", registry.getDialogs().get(1).getDialog().getTitle());
	}

	@Test
	void readsNestedDialogsAndTheEntriesOfADialogList() {
		NbtMap rules = NbtMap.builder().putString("type", "minecraft:notice").putString("title", "Rules")
				.putString("external_title", "Server rules").build();
		NbtMap list = NbtMap.builder()
				.putString("type", "minecraft:dialog_list")
				.putString("title", "Menu")
				.putList("dialogs", NbtType.COMPOUND, rules)
				.putCompound("exit_action", NbtMap.builder().putString("label", "Back").putCompound("action", NbtMap.builder()
						.putString("type", "minecraft:show_dialog").putString("dialog", "example:main").build()).build())
				.build();
		NbtMap tagged = NbtMap.builder().putString("type", "minecraft:dialog_list").putString("title", "Extras")
				.putString("dialogs", "#minecraft:pause_screen_additions").build();
		NbtMap named = NbtMap.builder().putString("type", "minecraft:dialog_list").putString("title", "Named")
				.putList("dialogs", NbtType.STRING, "example:rules").build();

		Dialog menu = shown(Holder.ofCustom(list));

		Dialog nested = menu.getButtons().getFirst().getAction().getDialog();
		assertEquals("Server rules", menu.getButtons().getFirst().getLabel());
		assertEquals("Rules", nested.getTitle());
		assertEquals(new DialogButton("Back", show("example:main"), true), menu.getButtons().get(1));
		assertEquals(List.of(), shown(Holder.ofCustom(tagged)).getButtons());
		assertEquals(List.of(new DialogButton("example:rules", show("example:rules"), false)),
				shown(Holder.ofCustom(named)).getButtons());
	}

	@Test
	void encodesCustomClicksWithTheValuesPutIntoTheirPayload() {
		ServerboundCustomClickActionPacket submit = encode(new ServerboundCustomClickAction("example:submit",
				"{source:\"dialog\",password:\"old\"}", List.of(
				DialogInputValue.text("password", "secret"),
				DialogInputValue.checkbox("remember", true),
				DialogInputValue.checkbox("hidden", false),
				DialogInputValue.slider("amount", 2.5F),
				DialogInputValue.text("mode", "hard")
		)));
		ServerboundCustomClickActionPacket fresh = encode(new ServerboundCustomClickAction("example:fresh", null, List.of(
				DialogInputValue.slider("amount", 1F)
		)));

		assertEquals(Key.key("example:submit"), submit.getId());
		assertEquals(NbtMap.builder()
				.putString("source", "dialog")
				.putString("password", "secret")
				.putByte("remember", (byte) 1)
				.putByte("hidden", (byte) 0)
				.putFloat("amount", 2.5F)
				.putString("mode", "hard")
				.build(), submit.getPayload());
		assertEquals(NbtMap.builder().putFloat("amount", 1F).build(), fresh.getPayload());
	}

	@Test
	void encodesStaticCustomClicksWithTheDeclaredPayloadOrAnEmptyCompound() {
		ServerboundCustomClickActionPacket leave = encode(new ServerboundCustomClickAction("example:leave",
				"{source:\"dialog\",page:2}", List.of()));
		ServerboundCustomClickActionPacket ping = encode(new ServerboundCustomClickAction("example:ping", null, List.of()));

		assertEquals(NbtMap.builder().putString("source", "dialog").putInt("page", 2).build(), leave.getPayload());
		assertEquals(NbtMap.EMPTY, ping.getPayload());
	}

	@Test
	void refusesADeclaredPayloadThatIsNotACompound() {
		assertThrows(IllegalArgumentException.class, () -> packets.encode(new ServerboundCustomClickAction("example:ping", "[1,2]",
				List.of(DialogInputValue.text("mode", "hard")))));
		assertThrows(IllegalArgumentException.class, () -> packets.encode(new ServerboundCustomClickAction("example:ping", "[1,2]",
				List.of())));
	}

	@Test
	void leavesPacketsOfOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ClientboundKeepAlivePacket(7)));
		assertNull(packets.encode(new UnknownPacket()));
	}

	@Test
	void carriesEveryDialogPacket() {
		assertEquals(Set.of(
				ClientboundShowDialog.class,
				ClientboundClearDialog.class,
				ClientboundRegistryData.class,
				ServerboundCustomClickAction.class
		), packets.packets());
	}

	private ServerboundCustomClickActionPacket encode(ServerboundCustomClickAction click) {
		return assertInstanceOf(ServerboundCustomClickActionPacket.class, packets.encode(click));
	}

	private Dialog shown(Holder<NbtMap> dialog) {
		ClientboundShowDialog shown = assertInstanceOf(ClientboundShowDialog.class,
				packets.decode(new ClientboundShowDialogGamePacket(dialog)));

		assertNull(shown.getRegistryId());
		return shown.getDialog();
	}

	private static DialogAction action(DialogActionType type, String value, String payload) {
		return new DialogAction(type, value, payload, null);
	}

	private static DialogAction show(String registryName) {
		return new DialogAction(DialogActionType.SHOW_DIALOG, registryName, null, null);
	}

	private static final class UnknownPacket implements ServerboundPacket {
	}
}
