package me.whereareiam.anvil.capability.commands;

import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.commands.model.CommandTree;
import me.whereareiam.anvil.capability.commands.model.CommandTreeNode;
import me.whereareiam.anvil.capability.commands.type.CommandCheck;
import me.whereareiam.anvil.capability.commands.type.CommandNodeType;
import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;
import me.whereareiam.anvil.capability.protocol.api.player.channel.CapabilityChannel;
import me.whereareiam.anvil.capability.protocol.api.player.channel.Subscription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChannelCommandsTest {
	private static final Duration TIMEOUT = Duration.ofSeconds(3);
	private static final CommandTree LOGIN = new CommandTree(List.of(
			CommandTreeNode.builder().type(CommandNodeType.ROOT).name("").parser("").child(1).build(),
			CommandTreeNode.builder().type(CommandNodeType.LITERAL).name("login").parser("").executable(true).build()
	), 0);

	@Test
	void keepsTheLastTreeOfTheCurrentConnection() {
		RecordingChannel channel = new RecordingChannel();
		ChannelCommands commands = new ChannelCommands(channel);
		assertEquals(Optional.empty(), commands.tree());

		channel.publish(CommandsOperations.TREE, LOGIN);
		assertEquals(Optional.of(LOGIN), commands.tree());
		assertEquals(LOGIN, commands.tree(tree -> tree.literal("login").isPresent(), TIMEOUT));

		channel.publish(CommandsOperations.RESET, null);
		assertEquals(Optional.empty(), commands.tree());
		assertEquals("Player did not receive a matching command tree",
				assertThrows(IllegalStateException.class, () -> commands.tree(tree -> true, TIMEOUT)).getMessage());
	}

	@Test
	void checksCommandsWithOrWithoutTheirSlashAgainstTheTree() {
		RecordingChannel channel = new RecordingChannel();
		ChannelCommands commands = new ChannelCommands(channel);
		assertEquals(CommandCheck.PARSE_ERRORS, commands.check("login"));

		channel.publish(CommandsOperations.TREE, LOGIN);

		assertEquals(CommandCheck.NO_ISSUES, commands.check("login"));
		assertEquals(CommandCheck.NO_ISSUES, commands.check("/login"));
		assertEquals(CommandCheck.PARSE_ERRORS, commands.check("logout"));
		assertEquals(List.of(), channel.requests);
	}

	/**
	 * Records requests and delivers published events synchronously; a wait whose condition does not hold fails
	 * immediately instead of timing out.
	 */
	private static final class RecordingChannel implements CapabilityChannel {
		private final List<String> requests = new ArrayList<>();
		private final Map<String, List<Consumer<Object>>> listeners = new HashMap<>();

		private <E> void publish(EventDescriptor<E> eventDescriptor, @Nullable E payload) {
			listeners.getOrDefault(eventDescriptor.getId(), List.of()).forEach(listener -> listener.accept(payload));
		}

		@Override
		public <Q, R> @Nullable R request(@NotNull ChannelOperation<Q, R> channelOperation, @Nullable Q request) {
			requests.add(channelOperation.getId());
			return null;
		}

		@Override
		@SuppressWarnings("unchecked")
		public <E> @NotNull Subscription subscribe(@NotNull EventDescriptor<E> eventDescriptor, @NotNull Consumer<E> listener) {
			listeners.computeIfAbsent(eventDescriptor.getId(), ignored -> new ArrayList<>()).add((Consumer<Object>) listener);
			return () -> listeners.get(eventDescriptor.getId()).remove(listener);
		}

		@Override
		public void await(@NotNull BooleanSupplier condition, @NotNull String description, @NotNull Duration timeout) {
			if (!condition.getAsBoolean()) throw new IllegalStateException("Player did not " + description);
		}

		@Override
		public @NotNull Set<String> installedCapabilities() {
			return Set.of(CommandsProvider.ID);
		}
	}
}
