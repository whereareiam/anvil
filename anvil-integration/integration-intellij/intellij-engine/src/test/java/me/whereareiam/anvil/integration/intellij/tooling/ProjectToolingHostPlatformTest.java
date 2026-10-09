package me.whereareiam.anvil.integration.intellij.tooling;

import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import me.whereareiam.anvil.integration.intellij.EnginePlatformTestCase;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import org.junit.jupiter.api.Assertions;

public class ProjectToolingHostPlatformTest extends EnginePlatformTestCase {
	public void testThrowingListenerStillStartsTheLaunchSoReplacementsProceed() throws Exception {
		var host = new ProjectToolingHost(getProject(), builder -> {
			throw new AssertionError("Unresolvable sources must fail before starting a process");
		});
		Disposer.register(getTestRootDisposable(), host);
		IllegalStateException listenerFailure = new IllegalStateException("Listener failed");
		AtomicInteger notified = new AtomicInteger();
		host.subscribe((launch, purpose) -> {
			if (notified.incrementAndGet() == 1) throw listenerFailure;
		}, getTestRootDisposable());
		ScenarioSource source = source();

		assertSame(listenerFailure, Assertions.assertThrows(IllegalStateException.class, () -> host.reload(source)));
		ToolingLaunch first = host.current();
		assertNotSame(ToolingLaunch.State.NEW, first.state());
		first.outcome().get(5, TimeUnit.SECONDS);

		host.reload(source);
		ToolingLaunch second = host.current();
		assertNotSame(first, second);
		assertInstanceOf(second.outcome().get(5, TimeUnit.SECONDS), ToolingLaunch.Outcome.Failed.class);
		assertEquals(ProjectToolingHost.Purpose.DISCOVERY, host.purpose(second));
	}

	private ScenarioSource source() throws Exception {
		Path directory = Files.createTempDirectory("anvil-host-");
		Disposer.register(getTestRootDisposable(), () -> FileUtil.delete(directory.toFile()));

		return ScenarioSource.builder()
				.id("fixture:host")
				.integrationId("fixture")
				.displayName("Fixture")
				.directory(directory)
				.build();
	}
}
