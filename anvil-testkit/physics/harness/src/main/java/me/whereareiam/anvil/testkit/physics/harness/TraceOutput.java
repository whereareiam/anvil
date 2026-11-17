package me.whereareiam.anvil.testkit.physics.harness;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs every course on the client and writes one {@code <course>.trace} per course that runs on the release.
 *
 * <p>A course that joins records what its handshake sent as tick {@code -1}. Before each tick, the client gets the
 * course's chunks when the join's {@code chunksAfterTicks} ticks have passed, then the server's packets of that tick,
 * then the tick runs with the course's keys held.</p>
 */
public final class TraceOutput implements VanillaOutput {
	private final Path courses;
	private final Path traces;

	/**
	 * @param courses the courses directory
	 * @param traces  the directory the traces are written into
	 */
	public TraceOutput(Path courses, Path traces) {
		this.courses = courses;
		this.traces = traces;
	}

	@Override
	public void write(VanillaClient client, String minecraft) throws Exception {
		List<Course> all = new CourseReader().readAll(courses);
		Files.createDirectories(traces);
		for (Course course : all) {
			if (!course.runsOn(minecraft))
				continue;
			List<TraceWriter.Tick> ticks = run(client, course, minecraft);
			TraceWriter.write(traces.resolve(course.getName() + ".trace"), minecraft, course.getName(), ticks);
			System.out.println("Traced " + course.getName() + " on " + minecraft + ": " + course.getTicks() + " ticks");
		}
	}

	private static List<TraceWriter.Tick> run(VanillaClient client, Course course, String minecraft) throws Exception {
		VanillaClient.CourseRun run = client.start(course);
		List<TraceWriter.Tick> ticks = new ArrayList<>();
		if (course.getJoin() != null)
			ticks.add(new TraceWriter.Tick(-1, run.sample(), run.sent()));
		for (int tick = 0; tick < course.getTicks(); tick++) {
			if (course.getJoin() != null && tick == course.getJoin().getChunksAfterTicks())
				run.loadChunks();
			for (Course.ServerPacket packet : course.serverBefore(tick))
				run.teleport(packet.getTeleport());
			run.hold(course.heldOn(tick));
			run.tick();
			ticks.add(new TraceWriter.Tick(tick, run.sample(), run.sent()));
		}
		return ticks;
	}
}
