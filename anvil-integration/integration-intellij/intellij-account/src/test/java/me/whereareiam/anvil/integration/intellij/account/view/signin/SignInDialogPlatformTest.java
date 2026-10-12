package me.whereareiam.anvil.integration.intellij.account.view.signin;

import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.JBUI;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.JButton;
import javax.swing.JPanel;

import me.whereareiam.anvil.integration.intellij.AccountPlatformTestCase;
import me.whereareiam.anvil.integration.intellij.ControlledToolingProcess;
import me.whereareiam.anvil.integration.intellij.SwingTestSupport;
import me.whereareiam.anvil.integration.intellij.account.AccountSignInFixture;

public class SignInDialogPlatformTest extends AccountPlatformTestCase {
	public void testInstructionsAppearWhileSigningInAndCompletionOffersClose() throws Exception {
		SwingTestSupport.useDarcula(getTestRootDisposable());
		Path directory = Files.createTempDirectory("anvil-sign-in-");
		var authentication = new ControlledToolingProcess(false);
		var dialog = new SignInDialog(getProject(), "alice",
				output -> {
					try {
						return AccountSignInFixture.controlled(directory, output, authentication);
					} catch (java.io.IOException failure) {
						throw new AssertionError(failure);
					}
				}, () -> {});
		Disposer.register(getTestRootDisposable(), dialog.getDisposable());
		var panel = new JPanel(new BorderLayout(0, 12));
		panel.setBorder(JBUI.Borders.empty(12));
		panel.add(dialog.createCenterPanel(), BorderLayout.CENTER);
		var footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		footer.add(new JButton(dialog.createActions()[0]));
		panel.add(footer, BorderLayout.SOUTH);
		try {
			authentication.emit("Open https://example.test/device?code=fixture");
			SwingTestSupport.await(() -> SwingTestSupport.text(panel).contains("Waiting for you to sign in"));
			assertNotNull(SwingTestSupport.button(panel, "Copy link"));
			assertNotNull(SwingTestSupport.button(panel, "Cancel sign-in"));
			SwingTestSupport.capture(panel, "project-accounts-sign-in", 660, 400);

			authentication.complete(0);
			SwingTestSupport.await(() -> SwingTestSupport.text(panel).contains("Account added to this project"));
			assertNotNull(SwingTestSupport.button(panel, "Close"));
		} finally {
			dialog.doCancelAction();
		}
	}

	public void testClosingTheDialogCancelsTheSignInAndStopsItsProcess() throws Exception {
		Path directory = Files.createTempDirectory("anvil-sign-in-");
		var authentication = new ControlledToolingProcess(false);
		var dialog = new SignInDialog(getProject(), "alice",
				output -> {
					try {
						return AccountSignInFixture.controlled(directory, output, authentication);
					} catch (java.io.IOException failure) {
						throw new AssertionError(failure);
					}
				}, () -> {});
		Disposer.register(getTestRootDisposable(), dialog.getDisposable());
		var panel = dialog.createCenterPanel();
		authentication.emit("Open https://example.test/device?code=fixture");
		SwingTestSupport.await(() -> SwingTestSupport.text(panel).contains("Waiting for you to sign in"));

		dialog.doCancelAction();

		SwingTestSupport.await(() -> !authentication.isAlive());
	}
}
