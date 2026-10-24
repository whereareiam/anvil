package me.whereareiam.anvil.workbench.protocol;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.cfg.ConstructorDetector;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.api.model.java.JavaSource;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.environment.execution.api.model.ProcessLaunch;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Builds the JSON mapper both ends of a workbench connection share. Anvil's models are Lombok values, so the
 * mapper reads and writes their fields and creates them through their all-arguments constructors; interfaces a
 * model holds carry the class of their value, which must be one of Anvil's.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WireMapper {
	private static final String ANVIL_PACKAGES = "me.whereareiam.anvil.";

	/**
	 * Creates a mapper; it is thread-safe once created.
	 *
	 * @return mapper for workbench messages
	 */
	public static @NotNull ObjectMapper create() {
		return JsonMapper.builder()
				.addModule(new ParameterNamesModule())
				.addModule(values())
				.constructorDetector(ConstructorDetector.USE_PROPERTIES_BASED)
				.visibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
				.visibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
				.visibility(PropertyAccessor.CREATOR, JsonAutoDetect.Visibility.ANY)
				.polymorphicTypeValidator(BasicPolymorphicTypeValidator.builder().allowIfSubType(ANVIL_PACKAGES).build())
				.addMixIn(MinecraftProcess.class, Typed.class)
				.addMixIn(JavaSource.class, Typed.class)
				.addMixIn(ProcessLaunch.class, Typed.class)
				.addMixIn(AnvilScenario.class, ScenarioWithoutHook.class)
				.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
				.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
				.build();
	}

	private static SimpleModule values() {
		SimpleModule module = new SimpleModule("anvil-workbench-values");
		module.addSerializer(Path.class, new PathSerializer());
		module.addDeserializer(Path.class, new PathDeserializer());
		module.addSerializer(InetSocketAddress.class, new AddressSerializer());
		module.addDeserializer(InetSocketAddress.class, new AddressDeserializer());
		module.addSerializer(Duration.class, new ToStringSerializer<>());
		module.addDeserializer(Duration.class, new FromStringDeserializer<>(Duration::parse));
		module.addSerializer(Pattern.class, new ToStringSerializer<>());
		module.addDeserializer(Pattern.class, new FromStringDeserializer<>(Pattern::compile));

		return module;
	}

	@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@class")
	private interface Typed {
	}

	private abstract static class ScenarioWithoutHook {
		@JsonIgnore
		Object setupHook;
	}

	/**
	 * Writes a relative path with {@code /} between its names, so it means the same on either side; an absolute
	 * path is one side's own and is written as that side spells it.
	 */
	private static final class PathSerializer extends JsonSerializer<Path> {
		@Override
		public void serialize(Path value, JsonGenerator generator, SerializerProvider provider) throws IOException {
			if (value.isAbsolute()) {
				generator.writeString(value.toString());
				return;
			}

			generator.writeString(StreamSupport.stream(value.spliterator(), false).map(Path::toString)
					.collect(Collectors.joining("/")));
		}
	}

	private static final class PathDeserializer extends JsonDeserializer<Path> {
		@Override
		public Path deserialize(JsonParser parser, DeserializationContext context) throws IOException {
			return Path.of(parser.getValueAsString());
		}
	}

	private static final class AddressSerializer extends JsonSerializer<InetSocketAddress> {
		@Override
		public void serialize(InetSocketAddress value, JsonGenerator generator, SerializerProvider provider) throws IOException {
			generator.writeStartObject();
			generator.writeStringField("host", value.getHostString());
			generator.writeNumberField("port", value.getPort());
			generator.writeBooleanField("unresolved", value.isUnresolved());
			generator.writeEndObject();
		}
	}

	private static final class AddressDeserializer extends JsonDeserializer<InetSocketAddress> {
		@Override
		public InetSocketAddress deserialize(JsonParser parser, DeserializationContext context) throws IOException {
			JsonNode node = parser.readValueAsTree();
			String host = node.path("host").asText();
			int port = node.path("port").asInt();
			boolean unresolved = node.path("unresolved").asBoolean();

			return unresolved ? InetSocketAddress.createUnresolved(host, port) : new InetSocketAddress(host, port);
		}
	}

	private static final class ToStringSerializer<T> extends JsonSerializer<T> {
		@Override
		public void serialize(T value, JsonGenerator generator, SerializerProvider provider) throws IOException {
			generator.writeString(value.toString());
		}
	}

	private static final class FromStringDeserializer<T> extends JsonDeserializer<T> {
		private final Function<String, T> parse;

		private FromStringDeserializer(Function<String, T> parse) {
			this.parse = parse;
		}

		@Override
		public T deserialize(JsonParser parser, DeserializationContext context) throws IOException {
			return parse.apply(parser.getValueAsString());
		}
	}
}
