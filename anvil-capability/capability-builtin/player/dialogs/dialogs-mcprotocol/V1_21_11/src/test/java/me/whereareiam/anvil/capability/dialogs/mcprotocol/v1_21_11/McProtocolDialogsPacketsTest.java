package me.whereareiam.anvil.capability.dialogs.mcprotocol.v1_21_11;

import me.whereareiam.anvil.capability.dialogs.model.click.DialogClick;
import me.whereareiam.anvil.capability.dialogs.model.click.DialogValue;
import me.whereareiam.anvil.capability.dialogs.model.dialog.Dialog;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogAction;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogButton;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogInput;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogOption;
import me.whereareiam.anvil.capability.dialogs.model.dialog.RegistryDialog;
import me.whereareiam.anvil.capability.dialogs.packet.DialogsPacketListener;
import me.whereareiam.anvil.capability.dialogs.packet.DialogsPackets;
import me.whereareiam.anvil.capability.dialogs.type.DialogActionType;
import me.whereareiam.anvil.capability.dialogs.type.DialogAfterAction;
import me.whereareiam.anvil.capability.dialogs.type.DialogInputType;
import net.kyori.adventure.key.Key;
import org.cloudburstmc.nbt.NbtList;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.SessionListener;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.Holder;
import org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundClearDialogPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundCustomClickActionPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundRegistryDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundShowDialogConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundShowDialogGamePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundStartConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class McProtocolDialogsPacketsTest {
	private final McProtocolDialogsPackets packets = new McProtocolDialogsPackets();

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

		Dialog dialog = show(Holder.ofCustom(form));

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
				.button(new DialogButton("Log in", DialogAction.of(DialogActionType.DYNAMIC_RUN_COMMAND, "login $(password)", null), false))
				.button(new DialogButton("Help", DialogAction.of(DialogActionType.DYNAMIC_CUSTOM, "example:help", "{source:\"dialog\",page:2}"), false))
				.button(new DialogButton("Rules", DialogAction.of(DialogActionType.OPEN_URL, "https://example.com/rules", null), false))
				.button(new DialogButton("Leave", DialogAction.of(DialogActionType.RUN_COMMAND, "/hub", null), true))
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

		Dialog shownNotice = show(Holder.ofCustom(notice));
		Dialog shownConfirmation = show(Holder.ofCustom(confirmation));

		assertEquals("menu.paused", shownNotice.getTitle());
		assertEquals(List.of("First", "Shiny"), shownNotice.getBody());
		assertEquals(List.of(new DialogButton("gui.ok", null, true)), shownNotice.getButtons());
		assertEquals(true, shownNotice.isCloseWithEscape());
		assertEquals(DialogAfterAction.CLOSE, shownNotice.getAfterAction());
		assertEquals(List.of(
				new DialogButton("Yes", DialogAction.of(DialogActionType.CUSTOM, "example:leave", null), false),
				new DialogButton("No", null, true)
		), shownConfirmation.getButtons());
	}

	@Test
	void reportsADialogShownByRegistryIdWithItsIdOnly() {
		Dialog dialog = show(Holder.ofId(3));

		assertEquals(3, dialog.getRegistryId());
		assertEquals("", dialog.getType());
		assertEquals(List.of(), dialog.getButtons());
	}

	@Test
	void reportsClearedDialogsConfigurationDialogsAndEveryScreenReset() {
		NativeSession session = new NativeSession();
		List<String> events = new ArrayList<>();
		packets.listen(session.proxy, recording(events));

		session.receive(new ClientboundKeepAlivePacket(7));
		session.receive(new ClientboundShowDialogConfigurationPacket(NbtMap.builder().putString("type", "minecraft:notice")
				.putString("title", "Terms").build()));
		session.receive(new ClientboundClearDialogPacket());
		session.receive(new ClientboundStartConfigurationPacket());
		session.receive(new ClientboundRespawnPacket(null, false, false));

		assertEquals(List.of("shown Terms during configuration", "cleared", "reset", "reset"), events);
	}

	@Test
	void reportsTheDialogRegistryOnly() {
		NativeSession session = new NativeSession();
		List<String> events = new ArrayList<>();
		packets.listen(session.proxy, recording(events));

		session.receive(new ClientboundRegistryDataPacket(Key.key("minecraft:dimension_type"), List.of()));
		session.receive(new ClientboundRegistryDataPacket(Key.key("minecraft:dialog"), List.of(
				new RegistryEntry(Key.key("minecraft:server_links"), null),
				new RegistryEntry(Key.key("example:rules"), NbtMap.builder().putString("type", "minecraft:notice").putString("title", "Rules").build())
		)));

		assertEquals(List.of("registry [minecraft:server_links, example:rules]"), events);
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

		Dialog menu = show(Holder.ofCustom(list));

		Dialog nested = menu.getButtons().getFirst().getAction().getDialog();
		assertEquals("Server rules", menu.getButtons().getFirst().getLabel());
		assertEquals("Rules", nested.getTitle());
		assertEquals(new DialogButton("Back", DialogAction.show("example:main", null), true), menu.getButtons().get(1));
		assertEquals(List.of(), show(Holder.ofCustom(tagged)).getButtons());
		assertEquals(List.of(new DialogButton("example:rules", DialogAction.show("example:rules", null), false)),
				show(Holder.ofCustom(named)).getButtons());
	}

	@Test
	void reportsNothingWhileDisconnectedAndRemovesItsListenerWhenDetached() {
		NativeSession session = new NativeSession();
		List<String> events = new ArrayList<>();
		Runnable detach = packets.listen(session.proxy, recording(events));

		session.connected = false;
		session.receive(new ClientboundClearDialogPacket());
		detach.run();

		assertEquals(List.of(), events);
		assertEquals(List.of(), session.listeners);
	}

	@Test
	void sendsCommandsThroughTheUnsignedCommandPacket() {
		NativeSession session = new NativeSession();

		packets.click(session.proxy, DialogClick.command("login secret"));

		assertEquals("login secret", assertInstanceOf(ServerboundChatCommandPacket.class, session.sent.getFirst()).getCommand());
	}

	@Test
	void sendsCustomClicksWithTheValuesPutIntoTheirPayload() {
		NativeSession session = new NativeSession();

		packets.click(session.proxy, DialogClick.custom("example:submit", "{source:\"dialog\",password:\"old\"}", List.of(
				new DialogValue("password", DialogInputType.TEXT, "secret"),
				new DialogValue("remember", DialogInputType.BOOLEAN, "true"),
				new DialogValue("amount", DialogInputType.NUMBER_RANGE, "2.5"),
				new DialogValue("mode", DialogInputType.SINGLE_OPTION, "hard")
		)));
		packets.click(session.proxy, DialogClick.custom("example:ping", null, List.of()));

		ServerboundCustomClickActionPacket submit = assertInstanceOf(ServerboundCustomClickActionPacket.class, session.sent.get(0));
		assertEquals(Key.key("example:submit"), submit.getId());
		assertEquals(NbtMap.builder()
				.putString("source", "dialog")
				.putString("password", "secret")
				.putBoolean("remember", true)
				.putFloat("amount", 2.5F)
				.putString("mode", "hard")
				.build(), submit.getPayload());
		assertEquals(NbtMap.EMPTY, assertInstanceOf(ServerboundCustomClickActionPacket.class, session.sent.get(1)).getPayload());
	}

	@Test
	void refusesAPayloadThatIsNotACompound() {
		NativeSession session = new NativeSession();

		assertThrows(IllegalArgumentException.class, () -> packets.click(session.proxy, DialogClick.custom("example:ping", "[1,2]", List.of())));
		assertEquals(List.of(), session.sent);
	}

	@Test
	void isTheOnlyDialogsPortOfItsRelease() {
		List<Class<?>> ports = ServiceLoader.load(DialogsPackets.class).stream()
				.map(ServiceLoader.Provider::type)
				.<Class<?>>map(type -> type)
				.toList();

		assertEquals(List.of(McProtocolDialogsPackets.class), ports);
		assertEquals(Session.class, packets.sessionType());
	}

	private Dialog show(Holder<NbtMap> dialog) {
		NativeSession session = new NativeSession();
		List<Dialog> shown = new ArrayList<>();
		packets.listen(session.proxy, new Recording() {
			@Override
			public void shown(@NotNull Dialog value) {
				shown.add(value);
			}
		});

		session.receive(new ClientboundShowDialogGamePacket(dialog));
		assertEquals(1, shown.size());
		return shown.getFirst();
	}

	private static DialogsPacketListener recording(List<String> events) {
		return new Recording() {
			@Override
			public void shown(@NotNull Dialog dialog) {
				events.add("shown " + dialog.getTitle() + (dialog.isConfiguration() ? " during configuration" : ""));
			}

			@Override
			public void cleared() {
				events.add("cleared");
			}

			@Override
			public void reset() {
				events.add("reset");
			}

			@Override
			public void registry(@NotNull List<RegistryDialog> dialogs) {
				events.add("registry " + dialogs.stream().map(RegistryDialog::getName).toList());
			}
		};
	}

	/**
	 * A listener that fails on every report a test does not expect.
	 */
	private static class Recording implements DialogsPacketListener {
		@Override
		public void shown(@NotNull Dialog dialog) {
			throw new AssertionError("No dialog is shown");
		}

		@Override
		public void cleared() {
			throw new AssertionError("Nothing is cleared");
		}

		@Override
		public void reset() {
			throw new AssertionError("Nothing is reset");
		}

		@Override
		public void registry(@NotNull List<RegistryDialog> dialogs) {
			throw new AssertionError("No registry is sent");
		}

	}

	@SafeVarargs
	private static <T> NbtList<T> list(NbtType<T> type, T... elements) {
		return new NbtList<>(type, elements);
	}

	/**
	 * An MCProtocolLib session that records sent packets and listeners and delivers received packets synchronously.
	 */
	private static final class NativeSession {
		private final List<Packet> sent = new ArrayList<>();
		private final List<SessionListener> listeners = new ArrayList<>();
		private final Session proxy = (Session) Proxy.newProxyInstance(Session.class.getClassLoader(), new Class<?>[]{Session.class},
				(instance, method, arguments) -> handle(method, arguments));
		private boolean connected = true;

		private void receive(Packet packet) {
			List.copyOf(listeners).forEach(listener -> listener.packetReceived(proxy, packet));
		}

		private Object handle(Method method, Object[] arguments) {
			switch (method.getName()) {
				case "send" -> sent.add((Packet) arguments[0]);
				case "addListener" -> listeners.add((SessionListener) arguments[0]);
				case "removeListener" -> listeners.remove((SessionListener) arguments[0]);
				case "isConnected" -> {
					return connected;
				}
				default -> throw new UnsupportedOperationException(method.getName());
			}
			return null;
		}
	}
}
