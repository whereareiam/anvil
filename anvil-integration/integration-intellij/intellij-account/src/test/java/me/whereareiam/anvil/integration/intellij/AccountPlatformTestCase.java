package me.whereareiam.anvil.integration.intellij;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import me.whereareiam.anvil.integration.intellij.account.AccountLibrary;
import me.whereareiam.anvil.integration.intellij.account.settings.AccountSettings;

/**
 * Gives each test fresh account settings and a fresh project account library over the module's real descriptor.
 */
public abstract class AccountPlatformTestCase extends BasePlatformTestCase {
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		ServiceContainerUtil.registerOrReplaceServiceInstance(
				ApplicationManager.getApplication(), AccountSettings.class, new AccountSettings(), getTestRootDisposable());
		ServiceContainerUtil.registerOrReplaceServiceInstance(
				getProject(), AccountLibrary.class, new AccountLibrary(getProject()), getTestRootDisposable());
	}
}
