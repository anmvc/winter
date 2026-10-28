package com.thewinterframework.utils.reflect;

import org.junit.jupiter.api.Test;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessorUtilsTest {

	@Test
	void theProjectTypeDefaultsToImplementation() {
		assertEquals(ProcessorUtils.ProjectType.IMPL, ProcessorUtils.getProjectType(environment(Map.of())));
	}

	@Test
	void theProjectTypeIsReadFromTheProcessorOptions() {
		assertEquals(ProcessorUtils.ProjectType.API, ProcessorUtils.getProjectType(environment(Map.of("plugin.type", "API"))));
		assertEquals(ProcessorUtils.ProjectType.IMPL, ProcessorUtils.getProjectType(environment(Map.of("plugin.type", "IMPL"))));
	}

	@Test
	void theProjectTypeIsCaseAndWhitespaceInsensitive() {
		assertEquals(ProcessorUtils.ProjectType.API, ProcessorUtils.getProjectType(environment(Map.of("plugin.type", " api "))));
		assertEquals(ProcessorUtils.ProjectType.IMPL, ProcessorUtils.getProjectType(environment(Map.of("plugin.type", "impl"))));
	}

	@Test
	void anUnknownProjectTypeIsReported() {
		final var exception = assertThrows(IllegalArgumentException.class,
				() -> ProcessorUtils.getProjectType(environment(Map.of("plugin.type", "server"))));

		assertTrue(exception.getMessage().contains("plugin.type"), exception.getMessage());
		assertTrue(exception.getMessage().contains("API"), exception.getMessage());
	}

	private static ProcessingEnvironment environment(final Map<String, String> options) {
		final var copy = new HashMap<>(options);

		return new ProcessingEnvironment() {
			@Override
			public Map<String, String> getOptions() {
				return copy;
			}

			@Override
			public Messager getMessager() {
				throw new UnsupportedOperationException();
			}

			@Override
			public Filer getFiler() {
				throw new UnsupportedOperationException();
			}

			@Override
			public Elements getElementUtils() {
				throw new UnsupportedOperationException();
			}

			@Override
			public Types getTypeUtils() {
				throw new UnsupportedOperationException();
			}

			@Override
			public SourceVersion getSourceVersion() {
				return SourceVersion.latest();
			}

			@Override
			public Locale getLocale() {
				return Locale.ROOT;
			}
		};
	}
}
