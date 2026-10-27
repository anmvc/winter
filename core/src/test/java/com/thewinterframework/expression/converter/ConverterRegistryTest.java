package com.thewinterframework.expression.converter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConverterRegistryTest {

	interface Named {
		String name();
	}

	interface Animal {
		String sound();
	}

	static final class Dog implements Animal, Named {
		@Override
		public String sound() {
			return "woof";
		}

		@Override
		public String name() {
			return "dog";
		}
	}

	@Test
	void anAlreadyAssignableValueIsReturnedAsItIs() {
		final var registry = new ConverterRegistry();
		final var value = new Dog();

		assertSame(value, registry.convert(value, Dog.class));
		assertSame(value, registry.convert(value, Animal.class));
	}

	@Test
	void nullStaysNull() {
		assertNull(new ConverterRegistry().convert(null, String.class));
	}

	@Test
	void convertersOfSuperTypesAreUsed() {
		final var registry = new ConverterRegistry();
		registry.register(Number.class, String.class, number -> "number:" + number);

		assertEquals("number:42", registry.convert(42, String.class));
		assertEquals("number:42.5", registry.convert(42.5, String.class));
	}

	@Test
	void convertersOfImplementedInterfacesAreUsed() {
		final var registry = new ConverterRegistry();
		registry.register(Named.class, String.class, Named::name);

		assertEquals("dog", registry.convert(new Dog(), String.class));
	}

	@Test
	void theClosestConverterWins() {
		final var registry = new ConverterRegistry();
		// The super type converter is registered last on purpose, the resolution must not depend on the
		// registration order.
		registry.register(Integer.class, String.class, value -> "integer");
		registry.register(Number.class, String.class, value -> "number");

		assertEquals("integer", registry.convert(42, String.class));
		assertEquals("number", registry.convert(42.5, String.class));
	}

	@Test
	void aMissingConverterIsReported() {
		final var exception = assertThrows(IllegalArgumentException.class,
				() -> new ConverterRegistry().convert(42, StringBuilder.class));

		assertTrue(exception.getMessage().contains("No converter found"), exception.getMessage());
	}
}
