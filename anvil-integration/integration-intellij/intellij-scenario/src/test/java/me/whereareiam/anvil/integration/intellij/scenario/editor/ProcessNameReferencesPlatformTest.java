package me.whereareiam.anvil.integration.intellij.scenario.editor;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.List;

import me.whereareiam.anvil.integration.intellij.ScenarioApiFixture;
import me.whereareiam.anvil.integration.intellij.ScenarioPlatformTestCase;

public class ProcessNameReferencesPlatformTest extends ScenarioPlatformTestCase {
	private static final String SCENARIO = """
			import me.whereareiam.anvil.api.model.process.*;
			import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
			class Network {
				static final String LOBBY = "lobby";
				AnvilScenario define() {
					var lobby = MinecraftServer.builder().name(LOBBY).build();
					var games = MinecraftServer.builder().name("games").build();
					var proxy = MinecraftProxy.builder().name("velocity").server(%s).defaultServer("lobby").build();
					return AnvilScenario.builder().name("network").entrypoint(%s).server(lobby).server(games).proxy(proxy).build();
				}
			}
			""";

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		ScenarioApiFixture.add(myFixture);
	}

	public void testCompletesTheEntrypointWithTheDeclaredProcesses() {
		myFixture.configureByText("Network.java", SCENARIO.formatted("\"games\"", "\"<caret>\""));

		myFixture.completeBasic();

		assertContainsElements(myFixture.getLookupElementStrings(), List.of("lobby", "games", "velocity"));
	}

	public void testCompletesTheServersOfAProxy() {
		myFixture.configureByText("Network.java", SCENARIO.formatted("\"<caret>\"", "\"velocity\""));

		myFixture.completeBasic();

		assertContainsElements(myFixture.getLookupElementStrings(), List.of("lobby", "games", "velocity"));
	}

	public void testNavigatesToTheDeclaration() {
		myFixture.configureByText("Network.java", SCENARIO.formatted("\"games\"", "\"veloc<caret>ity\""));

		var target = myFixture.getElementAtCaret();

		assertInstanceOf(target, PsiLiteralExpression.class);
		assertEquals("velocity", ((PsiLiteralExpression) target).getValue());
	}

	public void testLeavesProcessesDeclaredElsewhereUnreported() {
		myFixture.configureByText("Network.java", SCENARIO.formatted("\"games\"", "\"elsewhere\""));

		assertEmpty(myFixture.doHighlighting(HighlightSeverity.ERROR));
		var literal = PsiTreeUtil.findChildrenOfType(myFixture.getFile(), PsiLiteralExpression.class).stream()
				.filter(candidate -> "elsewhere".equals(candidate.getValue()))
				.findFirst()
				.orElseThrow();
		assertNull(literal.getReferences()[0].resolve());
	}
}
