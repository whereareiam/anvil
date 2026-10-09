package me.whereareiam.anvil.integration.junit;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Finds the one environment a test declares, on its method before its class, through {@link AnvilTest}
 * or an annotation carrying {@link AnvilEnvironment}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ScenarioSelection {
	static @NotNull AnvilScenario scenario(@NotNull Method method, @NotNull Class<?> type) {
		Supplier<AnvilScenario> declared = declared(method);
		if (declared == null) declared = declared(type);
		if (declared == null)
			throw new ExtensionConfigurationException("AnvilExtension requires @AnvilTest or an @AnvilEnvironment annotation");

		return declared.get();
	}

	private static @Nullable Supplier<AnvilScenario> declared(AnnotatedElement element) {
		List<Supplier<AnvilScenario>> declarations = new ArrayList<>();
		AnvilTest fixed = element.getAnnotation(AnvilTest.class);
		if (fixed != null) declarations.add(() -> instantiate(fixed.value()).define());

		for (Annotation annotation : element.getAnnotations()) {
			AnvilEnvironment environment = annotation.annotationType().getAnnotation(AnvilEnvironment.class);
			if (environment != null) declarations.add(() -> create(environment, annotation));
		}

		if (declarations.size() > 1)
			throw new ExtensionConfigurationException(element + " declares several Anvil environments; declare exactly one");

		return declarations.isEmpty() ? null : declarations.getFirst();
	}

	@SuppressWarnings("unchecked")
	private static AnvilScenario create(AnvilEnvironment environment, Annotation declaration) {
		AnvilScenarioFactory<Annotation> factory = (AnvilScenarioFactory<Annotation>) instantiate(environment.value());
		try {
			return factory.create(declaration);
		} catch (ClassCastException mismatch) {
			throw new ExtensionConfigurationException(environment.value().getName() + " does not build scenarios for @"
					+ declaration.annotationType().getSimpleName(), mismatch);
		}
	}

	private static <T> T instantiate(Class<T> type) {
		try {
			return type.getDeclaredConstructor().newInstance();
		} catch (ReflectiveOperationException exception) {
			throw new ExtensionConfigurationException("Could not instantiate " + type.getName()
					+ "; it needs an accessible no-argument constructor", exception);
		}
	}
}
