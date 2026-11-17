package me.whereareiam.anvil.capability.packets.recording;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import me.whereareiam.anvil.capability.packets.model.RecordedPackets;
import me.whereareiam.anvil.capability.packets.model.RecordingHeader;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundFrame;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.type.ConnectionState;
import me.whereareiam.anvil.protocol.java.json.JavaProtocolJson;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Reads and writes recordings as gzip-compressed JSON Lines: the {@link RecordingHeader} on the first line, then one
 * line per frame in the order the frames arrived, with its position {@code seq}, its {@code state}, {@code id},
 * {@code name}, its bytes as base64 {@code payload} and, when the protocol models the packet, its value as
 * {@code packet} in the Java protocol's JSON form, which names the value's {@code kind}.
 *
 * <pre>{@code
 * {"format":1,"protocol":774,"minecraft":"1.21.11","player":"Alice"}
 * {"seq":0,"state":"PLAY","id":43,"name":"login","payload":"AAAAKg...","packet":{"kind":"ClientboundLogin",...}}
 * {"seq":1,"state":"PLAY","id":42,"name":"update_light","payload":"AgAB..."}
 * }</pre>
 */
public final class RecordingFiles {
	private final ObjectMapper json = JavaProtocolJson.configure(new ObjectMapper())
			.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

	/**
	 * Writes a recording, replacing the file.
	 *
	 * @param recording the recording
	 * @param file the file
	 * @throws IOException when the file cannot be written
	 */
	public void write(@NotNull RecordedPackets recording, @NotNull Path file) throws IOException {
		try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
				new GZIPOutputStream(Files.newOutputStream(file)), StandardCharsets.UTF_8))) {
			out.write(json.writeValueAsString(recording.getHeader()));
			out.newLine();
			List<ClientboundFrame> frames = recording.getFrames();
			for (int seq = 0; seq < frames.size(); seq++) {
				ClientboundFrame frame = frames.get(seq);
				out.write(json.writeValueAsString(new Line(seq, frame.getState(), frame.getId(), frame.name().orElse(null),
						frame.payload(), frame.packet().orElse(null))));
				out.newLine();
			}
		}
	}

	/**
	 * Reads a recording.
	 *
	 * @param file the file
	 * @return the recording
	 * @throws IOException when the file cannot be read
	 * @throws IllegalArgumentException when the file is no recording of a format this Anvil reads
	 */
	public @NotNull RecordedPackets read(@NotNull Path file) throws IOException {
		try (BufferedReader in = new BufferedReader(new InputStreamReader(
				new GZIPInputStream(Files.newInputStream(file)), StandardCharsets.UTF_8))) {
			RecordingHeader header = header(file, in.readLine());
			List<ClientboundFrame> frames = new ArrayList<>();
			for (String text = in.readLine(); text != null; text = in.readLine()) {
				Line line = line(file, frames.size(), text);
				frames.add(new ClientboundFrame(line.state(), line.id(), line.name(), line.payload(), line.packet()));
			}

			return new RecordedPackets(header, List.copyOf(frames));
		}
	}

	private RecordingHeader header(Path file, @Nullable String text) {
		if (text == null) throw new IllegalArgumentException(file + " is empty, not a packet recording");

		RecordingHeader header = parse(file, "its header", text, RecordingHeader.class);
		if (header.getFormat() != RecordingHeader.FORMAT)
			throw new IllegalArgumentException(file + " is a packet recording of format " + header.getFormat()
					+ ", which this Anvil does not read; it reads format " + RecordingHeader.FORMAT);

		return header;
	}

	private Line line(Path file, int expected, String text) {
		Line line = parse(file, "frame " + expected, text, Line.class);
		if (line.seq() != expected)
			throw new IllegalArgumentException(file + " holds frame " + line.seq() + " where frame " + expected
					+ " belongs");

		return line;
	}

	private <T> T parse(Path file, String what, String text, Class<T> type) {
		try {
			return json.readValue(text, type);
		} catch (JacksonException invalid) {
			throw new IllegalArgumentException(file + " is no packet recording this Anvil reads: " + what + " is "
					+ "invalid: " + invalid.getOriginalMessage(), invalid);
		}
	}

	/**
	 * One frame's line.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private record Line(
			int seq,
			@NotNull ConnectionState state,
			int id,
			@Nullable String name,
			byte @NotNull [] payload,
			@Nullable ClientboundPacket packet
	) {
	}
}
