package me.whereareiam.anvil.protocol.mcprotocol.client.v1_19_4;

import com.github.steveice10.mc.protocol.data.game.command.CommandNode;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2.CommandTranslation;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.OptionalInt;

/**
 * Commands of the MCProtocolLib 1.19.4 release, which keeps a node's redirect as an optional index and names a node's
 * suggestions by their key.
 */
public final class CommandPackets extends CommandTranslation {
	@Override
	protected @Nullable Integer redirect(@NotNull CommandNode node) {
		OptionalInt redirect = node.getRedirectIndex();
		return redirect.isPresent() ? redirect.getAsInt() : null;
	}

	@Override
	protected @Nullable String suggestions(@NotNull CommandNode node) {
		String suggestions = node.getSuggestionType();
		return suggestions == null ? null : Key.key(suggestions).asString();
	}
}
