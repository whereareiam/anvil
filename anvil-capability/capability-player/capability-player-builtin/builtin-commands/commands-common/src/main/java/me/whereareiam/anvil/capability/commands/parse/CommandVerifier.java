package me.whereareiam.anvil.capability.commands.parse;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.commands.model.CommandTree;
import me.whereareiam.anvil.capability.commands.model.CommandTreeNode;
import me.whereareiam.anvil.capability.commands.type.CommandCheck;
import me.whereareiam.anvil.capability.commands.type.CommandNodeType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks a command as the client does before it runs one on its own: it parses the command against the
 * server's command tree as Brigadier does, once with every node and once without the nodes that need permissions.
 *
 * <p>The client parses each argument with its own parser. Anvil parses literals and the Brigadier types exactly,
 * reads a {@code minecraft:message} argument to the end of the command, coordinates as their two or three parts,
 * entity selectors with their options, and any other argument as one word, in which quotes and brackets may hold
 * spaces. It does not check that such an argument's text is valid for its type.</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CommandVerifier {
	private static final String SIGNED_ARGUMENT = "minecraft:message";

	/**
	 * Checks a command.
	 *
	 * @param tree the command tree the server sent, or {@code null} when it sent none
	 * @param command the command without its leading slash
	 * @return what the client finds
	 */
	public static @NotNull CommandCheck check(@Nullable CommandTree tree, @NotNull String command) {
		if (tree == null) return CommandCheck.PARSE_ERRORS;

		Parse full = new Parser(tree, true).parse(command);
		if (!full.valid()) return CommandCheck.PARSE_ERRORS;
		if (full.signed()) return CommandCheck.SIGNATURE_REQUIRED;

		return new Parser(tree, false).parse(command).valid() ? CommandCheck.NO_ISSUES : CommandCheck.PERMISSIONS_REQUIRED;
	}

	/**
	 * Result of one parse: how far the input was read, whether parsing failed, the node the command ended at and
	 * whether a signed argument was used.
	 */
	private record Parse(int cursor, int length, boolean failed, @Nullable CommandTreeNode last, boolean signed) {
		boolean valid() {
			return cursor >= length && !failed && last != null && last.isExecutable();
		}

		boolean consumed() {
			return cursor >= length;
		}
	}

	/**
	 * Brigadier's parse over the tree: at each node, a literal matching the next word is the only candidate,
	 * otherwise every argument is; each candidate that parses continues with its children or its redirect, and the
	 * candidate that read the whole input without errors wins.
	 */
	private static final class Parser {
		private final CommandTree tree;
		private final boolean withRestricted;
		private String input = "";

		private Parser(CommandTree tree, boolean withRestricted) {
			this.tree = tree;
			this.withRestricted = withRestricted;
		}

		private Parse parse(String command) {
			input = command;
			return parseNodes(node(tree.getRoot()), 0, null, false);
		}

		private Parse parseNodes(CommandTreeNode node, int cursor, @Nullable CommandTreeNode last, boolean signed) {
			List<Parse> potentials = new ArrayList<>();
			boolean failed = false;

			for (CommandTreeNode child : relevant(node, cursor)) {
				if (child.isRestricted() && !withRestricted) continue;

				int end = read(child, cursor);
				if (end < 0 || end < input.length() && input.charAt(end) != ' ') {
					failed = true;
					continue;
				}

				potentials.add(parseAfter(child, end, signed || SIGNED_ARGUMENT.equals(child.getParser())));
			}

			if (potentials.isEmpty()) return new Parse(cursor, input.length(), failed || cursor < input.length(), last, signed);

			return potentials.stream()
					.sorted((first, second) -> {
						if (first.consumed() != second.consumed()) return first.consumed() ? -1 : 1;
						if (first.failed() != second.failed()) return first.failed() ? 1 : -1;
						return 0;
					})
					.findFirst()
					.orElseThrow();
		}

		/**
		 * Continues after a node that parsed up to {@code end}: with its children, or with the node it redirects to,
		 * when input is left for them, otherwise the command ends at the node.
		 */
		private Parse parseAfter(CommandTreeNode child, int end, boolean signed) {
			Integer redirect = child.getRedirect();
			if (end + (redirect == null ? 2 : 1) > input.length()) return new Parse(end, input.length(), false, child, signed);

			CommandTreeNode next = redirect == null ? child : node(redirect);
			return parseNodes(next, end + 1, child, signed);
		}

		/**
		 * Returns the literal child named by the next word if there is one, otherwise every argument child.
		 */
		private List<CommandTreeNode> relevant(CommandTreeNode node, int cursor) {
			int space = input.indexOf(' ', cursor);
			String word = input.substring(cursor, space < 0 ? input.length() : space);
			List<CommandTreeNode> arguments = new ArrayList<>();
			for (int index : node.getChildren()) {
				CommandTreeNode child = node(index);
				if (child.getType() == CommandNodeType.LITERAL && child.getName().equals(word)) return List.of(child);
				if (child.getType() == CommandNodeType.ARGUMENT) arguments.add(child);
			}
			return arguments;
		}

		/**
		 * Reads one node from the cursor.
		 *
		 * @return the position after the node, or {@code -1} when the node does not parse there
		 */
		private int read(CommandTreeNode node, int cursor) {
			if (node.getType() == CommandNodeType.LITERAL) {
				int end = cursor + node.getName().length();
				return input.startsWith(node.getName(), cursor) ? end : -1;
			}
			return new ArgumentReader(input, cursor).read(node);
		}

		private CommandTreeNode node(int index) {
			return tree.getNodes().get(index);
		}
	}
}
