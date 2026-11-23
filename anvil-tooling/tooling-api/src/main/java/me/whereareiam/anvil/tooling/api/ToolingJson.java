package me.whereareiam.anvil.tooling.api;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.LogicalType;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * The JSON form of the tooling protocol's payloads, which both peers of a {@link ToolingSession} connection share: the
 * runner that writes frames and reads requests, and the editor that writes requests and reads frames.
 *
 * <p>A payload is written as its fields name it. The payloads read through fluent accessors, which Jackson does not
 * take for getters, so the form sees each payload's fields and none of its getters. Immutable payloads are read back
 * through their Lombok builders' Jackson binding metadata. A value is read only in the shape it is written: no scalar
 * is coerced into another (no text from a number or a truth value, no integer from a fraction), no enum is read from
 * its ordinal, and no primitive from {@code null}. Each peer's codec keeps its own framing, headers and version checks
 * on top of this form.</p>
 *
 * <pre>{@code
 * JsonMapper mapper = ToolingJson.builder().build();
 * String catalog = mapper.writeValueAsString(session.scenarios());
 * }</pre>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ToolingJson {
	/**
	 * Starts a mapper in the JSON form of the tooling protocol's payloads. A peer adds what is its own, such as a
	 * mix-in, before it builds the mapper.
	 *
	 * @return a builder of a mapper that writes and reads the payloads in that form
	 */
	public static @NotNull JsonMapper.Builder builder() {
		return JsonMapper.builder()
				.visibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
				.visibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE)
				.visibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE)
				.disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
				.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
				.enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
				.enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
				.withCoercionConfig(LogicalType.Textual, textual -> textual
						.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
						.setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
						.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
	}
}
