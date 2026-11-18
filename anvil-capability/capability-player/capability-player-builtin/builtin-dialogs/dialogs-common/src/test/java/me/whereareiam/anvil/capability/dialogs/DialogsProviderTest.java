package me.whereareiam.anvil.capability.dialogs;

import me.whereareiam.anvil.capability.commands.Commands;
import me.whereareiam.anvil.capability.commands.model.CommandTree;
import me.whereareiam.anvil.capability.commands.type.CommandCheck;
import me.whereareiam.anvil.capability.dialogs.channel.DialogsChannel;
import me.whereareiam.anvil.capability.protocol.api.player.TestProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.edition.java.protocol.JavaProtocol;
import me.whereareiam.anvil.player.api.channel.TestProtocolChannel;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogsProviderTest {
	@Test
	void describesTheDialogsCapabilityForTheLibrariesWithAWorkerSide() {
		DialogsProvider provider = new DialogsProvider();

		assertEquals("me.whereareiam.anvil.dialogs", DialogsChannel.ID);
		assertEquals(DialogsChannel.ID, provider.descriptor().getId());
		assertTrue(provider.descriptor().getRequiredCapabilities().isEmpty());
		assertEquals(Set.of(Commands.class), provider.descriptor().getOptionalCapabilities());
		assertEquals(Set.of(JavaProtocol.class), provider.requiredProtocols());
		assertEquals(Dialogs.class, provider.capability());
	}

	@Test
	void observesShownAndClearedDialogsWithoutRequestingOnCreation() {
		TestProtocolChannel channel = new TestProtocolChannel(Set.of(DialogsChannel.ID));
		Dialogs dialogs = new DialogsProvider().create(new TestProtocolPlayerCapabilityContext(channel)
				.withCapability(Commands.class, COMMANDS));

		assertEquals(List.of(DialogsChannel.REGISTRY.getId(), DialogsChannel.SHOWN.getId(), DialogsChannel.CLEARED.getId(),
				DialogsChannel.RESET.getId()), channel.subscriptions());
		assertTrue(channel.requests().isEmpty());
		assertTrue(dialogs.current().isEmpty());
	}

	private static final Commands COMMANDS = new Commands() {
		@Override
		public @NotNull Optional<CommandTree> tree() {
			return Optional.empty();
		}

		@Override
		public @NotNull CommandTree tree(@NotNull Predicate<CommandTree> matcher, @NotNull Duration timeout) {
			throw new AssertionError("Creating dialogs does not wait");
		}

		@Override
		public @NotNull CommandCheck check(@NotNull String command) {
			throw new AssertionError("Creating dialogs checks no command");
		}
	};
}
