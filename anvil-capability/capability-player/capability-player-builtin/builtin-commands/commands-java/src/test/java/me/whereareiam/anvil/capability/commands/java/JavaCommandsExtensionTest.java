package me.whereareiam.anvil.capability.commands.java;

import me.whereareiam.anvil.capability.commands.channel.CommandsChannel;
import me.whereareiam.anvil.capability.commands.model.CommandTree;
import me.whereareiam.anvil.capability.commands.model.CommandTreeNode;
import me.whereareiam.anvil.capability.commands.type.CommandNodeType;
import me.whereareiam.anvil.edition.java.protocol.model.command.CommandNode;
import me.whereareiam.anvil.edition.java.protocol.packet.command.ClientboundCommands;
import me.whereareiam.anvil.edition.java.protocol.type.chat.StringArgumentMode;
import me.whereareiam.anvil.edition.java.protocol.worker.TestJavaPlayer;
import me.whereareiam.anvil.player.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.player.api.worker.WorkerBinding;
import me.whereareiam.anvil.player.api.worker.WorkerExtension;
import me.whereareiam.anvil.player.worker.TestOperations;
import me.whereareiam.anvil.player.worker.TestWorkerPlayer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JavaCommandsExtensionTest {
	private static final ClientboundCommands EMPTY = new ClientboundCommands(List.of(), 0);

	private final JavaCommandsExtension extension = new JavaCommandsExtension();
	private final TestJavaPlayer player = new TestJavaPlayer("Alice");
	private final TestOperations operations = new TestOperations();

	@Test
	void isDiscoveredAsTheEditionWideCommandsExtension() {
		WorkerExtension<?> discovered = ServiceLoader.load(WorkerExtension.class).stream()
				.map(ServiceLoader.Provider::get)
				.filter(JavaCommandsExtension.class::isInstance)
				.findFirst()
				.orElseThrow();

		assertEquals("me.whereareiam.anvil.commands", discovered.id());
		assertEquals(Optional.empty(), discovered.libraryId());
		assertEquals(Object.class, discovered.nativeSessionType());
	}

	@Test
	void resetsForEachNewConnectionAndReportsItsTreesWithoutRegisteringOperations() {
		extension.bind(player, operations);

		player.connect();
		player.deliver(EMPTY);
		player.rejoin();
		player.deliver(EMPTY);

		assertEquals(List.of("commands.reset", "commands.tree", "commands.reset", "commands.tree"),
				player.emittedEvents());
		assertEquals(List.of(), operations.registered());
		assertEquals(List.of(), player.sent());
	}

	@Test
	void mapsTheDeclaredNodesToTheCommandTree() {
		extension.bind(player, operations);
		player.connect();

		player.deliver(new ClientboundCommands(List.of(
				CommandNode.builder().type(me.whereareiam.anvil.edition.java.protocol.type.chat.CommandNodeType.ROOT).child(1).build(),
				CommandNode.builder()
						.type(me.whereareiam.anvil.edition.java.protocol.type.chat.CommandNodeType.LITERAL)
						.name("give")
						.child(2)
						.child(3)
						.restricted(true)
						.build(),
				CommandNode.builder()
						.type(me.whereareiam.anvil.edition.java.protocol.type.chat.CommandNodeType.ARGUMENT)
						.name("amount")
						.redirect(0)
						.executable(true)
						.parser("brigadier:integer")
						.min(1D)
						.max(64D)
						.suggestions("minecraft:ask_server")
						.build(),
				CommandNode.builder()
						.type(me.whereareiam.anvil.edition.java.protocol.type.chat.CommandNodeType.ARGUMENT)
						.name("text")
						.executable(true)
						.parser("brigadier:string")
						.stringMode(StringArgumentMode.GREEDY_PHRASE)
						.build()
		), 0));

		CommandTree tree = player.emitted(CommandsChannel.TREE).getLast();
		assertEquals(0, tree.getRoot());
		assertEquals(CommandTreeNode.builder().type(CommandNodeType.ROOT).child(1).build(), tree.getNodes().get(0));
		assertEquals(CommandTreeNode.builder().type(CommandNodeType.LITERAL).name("give").child(2).child(3)
				.restricted(true)
				.build(), tree.getNodes().get(1));
		assertEquals(CommandTreeNode.builder().type(CommandNodeType.ARGUMENT).name("amount").redirect(0).executable(true)
				.parser("brigadier:integer").min(1D).max(64D).build(), tree.getNodes().get(2));
		assertEquals(CommandTreeNode.builder().type(CommandNodeType.ARGUMENT).name("text").executable(true)
				.parser("brigadier:string")
				.stringMode(me.whereareiam.anvil.capability.commands.type.StringArgumentMode.GREEDY_PHRASE).build(),
						tree.getNodes().get(3));
		assertEquals(tree.getNodes().get(1), tree.literal("give").orElseThrow());
	}

	@Test
	void stopsListeningAndObservingConnectionsWhenClosed() {
		WorkerBinding binding = extension.bind(player, operations);

		binding.close();
		player.connect();
		player.deliver(EMPTY);

		assertFalse(player.protocol().wants(ClientboundCommands.class));
		assertEquals(List.of(), player.emittedEvents());
	}

	@Test
	void isUnavailableForAPlayerThatDoesNotSpeakTheJavaProtocol() {
		TestWorkerPlayer silent = new TestWorkerPlayer("Alice");

		assertThrows(ProtocolUnavailableException.class, () -> extension.bind(silent, operations));
		silent.connect();

		assertEquals(List.of(), silent.emittedEvents());
		assertEquals(List.of(), operations.registered());
	}
}
