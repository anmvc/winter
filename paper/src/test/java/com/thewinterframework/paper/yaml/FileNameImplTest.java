package com.thewinterframework.paper.yaml;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileNameImplTest {

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
