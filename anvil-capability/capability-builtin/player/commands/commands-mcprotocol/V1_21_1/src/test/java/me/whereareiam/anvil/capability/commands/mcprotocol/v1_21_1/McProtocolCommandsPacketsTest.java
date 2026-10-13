package me.whereareiam.anvil.capability.commands.mcprotocol.v1_21_1;

import me.whereareiam.anvil.capability.commands.model.CommandTree;
import me.whereareiam.anvil.capability.commands.model.CommandTreeNode;
import me.whereareiam.anvil.capability.commands.packet.CommandsPackets;
import me.whereareiam.anvil.capability.commands.type.CommandNodeType;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.SessionListener;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.command.CommandNode;
import org.geysermc.mcprotocollib.protocol.data.game.command.CommandParser;
import org.geysermc.mcprotocollib.protocol.data.game.command.CommandType;
import org.geysermc.mcprotocollib.protocol.data.game.command.properties.IntegerProperties;
import org.geysermc.mcprotocollib.protocol.data.game.command.properties.StringProperties;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundCommandsPacket;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;

class McProtocolCommandsPacketsTest {
	private final McProtocolCommandsPackets packets = new McProtocolCommandsPackets();

	@Test
	void readsTheCommandTreeWithVanillaParserIds() {
		NativeSession session = new NativeSession();
		List<CommandTree> trees = new ArrayList<>();
		packets.listen(session.proxy, trees::add);

		session.receive(new ClientboundCommandsPacket(new CommandNode[]{
				new CommandNode(CommandType.ROOT, false, new int[]{1}, OptionalInt.empty(), null, null, null, null),
				new CommandNode(CommandType.LITERAL, false, new int[]{2}, OptionalInt.empty(), "give", null, null, null),
				new CommandNode(CommandType.ARGUMENT, true, new int[0], OptionalInt.of(0), "amount", CommandParser.INTEGER,
						new IntegerProperties(1, 64), null),
				new CommandNode(CommandType.ARGUMENT, true, new int[0], OptionalInt.empty(), "text", CommandParser.STRING,
						StringProperties.GREEDY_PHRASE, null),
				new CommandNode(CommandType.ARGUMENT, true, new int[0], OptionalInt.empty(), "message", CommandParser.MESSAGE, null, null)
		}, 0));

		CommandTree tree = trees.getFirst();
		assertEquals(0, tree.getRoot());
		assertEquals(CommandTreeNode.builder().type(CommandNodeType.LITERAL).name("give").child(2).restricted(false).parser("").build(),
				tree.getNodes().get(1));
		assertEquals(CommandTreeNode.builder().type(CommandNodeType.ARGUMENT).name("amount").redirect(0).executable(true)
				.parser("brigadier:integer").min(1D).max(64D).build(), tree.getNodes().get(2));
		assertEquals("GREEDY_PHRASE", tree.getNodes().get(3).getStringMode());
		assertEquals("minecraft:message", tree.getNodes().get(4).getParser());
		assertEquals(tree.getNodes().get(1), tree.literal("give").orElseThrow());
	}

	@Test
	void reportsNothingWhileDisconnectedAndRemovesItsListenerWhenDetached() {
		NativeSession session = new NativeSession();
		List<CommandTree> trees = new ArrayList<>();
		Runnable detach = packets.listen(session.proxy, trees::add);

		session.connected = false;
		session.receive(new ClientboundCommandsPacket(new CommandNode[0], 0));
		detach.run();

		assertEquals(List.of(), trees);
		assertEquals(List.of(), session.listeners);
	}

	@Test
	void isTheOnlyCommandsPortOfItsRelease() {
		List<Class<?>> ports = ServiceLoader.load(CommandsPackets.class).stream()
				.map(ServiceLoader.Provider::type)
				.<Class<?>>map(type -> type)
				.toList();

		assertEquals(List.of(McProtocolCommandsPackets.class), ports);
		assertEquals(Session.class, packets.sessionType());
	}

	/**
	 * An MCProtocolLib session that records listeners and delivers received packets synchronously.
	 */
	private static final class NativeSession {
		private final List<SessionListener> listeners = new ArrayList<>();
		private final Session proxy = (Session) Proxy.newProxyInstance(Session.class.getClassLoader(), new Class<?>[]{Session.class},
				(instance, method, arguments) -> handle(method, arguments));
		private boolean connected = true;

		private void receive(Packet packet) {
			List.copyOf(listeners).forEach(listener -> listener.packetReceived(proxy, packet));
		}

		private Object handle(Method method, Object[] arguments) {
			switch (method.getName()) {
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
