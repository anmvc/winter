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

	interface Pet extends Animal {
	}

	static final class Dog implements Animal, Pet, Named {
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
		// Broader types are registered first on purpose: a naive registration-order scan would match Number (or
		// Animal) first instead of picking the most specific converter.
		registry.register(Number.class, String.class, value -> "number");
		registry.register(Integer.class, String.class, value -> "integer");

		assertEquals("integer", registry.convert(42, String.class));
		assertEquals("number", registry.convert(42.5, String.class));

		final var interfaceHierarchy = new ConverterRegistry();
		interfaceHierarchy.register(Animal.class, String.class, value -> "animal");
		interfaceHierarchy.register(Pet.class, String.class, value -> "pet");
		assertEquals("pet", interfaceHierarchy.convert(new Dog(), String.class));

		interfaceHierarchy.register(Dog.class, String.class, value -> "dog-class");
		assertEquals("dog-class", interfaceHierarchy.convert(new Dog(), String.class));
	}

	@Test
	void aMissingConverterIsReported() {
		final var exception = assertThrows(IllegalArgumentException.class,
				() -> new ConverterRegistry().convert(42, StringBuilder.class));

		assertTrue(exception.getMessage().contains("No converter found"), exception.getMessage());
	}
}
