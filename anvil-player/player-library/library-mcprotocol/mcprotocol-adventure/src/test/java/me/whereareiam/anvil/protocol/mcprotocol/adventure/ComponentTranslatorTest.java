package me.whereareiam.anvil.protocol.mcprotocol.adventure;

import me.whereareiam.anvil.protocol.api.model.text.Text;
import me.whereareiam.anvil.protocol.api.model.text.TextColor;
import me.whereareiam.anvil.protocol.api.model.text.TextStyle;
import me.whereareiam.anvil.protocol.api.model.text.click.ChangePageClick;
import me.whereareiam.anvil.protocol.api.model.text.click.CopyToClipboardClick;
import me.whereareiam.anvil.protocol.api.model.text.click.OpenFileClick;
import me.whereareiam.anvil.protocol.api.model.text.click.OpenUrlClick;
import me.whereareiam.anvil.protocol.api.model.text.click.RunCommandClick;
import me.whereareiam.anvil.protocol.api.model.text.click.SuggestCommandClick;
import me.whereareiam.anvil.protocol.api.model.text.content.KeybindContent;
import me.whereareiam.anvil.protocol.api.model.text.content.LiteralContent;
import me.whereareiam.anvil.protocol.api.model.text.content.NbtContent;
import me.whereareiam.anvil.protocol.api.model.text.content.ScoreContent;
import me.whereareiam.anvil.protocol.api.model.text.content.SelectorContent;
import me.whereareiam.anvil.protocol.api.model.text.content.TranslatedContent;
import me.whereareiam.anvil.protocol.api.model.text.hover.ShowEntityHover;
import me.whereareiam.anvil.protocol.api.model.text.hover.ShowItemHover;
import me.whereareiam.anvil.protocol.api.model.text.hover.ShowTextHover;
import me.whereareiam.anvil.protocol.api.type.NbtSource;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.BlockNBTComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.SelectorComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;

import static me.whereareiam.anvil.protocol.api.type.TextDecoration.BOLD;
import static me.whereareiam.anvil.protocol.api.type.TextDecoration.ITALIC;
import static net.kyori.adventure.text.BlockNBTComponent.WorldPos.Coordinate.absolute;
import static net.kyori.adventure.text.BlockNBTComponent.WorldPos.Coordinate.relative;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ComponentTranslatorTest {
	private static final ComponentTranslator TEXT = new Translator();

	@Test
	void keepsTextAsWrittenWithItsChildrenInOrder() {
		Component message = Component.text("anvil:")
				.append(Component.text("wel"))
				.append(Component.text("come").append(Component.text("!")));

		assertEquals("anvil:welcome!", TEXT.translate(message).plain());
	}

	@Test
	void ignoresStyle() {
		assertEquals("ready", TEXT.translate(Component.text("ready", NamedTextColor.GREEN)).plain());
	}

	@Test
	void writesATranslationAsItsKeyFollowedByEachArgument() {
		Component message = Component.translatable("chat.type.text", Component.text("Alice"), Component.text("hello"))
				.append(Component.text("!"));

		assertEquals("chat.type.text Alice hello!", TEXT.translate(message).plain());
	}

	@Test
	void flattensNestedTranslationArguments() {
		Component message = Component.translatable("multiplayer.disconnect.kicked",
				Component.translatable("commands.kick.success", Component.text("Alice").append(Component.text("_2"))));

		assertEquals("multiplayer.disconnect.kicked commands.kick.success Alice_2", TEXT.translate(message).plain());
	}

	@Test
	void writesATranslationWithoutArgumentsAsItsKey() {
		assertEquals("disconnect.timeout", TEXT.translate(Component.translatable("disconnect.timeout")).plain());
	}

	@Test
	void writesKeybindsAndSelectorsAsTheirNamesAndIgnoresScores() {
		Component message = Component.text("press ")
				.append(Component.keybind("key.jump"))
				.append(Component.text(" near "))
				.append(Component.selector("@p"))
				.append(Component.score("Alice", "kills"))
				.append(Component.text("!"));

		assertEquals("press key.jump near @p!", TEXT.translate(message).plain());
	}

	@Test
	void flattensAnEmptyComponentToEmptyText() {
		assertEquals("", TEXT.translate(Component.empty()).plain());
	}

	@Test
	void keepsEachComponentWithItsContentStyleAndChildren() {
		Component message = Component.text("[", NamedTextColor.DARK_GRAY)
				.decorate(TextDecoration.BOLD)
				.append(Component.text("Arbor", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));

		assertEquals(Text.builder()
				.content(new LiteralContent("["))
				.style(TextStyle.builder().color(TextColor.DARK_GRAY).decoration(BOLD, true).build())
				.child(Text.builder()
						.content(new LiteralContent("Arbor"))
						.style(TextStyle.builder().color(TextColor.GOLD).decoration(ITALIC, false).build())
						.build())
				.build(), TEXT.translate(message));
	}

	@Test
	void keepsATranslationWithItsKeyAndStyledArguments() {
		Component message = Component.translatable("multiplayer.player.joined", NamedTextColor.YELLOW,
				Component.text("Ivan", NamedTextColor.AQUA));

		Text text = TEXT.translate(message);

		TranslatedContent joined = text.find(TranslatedContent.class).orElseThrow();
		assertEquals("multiplayer.player.joined", joined.getKey());
		assertNull(joined.getFallback());
		assertEquals(List.of(Text.builder()
				.content(new LiteralContent("Ivan"))
				.style(TextStyle.builder().color(TextColor.AQUA).build())
				.build()), joined.getArguments());
		assertEquals("multiplayer.player.joined Ivan", text.plain());
	}

	@Test
	void keepsKeybindsSelectorsScoresAndNbt() {
		Component message = Component.keybind("key.jump")
				.append(Component.selector("@p"))
				.append(Component.score("Alice", "kills"))
				.append(Component.blockNBT("Items[0]", BlockNBTComponent.WorldPos.of(absolute(1), relative(1), absolute(-2))))
				.append(Component.entityNBT("Health", "@s"))
				.append(Component.storageNBT("value", Key.key("arbor", "state")));

		Text text = TEXT.translate(message);

		assertEquals(new KeybindContent("key.jump"), text.getContent());
		assertEquals(List.of(
				new SelectorContent("@p", null),
				new ScoreContent("Alice", "kills"),
				new NbtContent("Items[0]", false, null, NbtSource.BLOCK, "1 ~1 -2"),
				new NbtContent("Health", false, null, NbtSource.ENTITY, "@s"),
				new NbtContent("value", false, null, NbtSource.STORAGE, "arbor:state")
		), text.getChildren().stream().map(Text::getContent).toList());
	}

	@Test
	void readsEveryClickWithTheValueTheReleaseReads() {
		Component message = Component.text("1").clickEvent(ClickEvent.openUrl("https://example.test"))
				.append(Component.text("2").clickEvent(ClickEvent.runCommand("/arbor help")))
				.append(Component.text("3").clickEvent(ClickEvent.suggestCommand("/vote ")))
				.append(Component.text("4").clickEvent(ClickEvent.changePage(3)))
				.append(Component.text("5").clickEvent(ClickEvent.copyToClipboard("copied")))
				.append(Component.text("6").clickEvent(ClickEvent.openFile("screenshot.png")));

		Text text = TEXT.translate(message);

		assertEquals(List.of(
				new OpenUrlClick("https://example.test"),
				new RunCommandClick("/arbor help"),
				new SuggestCommandClick("/vote "),
				new ChangePageClick(3),
				new CopyToClipboardClick("copied"),
				new OpenFileClick("screenshot.png")
		), text.spans().stream().map(span -> span.getStyle().getClick()).toList());
	}

	@Test
	void readsTextItemAndEntityHovers() {
		UUID zombie = UUID.randomUUID();
		Component message = Component.text("a").hoverEvent(HoverEvent.showText(Component.text("tip", NamedTextColor.GREEN)))
				.append(Component.text("b").hoverEvent(HoverEvent.showItem(Key.key("minecraft", "diamond"), 2)))
				.append(Component.text("c").hoverEvent(HoverEvent.showEntity(Key.key("minecraft", "zombie"), zombie,
						Component.text("Bob"))));

		Text text = TEXT.translate(message);

		assertEquals(List.of(
				new ShowTextHover(Text.builder()
						.content(new LiteralContent("tip"))
						.style(TextStyle.builder().color(TextColor.GREEN).build())
						.build()),
				new ShowItemHover("minecraft:diamond", 2),
				new ShowEntityHover("minecraft:zombie", zombie, Text.literal("Bob"))
		), text.spans().stream().map(span -> span.getStyle().getHover()).toList());
	}

	@Test
	void readsTheFontAndInsertionOfAComponent() {
		Component message = Component.text("vote")
				.style(style -> style.font(Key.key("minecraft", "uniform")))
				.insertion("/vote");

		TextStyle style = TEXT.translate(message).getStyle();

		assertEquals("minecraft:uniform", style.getFont());
		assertEquals("/vote", style.getInsertion());
	}

	@Test
	void readsWhatTheReleasesAdventureKeepsDifferentlyThroughTheSubclass() {
		ComponentTranslator release = new Translator() {
			@Override
			protected String clickValue(@NotNull ClickEvent click) {
				return "read " + click.value();
			}

			@Override
			protected String fallback(@NotNull TranslatableComponent translation) {
				return "Hello";
			}

			@Override
			protected Component separator(@NotNull SelectorComponent selector) {
				return Component.text(" & ");
			}
		};
		Component message = Component.translatable("arbor.hello", Component.text("Ivan"))
				.clickEvent(ClickEvent.runCommand("/hi"))
				.append(Component.selector("@a"));

		Text text = release.translate(message);

		assertEquals(new TranslatedContent("arbor.hello", "Hello", List.of(Text.literal("Ivan"))), text.getContent());
		assertEquals(new RunCommandClick("read /hi"), text.getStyle().getClick());
		assertEquals(new SelectorContent("@a", Text.literal(" & ")), text.getChildren().getFirst().getContent());
	}

	@Test
	void refusesAComponentOfAKindNeitherAdventureNorTheReleaseHas() {
		Component unknown = (Component) Proxy.newProxyInstance(Component.class.getClassLoader(),
				new Class<?>[]{Component.class}, (proxy, method, arguments) -> null);

		assertThrows(IllegalArgumentException.class, () -> TEXT.translate(unknown));
	}

	/**
	 * Reads what moved as Adventure 4.9, the closure these tests compile against, keeps it.
	 */
	private static class Translator extends ComponentTranslator {
		@Override
		protected @NotNull List<? extends ComponentLike> arguments(@NotNull TranslatableComponent translation) {
			return translation.args();
		}

		@Override
		protected String clickValue(@NotNull ClickEvent click) {
			return click.value();
		}
	}
}
