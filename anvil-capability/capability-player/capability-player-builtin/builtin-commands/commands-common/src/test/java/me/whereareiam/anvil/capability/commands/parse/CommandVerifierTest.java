package me.whereareiam.anvil.capability.commands.parse;

import me.whereareiam.anvil.capability.commands.model.CommandTree;
import me.whereareiam.anvil.capability.commands.model.CommandTreeNode;
import me.whereareiam.anvil.capability.commands.type.CommandCheck;
import me.whereareiam.anvil.capability.commands.type.CommandNodeType;
import me.whereareiam.anvil.capability.commands.type.StringArgumentMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandVerifierTest {
	private static final CommandTree TREE = tree();

	@Test
	void sendsCommandsThatParseWithoutPermissions() {
		assertEquals(CommandCheck.NO_ISSUES, CommandVerifier.check(TREE, "login secret"));
		assertEquals(CommandCheck.NO_ISSUES, CommandVerifier.check(TREE, "pass Secret 123!"));
		assertEquals(CommandCheck.NO_ISSUES, CommandVerifier.check(TREE, "say \"hello world\""));
		assertEquals(CommandCheck.NO_ISSUES, CommandVerifier.check(TREE, "say 'it\\'s'"));
		assertEquals(CommandCheck.NO_ISSUES, CommandVerifier.check(TREE, "give 64"));
		assertEquals(CommandCheck.NO_ISSUES, CommandVerifier.check(TREE, "tp 1 ~2 ^3"));
		assertEquals(CommandCheck.NO_ISSUES, CommandVerifier.check(TREE, "kill @e[type=minecraft:zombie,distance=..5]"));
		assertEquals(CommandCheck.NO_ISSUES, CommandVerifier.check(TREE, "data {CustomName:\"a b\"}"));
		assertEquals(CommandCheck.NO_ISSUES, CommandVerifier.check(TREE, "toggle true"));
		assertEquals(CommandCheck.NO_ISSUES, CommandVerifier.check(TREE, "help"));
	}

	@Test
	void followsRedirectsSuchAsExecuteRun() {
		assertEquals(CommandCheck.NO_ISSUES, CommandVerifier.check(TREE, "execute run login secret"));
		assertEquals(CommandCheck.PARSE_ERRORS, CommandVerifier.check(TREE, "execute run"));
	}

	@Test
	void findsParseErrorsAsBrigadierDoes() {
		assertEquals(CommandCheck.PARSE_ERRORS, CommandVerifier.check(TREE, "login Secret123!"));
		assertEquals(CommandCheck.PARSE_ERRORS, CommandVerifier.check(TREE, "login"));
		assertEquals(CommandCheck.PARSE_ERRORS, CommandVerifier.check(TREE, "login secret "));
		assertEquals(CommandCheck.PARSE_ERRORS, CommandVerifier.check(TREE, "logout"));
		assertEquals(CommandCheck.PARSE_ERRORS, CommandVerifier.check(TREE, "say \"unclosed"));
		assertEquals(CommandCheck.PARSE_ERRORS, CommandVerifier.check(TREE, "give 65"));
		assertEquals(CommandCheck.PARSE_ERRORS, CommandVerifier.check(TREE, "give 1.5"));
		assertEquals(CommandCheck.PARSE_ERRORS, CommandVerifier.check(TREE, "tp 1 2"));
		assertEquals(CommandCheck.PARSE_ERRORS, CommandVerifier.check(TREE, "toggle yes"));
		assertEquals(CommandCheck.PARSE_ERRORS, CommandVerifier.check(null, "login secret"));
	}

	@Test
	void refusesSignedArgumentsBeforeAskingForPermissions() {
		assertEquals(CommandCheck.SIGNATURE_REQUIRED, CommandVerifier.check(TREE, "msg Bob hello there"));
		assertEquals(CommandCheck.PERMISSIONS_REQUIRED, CommandVerifier.check(TREE, "op Bob"));
	}

	/**
	 * A tree with: login &lt;word&gt;, pass &lt;greedy&gt;, say &lt;quotable&gt;, give &lt;1..64&gt;, tp &lt;block_pos&gt;,
	 * kill &lt;entity&gt;, data &lt;nbt&gt;, toggle &lt;bool&gt;, help, help &lt;word&gt;, msg &lt;entity&gt; &lt;message&gt;,
	 * restricted op &lt;entity&gt; and execute run redirecting to the root.
	 */
	private static CommandTree tree() {
		List<CommandTreeNode.CommandTreeNodeBuilder> nodes = new ArrayList<>();
		nodes.add(CommandTreeNode.builder().type(CommandNodeType.ROOT));
		command(nodes, "login", argument("password", "brigadier:string").stringMode(StringArgumentMode.SINGLE_WORD)
				.executable(true));
		command(nodes, "pass", argument("password", "brigadier:string").stringMode(StringArgumentMode.GREEDY_PHRASE)
				.executable(true));
		command(nodes, "say", argument("text", "brigadier:string").stringMode(StringArgumentMode.QUOTABLE_PHRASE)
				.executable(true));
		command(nodes, "give", argument("amount", "brigadier:integer").min(1D).max(64D).executable(true));
		command(nodes, "tp", argument("position", "minecraft:block_pos").executable(true));
		command(nodes, "kill", argument("targets", "minecraft:entity").executable(true));
		command(nodes, "data", argument("tag", "minecraft:nbt_compound_tag").executable(true));
		command(nodes, "toggle", argument("value", "brigadier:bool").executable(true));
		int help = command(nodes, "help", argument("topic", "brigadier:string")
				.stringMode(StringArgumentMode.SINGLE_WORD).executable(true));
		nodes.get(help).executable(true);
		int msg = command(nodes, "msg", argument("target", "minecraft:entity"));
		nodes.get(msg + 1).child(add(nodes, argument("message", "minecraft:message").executable(true)));
		int op = command(nodes, "op", argument("targets", "minecraft:game_profile").executable(true));
		nodes.get(op).restricted(true);
		int execute = add(nodes, literal("execute"));
		nodes.getFirst().child(execute);
		nodes.get(execute).child(add(nodes, literal("run").redirect(0)));
		return new CommandTree(nodes.stream().map(CommandTreeNode.CommandTreeNodeBuilder::build).toList(), 0);
	}
	private static int command(List<CommandTreeNode.CommandTreeNodeBuilder> nodes, String name, CommandTreeNode.CommandTreeNodeBuilder argument) {
		int literal = add(nodes, literal(name));
		nodes.getFirst().child(literal);
		nodes.get(literal).child(add(nodes, argument));

		return literal;
	}

	private static int add(List<CommandTreeNode.CommandTreeNodeBuilder> nodes, CommandTreeNode.CommandTreeNodeBuilder node) {
		nodes.add(node);
		return nodes.size() - 1;
	}

	private static CommandTreeNode.CommandTreeNodeBuilder literal(String name) {
		return CommandTreeNode.builder().type(CommandNodeType.LITERAL).name(name);
	}

	private static CommandTreeNode.CommandTreeNodeBuilder argument(String name, String parser) {
		return CommandTreeNode.builder().type(CommandNodeType.ARGUMENT).name(name).parser(parser);
	}
}
