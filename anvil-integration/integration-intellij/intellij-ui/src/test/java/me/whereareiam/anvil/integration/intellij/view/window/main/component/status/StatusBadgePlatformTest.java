package me.whereareiam.anvil.integration.intellij.view.window.main.component.status;

import com.intellij.util.ui.JBUI;

import java.util.List;

import me.whereareiam.anvil.integration.intellij.UiPlatformTestCase;
import me.whereareiam.anvil.integration.intellij.WindowTestSupport;
import me.whereareiam.anvil.integration.intellij.scenario.execution.EnvironmentSessionFixture;
import me.whereareiam.anvil.integration.intellij.type.EnvironmentState;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.model.process.ProcessSnapshot;
import me.whereareiam.anvil.tooling.api.type.ProcessState;
import me.whereareiam.anvil.tooling.api.type.SessionState;

public class StatusBadgePlatformTest extends UiPlatformTestCase {
	public void testBadgeRetainsAccessibleStatusAndCountsAlongsideColor() {
		var scenario = WindowTestSupport.scenario();
		var snapshot =
				SessionSnapshot.builder()
						.state(SessionState.RUNNING)
						.processes(List.of(live("paper", "READY")))
						.build();
		StatusBadge badge = new StatusBadge();
		badge.update(scenario, snapshot, EnvironmentState.PARTIALLY_RUNNING);
		assertEquals("Partially running", badge.getText());
		assertEquals(badge.getText(), badge.getAccessibleContext().getAccessibleName());
		assertEquals("Partially running · 1 of 2 processes running", badge.getToolTipText());
		assertEquals(badge.getToolTipText(), badge.getAccessibleContext().getAccessibleDescription());
		assertFalse(badge.isOpaque());
		assertTrue(badge.getInsets().left > 0);
		assertTrue(
				"Badge should leave space for native section navigation",
				badge.getPreferredSize().width < JBUI.scale(160));
	}

	private static ProcessSnapshot live(String name, String state) {
		return ProcessSnapshot.builder()
				.name(name)
				.executionId(EnvironmentSessionFixture.executionId(name))
				.displayName(name)
				.state(ProcessState.fromWireValue(state))
				.host("127.0.0.1")
				.port(25565)
				.workDirectory("/workspace/" + name)
				.build();
	}
}
