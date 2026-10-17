package me.whereareiam.anvil.integration.intellij.gradle.eula;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class EulaInspectionPlatformTest extends BasePlatformTestCase {
	private static final String FIX = "Accept the Minecraft EULA";

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		myFixture.enableInspections(new EulaInspection());
	}

	public void testReportsAnAnvilBuildWithoutTheEula() {
		myFixture.configureByText("build.gradle", """
				plugins {
				    java
				    id("me.whereareiam.<caret>anvil")
				}
				""");

		assertEquals(1, myFixture.filterAvailableIntentions(FIX).size());
	}

	public void testAddsTheCallToTheExistingAnvilBlock() {
		myFixture.configureByText("build.gradle", """
				plugins {
				    id("me.whereareiam.<caret>anvil.junit")
				}

				anvil {
				    artifact("plugin-under-test", tasks.named("jar"))
				}
				""");

		myFixture.launchAction(myFixture.findSingleIntention(FIX));

		myFixture.checkResult("""
				plugins {
				    id("me.whereareiam.anvil.junit")
				}

				anvil {
				    acceptEula()
				    artifact("plugin-under-test", tasks.named("jar"))
				}
				""");
	}

	public void testAddsAnAnvilBlockWhenTheScriptHasNone() {
		myFixture.configureByText("build.gradle", "plugins {\n\tid 'me.whereareiam.<caret>anvil'\n}\n");

		myFixture.launchAction(myFixture.findSingleIntention(FIX));

		myFixture.checkResult("plugins {\n\tid 'me.whereareiam.anvil'\n}\n\nanvil {\n\tacceptEula()\n}\n");
	}

	public void testPlacesTheBlockAfterTheExistingIndentation() {
		var insertion = AcceptEulaFix.insertion("plugins {\n    id(\"me.whereareiam.anvil\")\n}");

		assertEquals("\n\nanvil {\n    acceptEula()\n}\n", insertion.text());
	}

	public void testIgnoresBuildsThatAcceptTheEulaOrDoNotUseAnvil() {
		myFixture.configureByText("build.gradle", """
				plugins { id("me.whereareiam.<caret>anvil") }
				anvil { acceptEula() }
				""");
		assertEmpty(myFixture.filterAvailableIntentions(FIX));

		myFixture.configureByText("build.gradle", "plugins { id(\"me.whereareiam.<caret>anvil.capability.default\") }\n");
		assertEmpty(myFixture.filterAvailableIntentions(FIX));

		myFixture.configureByText("settings.gradle", "plugins { id(\"me.whereareiam.<caret>anvil\") }\n");
		assertEmpty(myFixture.filterAvailableIntentions(FIX));
	}
}
