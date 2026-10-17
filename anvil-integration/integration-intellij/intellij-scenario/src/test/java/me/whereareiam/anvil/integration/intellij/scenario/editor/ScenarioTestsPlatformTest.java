package me.whereareiam.anvil.integration.intellij.scenario.editor;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;

import java.util.List;

import me.whereareiam.anvil.integration.intellij.ScenarioPlatformTestCase;

public class ScenarioTestsPlatformTest extends ScenarioPlatformTestCase {
	private static final String ANNOTATION = """
			package me.whereareiam.anvil.integration.junit;
			public @interface AnvilTest { Class<?> value(); }
			""";

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		myFixture.addFileToProject("example/Registration.java", "package example; public class Registration {}");
		myFixture.addFileToProject("example/Lobby.java", "package example; public class Lobby {}");
	}

	public void testFindsAnnotatedClassesAndMethodsOfTheScenario() {
		myFixture.addFileToProject("me/whereareiam/anvil/integration/junit/AnvilTest.java", ANNOTATION);
		myFixture.addFileToProject("example/RegistrationTest.java", """
				package example;
				import me.whereareiam.anvil.integration.junit.AnvilTest;
				@AnvilTest(Registration.class)
				class RegistrationTest {
					@AnvilTest(Registration.class) void registers() {}
					void logsIn() {}
				}
				""");
		myFixture.addFileToProject("example/JourneyTest.java", """
				package example;
				import me.whereareiam.anvil.integration.junit.AnvilTest;
				class JourneyTest {
					@AnvilTest(Registration.class) void registers() {}
					@AnvilTest(Lobby.class) void joinsTheLobby() {}
				}
				""");
		myFixture.addFileToProject("example/BaseTest.java", """
				package example;
				@me.whereareiam.anvil.integration.junit.AnvilTest(Registration.class)
				abstract class BaseTest {}
				""");

		var tests = ScenarioTests.using(type("Registration")).stream().map(ScenarioTests::label).toList();

		assertEquals(List.of("JourneyTest.registers", "RegistrationTest"), tests);
		assertEquals(List.of("JourneyTest.joinsTheLobby"),
				ScenarioTests.using(type("Lobby")).stream().map(ScenarioTests::label).toList());
	}

	public void testFindsNothingWithoutTheJUnitIntegration() {
		myFixture.addFileToProject("example/RegistrationTest.java", "package example; class RegistrationTest {}");

		assertEmpty(ScenarioTests.using(type("Registration")));
	}

	private PsiClass type(String name) {
		return JavaPsiFacade.getInstance(getProject()).findClass("example." + name, GlobalSearchScope.projectScope(getProject()));
	}
}
