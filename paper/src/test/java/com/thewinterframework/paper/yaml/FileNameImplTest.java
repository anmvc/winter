package com.thewinterframework.paper.yaml;

import com.google.inject.Key;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileNameImplTest {

	@FileName("settings")
	private Object qualified;

	@Test
	void theAnnotationTypeIsFileName() {
		assertEquals(FileName.class, new FileNameImpl("settings").annotationType());
	}

	@Test
	void theValueIsExposed() {
		assertEquals("settings", new FileNameImpl("settings").value());
	}

	@Test
	void equalNamesProduceEqualAnnotations() {
		final var first = new FileNameImpl("settings");
		final var second = new FileNameImpl("settings");

		assertEquals(first, second);
		assertEquals(first.hashCode(), second.hashCode());
	}

	@Test
	void matchesTheRuntimeAnnotationAndGuiceBindingKey() throws Exception {
		// Comparing FileNameImpl only to itself would pass even with record-default equals/hashCode; Guice matches
		// the binding instance against the JVM's runtime @FileName proxy on injection points.
		final var runtime = getClass().getDeclaredField("qualified").getAnnotation(FileName.class);
		final var generated = new FileNameImpl("settings");

		assertEquals(runtime, generated);
		assertEquals(generated, runtime);
		assertEquals(runtime.hashCode(), generated.hashCode());
		assertEquals(Key.get(Object.class, runtime), Key.get(Object.class, generated));
	}

	@Test
	void differentNamesProduceDifferentAnnotations() {
		assertNotEquals(new FileNameImpl("settings"), new FileNameImpl("messages"));
	}

	@Test
	void otherTypesAreNotEqual() {
		assertFalse(new FileNameImpl("settings").equals("settings"));
		assertTrue(new FileNameImpl("settings").equals(new FileNameImpl("settings")));
	}

	@Test
	void theHashCodeFollowsTheAnnotationContract() {
		// A Guice binding annotation whose hashCode does not follow the annotation contract cannot be used as
		// a binding key: the lookup of the annotated key would silently miss the binding.
		final var expected = 127 * "value".hashCode() ^ "settings".hashCode();

		assertEquals(expected, new FileNameImpl("settings").hashCode());
	}
}
