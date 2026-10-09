package me.whereareiam.anvil.integration.intellij.view.window.main.component.status;

import com.intellij.ui.components.JBLabel;
import com.intellij.ui.icons.IconWithToolTip;
import com.intellij.util.ui.JBUI;

import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import me.whereareiam.anvil.integration.intellij.UiPlatformTestCase;
import me.whereareiam.anvil.integration.intellij.WindowTestSupport;
import me.whereareiam.anvil.integration.intellij.view.window.main.ScenarioPresentation;
import me.whereareiam.anvil.integration.intellij.view.window.main.component.ToolWindowPanel;
import me.whereareiam.anvil.tooling.api.type.ProcessRole;

public class StatusIconPlatformTest extends UiPlatformTestCase {
	@Override
	protected boolean isIconRequired() {
		return true;
	}

	public void testStandaloneDotsRetainLabelsAndThemeColor() throws Exception {
		for (boolean dark : List.of(true, false)) {
			WindowTestSupport.useTheme(getTestRootDisposable(), dark);
			for (StatusPresentation.Tone tone : StatusPresentation.Tone.values()) {
				String label = tone.name();
				Icon dot = StatusIcon.dot(tone, label);
				assertEquals(JBUI.scale(8), dot.getIconWidth());
				assertEquals(JBUI.scale(8), dot.getIconHeight());
				assertEquals(label, ((IconWithToolTip) dot).getToolTip(false));
				var image = new BufferedImage(dot.getIconWidth(), dot.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
				var painter = image.createGraphics();
				try {
					dot.paintIcon(null, painter, 0, 0);
				} finally {
					painter.dispose();
				}
				assertEquals(StatusIcon.color(tone).getRGB(), image.getRGB(JBUI.scale(4), JBUI.scale(4)));
			}
		}
	}

	public void testCustomRoleIconsAndStatusMarksRenderInBothNativeThemes() throws Exception {
		for (boolean dark : List.of(true, false)) {
			WindowTestSupport.useTheme(getTestRootDisposable(), dark);
			JPanel gallery = new ToolWindowPanel(new GridLayout(0, 4, JBUI.scale(16), JBUI.scale(10)));
			gallery.setBorder(JBUI.Borders.empty(16));
			for (StatusPresentation.Tone tone : StatusPresentation.Tone.values()) {
				String label = tone.name();
				for (Icon base : List.of(
						ScenarioPresentation.environmentIcon(),
						ScenarioPresentation.processIcon(ProcessRole.SERVER),
						ScenarioPresentation.processIcon(ProcessRole.PROXY))) {
					Icon marked = StatusIcon.overlay(tone, label, base);
					assertEquals(base.getIconWidth(), marked.getIconWidth());
					assertEquals(base.getIconHeight(), marked.getIconHeight());
					assertEquals(label, ((IconWithToolTip) marked).getToolTip(true));
					BufferedImage image = new BufferedImage(
							marked.getIconWidth(), marked.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
					var painter = image.createGraphics();
					try {
						marked.paintIcon(gallery, painter, 0, 0);
					} finally {
						painter.dispose();
					}
					assertEquals(
							StatusIcon.color(tone).getRGB(),
							image.getRGB(marked.getIconWidth() - JBUI.scale(4), marked.getIconHeight() - JBUI.scale(4)));
					gallery.add(new JBLabel(label, marked, SwingConstants.LEFT));
				}
				StatusBadge badge = new StatusBadge();
				badge.show(tone, label, label);
				gallery.add(badge);
			}
			SwingUtilities.updateComponentTreeUI(gallery);
			WindowTestSupport.capture(
					gallery, "anvil-status-icons-" + (dark ? "dark" : "light") + ".png", 760, 320);
		}
	}
}
