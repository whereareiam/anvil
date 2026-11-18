package me.whereareiam.anvil.capability.dialogs.java;

import me.whereareiam.anvil.capability.dialogs.channel.DialogsChannel;
import me.whereareiam.anvil.capability.dialogs.model.DialogRegistry;
import me.whereareiam.anvil.capability.dialogs.model.click.DialogClick;
import me.whereareiam.anvil.capability.dialogs.model.click.DialogValue;
import me.whereareiam.anvil.capability.dialogs.model.dialog.Dialog;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogAction;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogButton;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogInput;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogOption;
import me.whereareiam.anvil.capability.dialogs.model.dialog.RegistryDialog;
import me.whereareiam.anvil.capability.dialogs.type.DialogActionType;
import me.whereareiam.anvil.capability.dialogs.type.DialogAfterAction;
import me.whereareiam.anvil.capability.dialogs.type.DialogInputType;
import me.whereareiam.anvil.edition.java.protocol.model.configuration.RegistryEntry;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.model.nbt.NbtByte;
import me.whereareiam.anvil.edition.java.protocol.model.nbt.NbtCompound;
import me.whereareiam.anvil.edition.java.protocol.model.nbt.NbtFloat;
import me.whereareiam.anvil.edition.java.protocol.model.nbt.NbtInt;
import me.whereareiam.anvil.edition.java.protocol.model.nbt.NbtList;
import me.whereareiam.anvil.edition.java.protocol.model.nbt.NbtString;
import me.whereareiam.anvil.edition.java.protocol.model.nbt.NbtTag;
import me.whereareiam.anvil.edition.java.protocol.model.world.DimensionType;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.chat.ServerboundChatCommand;
import me.whereareiam.anvil.edition.java.protocol.packet.configuration.ClientboundRegistryData;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundStartConfiguration;
import me.whereareiam.anvil.edition.java.protocol.packet.dialog.ClientboundClearDialog;
import me.whereareiam.anvil.edition.java.protocol.packet.dialog.ClientboundShowDialog;
import me.whereareiam.anvil.edition.java.protocol.packet.dialog.ServerboundCustomClickAction;
import me.whereareiam.anvil.edition.java.protocol.worker.TestJavaPlayer;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import me.whereareiam.anvil.protocol.worker.TestOperations;
import me.whereareiam.anvil.protocol.worker.TestWorkerPlayer;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaDialogsExtensionTest {
	private static final NbtCompound NOTICE = NbtCompound.builder()
			.put("type", new NbtString("minecraft:notice"))
			.put("title", new NbtString("Log in"))
			.build();
	private static final Dialog LOGIN = Dialog.builder()
			.type("minecraft:notice")
			.title("Log in")
			.externalTitle("Log in")
			.button(new DialogButton("gui.ok", null, true))
			.closeWithEscape(true)
			.afterAction(DialogAfterAction.CLOSE)
			.build();
	private static final List<Class<? extends ClientboundPacket>> LISTENED = List.of(
			ClientboundShowDialog.class,
			ClientboundClearDialog.class,
			ClientboundRegistryData.class,
			ClientboundLogin.class,
			ClientboundRespawn.class,
			ClientboundStartConfiguration.class
	);

	private final JavaDialogsExtension extension = new JavaDialogsExtension();
	private final TestJavaPlayer player = new TestJavaPlayer("Alice");
	private final TestOperations operations = new TestOperations();

	@Test
	void isDiscoveredAsTheEditionWideDialogsExtension() {
		WorkerExtension<?> discovered = ServiceLoader.load(WorkerExtension.class).stream()
				.map(ServiceLoader.Provider::get)
				.filter(JavaDialogsExtension.class::isInstance)
				.findFirst()
				.orElseThrow();

		assertEquals("me.whereareiam.anvil.dialogs", discovered.id());
		assertEquals(Optional.empty(), discovered.libraryId());
		assertEquals(Object.class, discovered.nativeSessionType());
	}

	@Test
	void sendsCommandsAsUnsignedCommandsAndCustomClicksWithTheDeclaredIdAndPayload() {
		extension.bind(player, operations);
		player.connect();
		player.deliver(new ClientboundShowDialog(null, actions(
				custom("minecraft:custom", "example:leave", "payload", NbtCompound.EMPTY.with("source", new NbtString("dialog"))),
				custom("minecraft:custom", "example:ping", "payload", null)
		), false));

		assertNull(operations.call(DialogsChannel.CLICK, DialogClick.command("login secret")));
		assertNull(operations.call(DialogsChannel.CLICK, DialogClick.custom(handle(player, 0), List.of())));
		assertNull(operations.call(DialogsChannel.CLICK, DialogClick.custom(handle(player, 1), List.of())));

		assertEquals(List.of("dialogs.click"), operations.registered());
		assertEquals(List.of(
				new ServerboundChatCommand("login secret"),
				new ServerboundCustomClickAction("example:leave", NbtCompound.EMPTY.with("source", new NbtString("dialog"))),
				new ServerboundCustomClickAction("example:ping", NbtCompound.EMPTY)
		), player.sent());
	}

	@Test
	void sendsADynamicCustomClickWithTheValuesPutIntoTheDeclaredAdditions() {
		extension.bind(player, operations);
		player.connect();
		player.deliver(new ClientboundShowDialog(null, actions(custom("minecraft:dynamic/custom", "example:submit", "additions",
				NbtCompound.builder().put("source", new NbtString("dialog")).put("password", new NbtString("old")).build())), false));

		operations.call(DialogsChannel.CLICK, DialogClick.custom(handle(player, 0), List.of(
				new DialogValue("password", DialogInputType.TEXT, "secret"),
				new DialogValue("remember", DialogInputType.BOOLEAN, "true"),
				new DialogValue("hidden", DialogInputType.BOOLEAN, "false"),
				new DialogValue("amount", DialogInputType.NUMBER_RANGE, "2.5"),
				new DialogValue("mode", DialogInputType.SINGLE_OPTION, "hard")
		)));

		assertEquals(List.of(new ServerboundCustomClickAction("example:submit", NbtCompound.builder()
				.put("source", new NbtString("dialog"))
				.put("password", new NbtString("secret"))
				.put("remember", NbtByte.of(true))
				.put("hidden", NbtByte.of(false))
				.put("amount", new NbtFloat(2.5F))
				.put("mode", new NbtString("hard"))
				.build())), player.sent());
	}

	@Test
	void refusesACustomClickWhoseDeclaredPayloadIsNotACompound() {
		extension.bind(player, operations);
		player.connect();
		player.deliver(new ClientboundShowDialog(null, actions(custom("minecraft:custom", "example:ping", "payload",
				new NbtList(List.of(new NbtInt(1), new NbtInt(2))))), false));

		assertThrows(IllegalArgumentException.class,
				() -> operations.call(DialogsChannel.CLICK, DialogClick.custom(handle(player, 0), List.of())));
		assertEquals(List.of(), player.sent());
	}

	@Test
	void refusesAClickNamingAnActionOfADialogThatIsGone() {
		extension.bind(player, operations);
		player.connect();
		List<ClientboundPacket> endings = List.of(
				new ClientboundShowDialog(null, NOTICE, false),
				new ClientboundClearDialog(),
				login(42),
				respawn(),
				new ClientboundStartConfiguration()
		);

		for (ClientboundPacket ending : endings) {
			player.deliver(new ClientboundShowDialog(null, actions(custom("minecraft:custom", "example:ping", "payload", null)), false));
			long handle = handle(player, 0);
			player.deliver(ending);

			IllegalStateException failure = assertThrows(IllegalStateException.class,
					() -> operations.call(DialogsChannel.CLICK, DialogClick.custom(handle, List.of())));
			assertTrue(failure.getMessage().contains("no longer shown"), failure.getMessage());
		}

		player.deliver(new ClientboundShowDialog(null, actions(custom("minecraft:custom", "example:ping", "payload", null)), false));
		long handle = handle(player, 0);
		player.rejoin();

		assertThrows(IllegalStateException.class, () -> operations.call(DialogsChannel.CLICK, DialogClick.custom(handle, List.of())));
		assertEquals(List.of(), player.sent());
	}

	@Test
	void keepsTheActionsOfTheDialogRegistryUntilANewRegistryReplacesIt() {
		extension.bind(player, operations);
		player.connect();
		NbtCompound rules = actions(custom("minecraft:custom", "example:agree", "payload", null));

		player.deliver(new ClientboundRegistryData("minecraft:dialog", List.of(new RegistryEntry("example:rules", rules))));
		DialogRegistry registry = player.emitted(DialogsChannel.REGISTRY).getLast();
		long handle = registry.getDialogs().getFirst().getDialog().getButtons().getFirst().getAction().getHandle();
		player.deliver(new ClientboundShowDialog(0, null, false));
		player.deliver(new ClientboundClearDialog());
		operations.call(DialogsChannel.CLICK, DialogClick.custom(handle, List.of()));
		player.deliver(new ClientboundRegistryData("minecraft:dialog", List.of()));

		assertEquals(List.of(new ServerboundCustomClickAction("example:agree", NbtCompound.EMPTY)), player.sent());
		assertThrows(IllegalStateException.class, () -> operations.call(DialogsChannel.CLICK, DialogClick.custom(handle, List.of())));
	}

	@Test
	void sendsNothingWhileThePlayerIsDisconnected() {
		extension.bind(player, operations);

		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> operations.call(DialogsChannel.CLICK, DialogClick.command("login secret")));

		assertTrue(failure.getMessage().contains("not connected"), failure.getMessage());
		assertEquals(List.of(), player.sent());
	}

	@Test
	void resetsTheScreensForEachNewConnectionAndWheneverTheClientReplacesThem() {
		extension.bind(player, operations);

		player.connect();
		player.deliver(new ClientboundShowDialog(null, NOTICE, false));
		player.deliver(new ClientboundClearDialog());
		player.deliver(login(42));
		player.deliver(respawn());
		player.deliver(new ClientboundStartConfiguration());
		player.rejoin();

		assertEquals(List.of("dialogs.reset", "dialogs.shown", "dialogs.cleared", "dialogs.reset", "dialogs.reset", "dialogs.reset",
				"dialogs.reset"), player.emittedEvents());
		assertEquals(List.of(LOGIN), player.emitted(DialogsChannel.SHOWN));
	}

	@Test
	void mapsShownDialogsWithTheirInputsButtonsAndNestedDialogs() {
		extension.bind(player, operations);
		player.connect();


		player.deliver(new ClientboundShowDialog(null, NbtCompound.builder()
				.put("type", new NbtString("minecraft:multi_action"))
				.put("title", new NbtString("Identica"))
				.put("external_title", new NbtString("Log in"))
				.put("body", NbtCompound.builder()
						.put("type", new NbtString("minecraft:plain_message"))
						.put("contents", new NbtString("Welcome back"))
						.build())
				.put("inputs", new NbtList(List.of(
						NbtCompound.builder()
								.put("type", new NbtString("minecraft:text"))
								.put("key", new NbtString("password"))
								.put("label", new NbtString("Password"))
								.put("max_length", new NbtInt(64))
								.build(),
						NbtCompound.builder()
								.put("type", new NbtString("minecraft:boolean"))
								.put("key", new NbtString("remember"))
								.put("label", new NbtString("Remember"))
								.put("initial", NbtByte.of(true))
								.put("on_true", new NbtString("yes"))
								.build(),
						NbtCompound.builder()
								.put("type", new NbtString("minecraft:single_option"))
								.put("key", new NbtString("mode"))
								.put("label", new NbtString("Mode"))
								.put("options", new NbtList(List.of(NbtCompound.builder()
										.put("id", new NbtString("hard"))
										.put("display", new NbtString("Hard"))
										.put("initial", NbtByte.of(true))
										.build())))
								.build(),
						NbtCompound.builder()
								.put("type", new NbtString("minecraft:number_range"))
								.put("key", new NbtString("amount"))
								.put("label", new NbtString("Amount"))
								.put("start", new NbtFloat(1F))
								.put("end", new NbtFloat(4F))
								.put("step", new NbtFloat(0.5F))
								.build(),
						NbtCompound.builder()
								.put("type", new NbtString("example:colour"))
								.put("key", new NbtString("colour"))
								.build()
				)))
				.put("actions", new NbtList(List.of(
						button("Help", NbtCompound.builder()
								.put("type", new NbtString("minecraft:dynamic/custom"))
								.put("id", new NbtString("example:help"))
								.put("additions", NbtCompound.EMPTY.with("page", new NbtInt(2)))
								.build()),
						button("Rules", NbtCompound.builder()
								.put("type", new NbtString("minecraft:show_dialog"))
								.put("dialog", NOTICE)
								.build())
				)))
				.put("exit_action", button("Leave", NbtCompound.builder()
						.put("type", new NbtString("minecraft:run_command"))
						.put("command", new NbtString("/hub"))
						.build()))
				.put("can_close_with_escape", NbtByte.of(false))
				.put("after_action", new NbtString("wait_for_response"))
				.build(), false));

		assertEquals(Dialog.builder()
				.type("minecraft:multi_action")
				.title("Identica")
				.externalTitle("Log in")
				.body("Welcome back")
				.input(DialogInput.builder().key("password").type(DialogInputType.TEXT).label("Password").initial("").maxLength(64).build())
				.input(DialogInput.builder().key("remember").type(DialogInputType.BOOLEAN).label("Remember").initial("true")
						.onTrue("yes").onFalse("false").build())
				.input(DialogInput.builder().key("mode").type(DialogInputType.SINGLE_OPTION).label("Mode").initial("hard")
						.option(new DialogOption("hard", "Hard", true)).build())
				.input(DialogInput.builder().key("amount").type(DialogInputType.NUMBER_RANGE).label("Amount").initial("2.5")
						.start(1F).end(4F).step(0.5F).build())
				.input(DialogInput.builder().key("colour").type(DialogInputType.OTHER).label("").initial("").build())
				.button(new DialogButton("Help", DialogAction.custom(DialogActionType.DYNAMIC_CUSTOM, "example:help", 1), false))
				.button(new DialogButton("Rules", DialogAction.show("", LOGIN), false))
				.button(new DialogButton("Leave", DialogAction.of(DialogActionType.RUN_COMMAND, "/hub"), true))
				.closeWithEscape(false)
				.afterAction(DialogAfterAction.WAIT_FOR_RESPONSE)
				.build(), player.emitted(DialogsChannel.SHOWN).getLast());
	}

	@Test
	void reportsRegistryReferencesAndConfigurationDialogs() {
		extension.bind(player, operations);
		player.connect();

		player.deliver(new ClientboundShowDialog(3, null, false));
		player.deliver(new ClientboundShowDialog(null, NOTICE, true));

		assertEquals(List.of(
				Dialog.builder()
						.type("")
						.registryId(3)
						.title("")
						.externalTitle("")
						.closeWithEscape(true)
						.afterAction(DialogAfterAction.CLOSE)
						.build(),
				LOGIN.toBuilder().configuration(true).build()
		), player.emitted(DialogsChannel.SHOWN));
	}

	@Test
	void reportsOnlyTheDialogRegistryInItsOrder() {
		extension.bind(player, operations);
		player.connect();

		player.deliver(new ClientboundRegistryData("minecraft:dimension_type", List.of(
				new RegistryEntry("minecraft:overworld", NbtCompound.EMPTY.with("height", new NbtInt(384)))
		)));
		player.deliver(new ClientboundRegistryData("minecraft:dialog", List.of(
				new RegistryEntry("example:rules", NOTICE),
				new RegistryEntry("example:menu", null)
		)));

		assertEquals(List.of("dialogs.reset", "dialogs.registry"), player.emittedEvents());
		assertEquals(List.of(new DialogRegistry(List.of(
				new RegistryDialog("example:rules", LOGIN),
				new RegistryDialog("example:menu", null)
		))), player.emitted(DialogsChannel.REGISTRY));
	}

	@Test
	void takesTheBuiltInDialogsTheServerSendsWithoutDataFromTheClientsOwnDataPack() {
		extension.bind(player, operations);
		player.connect();

		player.deliver(new ClientboundRegistryData("minecraft:dialog", List.of(
				new RegistryEntry("minecraft:server_links", null),
				new RegistryEntry("minecraft:custom_options", null),
				new RegistryEntry("minecraft:quick_actions", null)
		)));

		List<RegistryDialog> dialogs = player.emitted(DialogsChannel.REGISTRY).getFirst().getDialogs();
		assertEquals(List.of("minecraft:server_links", "minecraft:dialog_list", "minecraft:dialog_list"),
				dialogs.stream().map(registered -> registered.getDialog().getType()).toList());
		assertEquals(List.of("menu.server_links.title", "menu.custom_options.title", "menu.quick_actions.title"),
				dialogs.stream().map(registered -> registered.getDialog().getTitle()).toList());
		assertEquals(List.of("menu.server_links", "menu.custom_options", "menu.quick_actions"),
				dialogs.stream().map(registered -> registered.getDialog().getExternalTitle()).toList());
		assertEquals(List.of("gui.back"), dialogs.getFirst().getDialog().getButtons().stream()
				.map(DialogButton::getLabel)
				.toList());
	}

	@Test
	void stopsListeningAndObservingConnectionsWhenClosed() {
		WorkerBinding binding = extension.bind(player, operations);
		for (Class<? extends ClientboundPacket> type : LISTENED)
			assertTrue(player.protocol().wants(type), type.getName());

		binding.close();
		player.connect();
		player.deliver(new ClientboundClearDialog());

		for (Class<? extends ClientboundPacket> type : LISTENED)
			assertFalse(player.protocol().wants(type), type.getName());
		assertEquals(List.of(), player.emittedEvents());
	}

	@Test
	void isUnavailableForAReleaseWithoutDialogs() {
		TestJavaPlayer older = new TestJavaPlayer("Alice", type -> type != ClientboundShowDialog.class);

		ProtocolUnavailableException failure = assertThrows(ProtocolUnavailableException.class,
				() -> extension.bind(older, operations));
		older.connect();

		assertTrue(failure.getMessage().contains(ClientboundShowDialog.class.getName()), failure.getMessage());
		assertEquals(List.of(), operations.registered());
		for (Class<? extends ClientboundPacket> type : LISTENED)
			assertFalse(older.protocol().wants(type), type.getName());
		assertEquals(List.of(), older.emittedEvents());
	}

	@Test
	void isUnavailableForAPlayerThatDoesNotSpeakTheJavaProtocol() {
		TestWorkerPlayer silent = new TestWorkerPlayer("Alice");

		assertThrows(ProtocolUnavailableException.class, () -> extension.bind(silent, operations));
		silent.connect();

		assertEquals(List.of(), operations.registered());
		assertEquals(List.of(), silent.emittedEvents());
	}

	/**
	 * A multi-action dialog with one button per action, labelled by its position.
	 */
	private static NbtCompound actions(NbtCompound... actions) {
		List<NbtCompound> buttons = new ArrayList<>();
		for (NbtCompound action : actions) buttons.add(button(Integer.toString(buttons.size()), action));

		return NbtCompound.builder()
				.put("type", new NbtString("minecraft:multi_action"))
				.put("title", new NbtString("Actions"))
				.put("actions", new NbtList(buttons))
				.build();
	}

	private static NbtCompound custom(String type, String id, String payloadField, @Nullable NbtTag payload) {
		NbtCompound action = NbtCompound.builder()
				.put("type", new NbtString(type))
				.put("id", new NbtString(id))
				.build();
		return payload == null ? action : action.with(payloadField, payload);
	}

	/**
	 * Returns the handle of a button's action in the dialog the player was shown last.
	 */
	private static long handle(TestJavaPlayer player, int button) {
		Dialog shown = player.emitted(DialogsChannel.SHOWN).getLast();
		return Objects.requireNonNull(shown.getButtons().get(button).getAction().getHandle());
	}

	private static NbtCompound button(String label, NbtCompound action) {
		return NbtCompound.builder()
				.put("label", new NbtString(label))
				.put("action", action)
				.build();
	}

	private static ClientboundLogin login(int entityId) {
		return ClientboundLogin.builder()
				.entityId(new EntityId(entityId))
				.level("minecraft:overworld")
				.dimension("minecraft:overworld")
				.dimensionType(DimensionType.known("minecraft:overworld"))
				.build();
	}

	private static ClientboundRespawn respawn() {
		return ClientboundRespawn.builder()
				.dimension("minecraft:overworld")
				.dimensionType(DimensionType.known("minecraft:overworld"))
				.build();
	}
}
