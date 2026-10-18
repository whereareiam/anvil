package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.command.CommandNode;
import com.github.steveice10.mc.protocol.data.game.command.SuggestionType;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Commands of the MCProtocolLib 1.18.2 release, which marks a node without a redirect with a negative index and names
 * a node's suggestions by a constant, whose key is its lowercase name in the {@code minecraft} namespace.
 */
final class CommandPackets extends CommandTranslation {
	@Override
	protected @Nullable Integer redirect(@NotNull CommandNode node) {
		return node.getRedirectIndex() < 0 ? null : node.getRedirectIndex();
	}

	@Override
	protected @Nullable String suggestions(@NotNull CommandNode node) {
		SuggestionType suggestions = node.getSuggestionType();
		if (suggestions == null) return null;

		return Key.key(Key.MINECRAFT_NAMESPACE, suggestions.name().toLowerCase(Locale.ROOT)).asString();
	}
}
