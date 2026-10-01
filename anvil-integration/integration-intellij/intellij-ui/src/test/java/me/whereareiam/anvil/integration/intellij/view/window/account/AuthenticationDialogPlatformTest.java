package me.whereareiam.anvil.integration.intellij.view.window.account;

import com.intellij.openapi.util.Disposer;

import java.util.concurrent.CompletableFuture;
import javax.swing.JButton;
import javax.swing.JPanel;

import me.whereareiam.anvil.integration.intellij.UiPlatformTestCase;
import me.whereareiam.anvil.integration.intellij.WindowTestSupport;
import me.whereareiam.anvil.integration.intellij.account.authentication.AccountEnrollment;

public class AuthenticationDialogPlatformTest extends UiPlatformTestCase {
	public void testSignInInstructionsAppearWhileLoginIsStillRunning() throws Exception {
		WindowTestSupport.useDarcula(getTestRootDisposable());
		var login = new ControlledEnrollment();
		var dialog = new AuthenticationDialog(getProject(), "alice", output -> {
			login.output = output;
			return login;
		}, () -> {});
		Disposer.register(getTestRootDisposable(), dialog.getDisposable());
		try {
			var panel = new JPanel(new java.awt.BorderLayout(0, 12));
			panel.setBorder(com.intellij.util.ui.JBUI.Borders.empty(12));
			panel.add(dialog.createCenterPanel(), java.awt.BorderLayout.CENTER);
			var footer = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
			footer.add(new JButton(dialog.createActions()[0]));
			panel.add(footer, java.awt.BorderLayout.SOUTH);
			login.output.accept("Open https://example.test/device?code=fixture");
			WindowTestSupport.await(() -> WindowTestSupport.text(panel).contains("Waiting for you to sign in"));
			assertFalse(login.completion.isDone());
			assertNotNull(WindowTestSupport.button(panel, "Copy link"));
			assertNotNull(WindowTestSupport.button(panel, "Cancel sign-in"));
			WindowTestSupport.capture(panel, "project-accounts-sign-in", 660, 400);
			login.completion.complete(null);
			WindowTestSupport.await(() -> WindowTestSupport.text(panel).contains("Account added to this project"));
			assertNotNull(WindowTestSupport.button(panel, "Close"));
		} finally {
			dialog.doCancelAction();
			assertTrue(login.disposed);
		}
	}
	private static final class ControlledEnrollment implements AccountEnrollment {
		private final CompletableFuture<Void> completion = new CompletableFuture<>();
		private java.util.function.Consumer<String> output;
		private boolean disposed;

		@Override
		public CompletableFuture<Void> start(java.util.concurrent.Executor executor) {
			return completion;
		}

		@Override
		public void dispose() {
			disposed = true;
			completion.cancel(false);
		}
	}

}
