package me.whereareiam.anvil.tooling.builtin;

import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.tooling.extension.api.ToolingExtension;
import me.whereareiam.anvil.tooling.extension.api.ToolingRegistration;
import org.jetbrains.annotations.NotNull;

/**
 * Portable controls supplied by built-in capabilities; discovered through the public tooling SPI.
 */
public final class BuiltinToolingExtension implements ToolingExtension {
	@Override
	public void register(@NotNull ToolingRegistration registration) {
		registration.action(new PlayerMessageAction("anvil.messages.command", "Player command", "Command", Messages::command));
		registration.action(new PlayerMessageAction("anvil.messages.chat", "Chat message", "Message", Messages::chat));
		registration.observation(new PlayerConnectionObservation());
	}
}
