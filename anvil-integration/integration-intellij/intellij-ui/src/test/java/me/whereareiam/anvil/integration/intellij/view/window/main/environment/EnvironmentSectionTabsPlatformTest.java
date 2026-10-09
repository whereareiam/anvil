package me.whereareiam.anvil.integration.intellij.view.window.main.environment;

import com.intellij.ide.ui.laf.UiThemeProviderListManager;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.JBUI;

import java.awt.Component;
import java.awt.Container;
import java.awt.DefaultKeyboardFocusManager;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import me.whereareiam.anvil.integration.intellij.UiPlatformTestCase;
import me.whereareiam.anvil.integration.intellij.WindowTestSupport;

public class EnvironmentSectionTabsPlatformTest extends UiPlatformTestCase {
	@Override
	protected boolean isIconRequired() {
		return true;
	}

	public void testUsesDockerSwingFamilyWithBlueUnderlineWithoutFocus() throws Exception {
		for (String themeId :
				List.of("ExperimentalDark", "ExperimentalLight", "Islands Dark", "Islands Light")) {
			var theme = UiThemeProviderListManager.Companion.getInstance().findThemeById(themeId);
			if (theme == null) {
				assertTrue(
						"Current IDE validation must include its Islands themes",
						ApplicationInfo.getInstance().getBuild().getBaselineVersion() < 262);
				continue;
			}
			WindowTestSupport.useTheme(getTestRootDisposable(), themeId, themeId.endsWith("Dark"));
			EnvironmentSectionTabs tabs = tabs();
			assertTrue(tabs instanceof JBTabbedPane);
			assertEquals(JBTabbedPane.SCROLL_TAB_LAYOUT, tabs.getTabLayoutPolicy());
			assertFalse("The selected underline must not depend on editor focus", tabs.hasFocus());
			for (int selected = 0; selected < tabs.getTabCount(); selected++) {
				tabs.setSelectedIndex(selected);
				assertFlatUnderline(tabs, render(tabs, 520));
			}
			SwingUtilities.updateComponentTreeUI(tabs);
			assertFlatUnderline(tabs, render(tabs, 520));
			WindowTestSupport.capture(
					tabs,
					"anvil-section-tabs-"
							+ ApplicationInfo.getInstance().getBuild().getBaselineVersion()
							+ "-"
							+ themeId.replace(' ', '-').toLowerCase()
							+ ".png",
					520,
					tabs.getPreferredSize().height);
		}
	}

	public void testThemeFillStyleCannotTurnSectionSelectionIntoAPill() throws Exception {
		WindowTestSupport.useDarcula(getTestRootDisposable());
		Object previous = UIManager.get("TabbedPane.tabFillStyle");
		try {
			UIManager.put("TabbedPane.tabFillStyle", "fill");
			EnvironmentSectionTabs tabs = tabs();
			assertFlatUnderline(tabs, render(tabs, 520));
		} finally {
			if (previous == null) UIManager.getDefaults().remove("TabbedPane.tabFillStyle");
			else UIManager.put("TabbedPane.tabFillStyle", previous);
		}
	}

	public void testFocusAndHoverNeverFillSelectedSections() throws Exception {
		for (String themeId :
				List.of("ExperimentalDark", "ExperimentalLight", "Islands Dark", "Islands Light")) {
			if (UiThemeProviderListManager.Companion.getInstance().findThemeById(themeId) == null)
				continue;
			WindowTestSupport.useTheme(getTestRootDisposable(), themeId, themeId.endsWith("Dark"));
			EnvironmentSectionTabs tabs = tabs();
			KeyboardFocusManager original = KeyboardFocusManager.getCurrentKeyboardFocusManager();
			try {
				KeyboardFocusManager.setCurrentKeyboardFocusManager(
						new DefaultKeyboardFocusManager() {
							@Override
							public Component getFocusOwner() {
								return tabs;
							}
						});
				assertTrue(
						"Exercise the focused selection that previously received a blue fill", tabs.hasFocus());
				assertFlatUnderline(tabs, render(tabs, 520));
			} finally {
				KeyboardFocusManager.setCurrentKeyboardFocusManager(original);
			}
			Rectangle selected = tabs.getBoundsAt(tabs.getSelectedIndex());
			tabs.dispatchEvent(
					new MouseEvent(
							tabs,
							MouseEvent.MOUSE_MOVED,
							0,
							0,
							selected.x + selected.width / 2,
							selected.y + selected.height / 2,
							0,
							false));
			assertFlatUnderline(tabs, render(tabs, 520));
		}
	}

	private static EnvironmentSectionTabs tabs() {
		EnvironmentSectionTabs tabs = new EnvironmentSectionTabs();
		for (String title : List.of("Environment", "Console", "Players")) {
			JPanel content = new JPanel(null);
			content.setPreferredSize(new Dimension());
			tabs.addTab(title, content);
		}
		return tabs;
	}

	private static BufferedImage render(EnvironmentSectionTabs tabs, int width) {
		tabs.setSize(width, tabs.getPreferredSize().height);
		layout(tabs);
		BufferedImage image = new BufferedImage(width, tabs.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try {
			tabs.printAll(graphics);
		} finally {
			graphics.dispose();
		}
		return image;
	}

	private static void layout(Container container) {
		container.doLayout();
		for (Component component : container.getComponents())
			if (component instanceof Container child) layout(child);
	}

	private static void assertFlatUnderline(EnvironmentSectionTabs tabs, BufferedImage image) {
		Rectangle selected = tabs.getBoundsAt(tabs.getSelectedIndex());
		int underline = JBUI.CurrentTheme.TabbedPane.SELECTION_HEIGHT.get();
		int y = selected.y + selected.height - Math.max(1, underline / 2);
		int expected = JBUI.CurrentTheme.TabbedPane.ENABLED_SELECTED_COLOR.getRGB();
		for (int x = selected.x + 2; x < selected.x + selected.width - 2; x++)
			assertEquals(
					"Selection must be a continuous native accent underline", expected, image.getRGB(x, y));
		assertEquals(
				"Selected tab corners retain the flat strip background",
				tabs.getBackground().getRGB(),
				image.getRGB(selected.x + 2, selected.y + 2));
		assertTrue("The underline leaves the tab body unfilled", selected.height > underline * 3);
	}
}
