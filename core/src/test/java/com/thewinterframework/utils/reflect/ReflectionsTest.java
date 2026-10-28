package com.thewinterframework.utils.reflect;

import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflectionsTest {

	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
	@interface Marker {
		String value() default "";
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	@Marker("type")
	@interface TypeMarker {
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	@Marker("meta")
	@interface MetaMarker {
	}

	static class Parent {

		@Marker("parent")
		public void overridden() {
		}

		@Marker("parent")
		public void unannotatedOverride() {
		}

		@Marker("inherited")
		public String parentOnly() {
			return "from-parent";
		}

		@Marker("parent-private")
		private void privateHook(final List<String> calls) {
			calls.add("parent-private");
		}

		@Marker
		public static void staticMethod() {
		}
	}

	static class Child extends Parent {

		@Override
		@Marker("child")
		public void overridden() {
		}

		@Override
		public void unannotatedOverride() {
		}

		@Marker("child-private")
		private void privateHook(final List<String> calls) {
			calls.add("child-private");
		}

		@MetaMarker
		public void metaAnnotated() {
		}

		public void notAnnotated() {
		}
	}

	@Test
	void overriddenMethodsUseTheMostDerivedDeclaration() throws Exception {
		final var methods = Reflections.findMethodsWith(Child.class, Marker.class);

		final var overridden = methods.stream().filter(m -> m.reflect().getName().equals("overridden")).toList();
		assertEquals(1, overridden.size());
		assertEquals(Child.class, overridden.getFirst().reflect().getDeclaringClass());
		assertEquals("child", overridden.getFirst().annotation().value());

		// Overriding a method without repeating the annotation suppresses the parent declaration.
		assertFalse(methods.stream().anyMatch(m -> m.reflect().getName().equals("unannotatedOverride")));
	}

	@Test
	void inheritedAndPrivateSuperclassMethodsAreFoundAndInvocable() throws Throwable {
		final var methods = Reflections.findMethodsWith(Child.class, Marker.class);
		final var parentOnly = methods.stream()
				.filter(m -> m.reflect().getName().equals("parentOnly"))
				.findFirst();

		assertTrue(parentOnly.isPresent(), methods.toString());
		assertEquals("from-parent", parentOnly.get().handle().invokeWithArguments(new Child()));

		// Private methods are not virtual overrides, so both Child and Parent hooks must remain discoverable.
		final var privateHooks = methods.stream()
				.filter(m -> m.reflect().getName().equals("privateHook"))
				.toList();
		assertEquals(2, privateHooks.size());

		final var calls = new ArrayList<String>();
		final var instance = new Child();
		for (final var hook : privateHooks) {
			hook.handle().invokeWithArguments(instance, calls);
		}
		assertEquals(List.of("child-private", "parent-private"), calls);
	}

	@Test
	void staticMethodsAreIgnored() throws Exception {
		final var methods = Reflections.findMethodsWith(Child.class, Marker.class);
		final var names = methods.stream().map(m -> m.reflect().getName()).toList();

		assertFalse(names.contains("staticMethod"), names.toString());
	}

	@Test
	void metaAnnotationsAreResolved() throws Exception {
		final var methods = Reflections.findMethodsWith(Child.class, Marker.class);
		final var meta = methods.stream()
				.filter(m -> m.reflect().getName().equals("metaAnnotated"))
				.findFirst();

		assertTrue(meta.isPresent(), methods.toString());
		// The handle exposes the meta annotation, the actual annotation is retrieved with annotatedWith(...).
		assertEquals("meta", meta.get().annotation().value());
		assertInstanceOf(MetaMarker.class, meta.get().annotatedWith(MetaMarker.class).annotation());
	}

	@Test
	void methodsWithoutTheAnnotationAreIgnored() throws Exception {
		final var methods = Reflections.findMethodsWith(Child.class, Marker.class);
		final var names = methods.stream().map(m -> m.reflect().getName()).toList();

		assertFalse(names.contains("notAnnotated"), names.toString());
	}

	@TypeMarker
	static class AnnotatedChild extends Child {
	}

	@Test
	void classAnnotationsAreResolvedThroughTheirMetaAnnotation() {
		final var annotations = Reflections.findClassAnnotations(AnnotatedChild.class, Marker.class);

		assertEquals(1, annotations.size());
		assertInstanceOf(TypeMarker.class, annotations.getFirst());
	}

	@Test
	void classesWithoutTheMetaAnnotationHaveNoAnnotations() {
		assertEquals(0, Reflections.findClassAnnotations(Child.class, Marker.class).size());
	}
}
