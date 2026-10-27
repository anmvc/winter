package com.thewinterframework.utils.reflect;

import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflectionsTest {

	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
	@interface Marker {
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	@Marker
	@interface TypeMarker {
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	@Marker
	@interface MetaMarker {
	}

	static class Parent {

		@Marker
		public void overridden() {
		}

		@Marker
		public void parentOnly() {
		}

		@Marker
		public static void staticMethod() {
		}
	}

	static class Child extends Parent {

		@Override
		@Marker
		public void overridden() {
		}

		@MetaMarker
		public void metaAnnotated() {
		}

		public void notAnnotated() {
		}
	}

	@Test
	void overriddenMethodsAreFoundOnce() throws Exception {
		final var methods = Reflections.findMethodsWith(Child.class, Marker.class);

		final var overridden = methods.stream().filter(m -> m.reflect().getName().equals("overridden")).count();
		assertEquals(1L, overridden);
	}

	@Test
	void inheritedMethodsAreFound() throws Exception {
		final var methods = Reflections.findMethodsWith(Child.class, Marker.class);
		final var names = methods.stream().map(m -> m.reflect().getName()).toList();

		assertTrue(names.contains("parentOnly"), names.toString());
	}

	@Test
	void staticMethodsAreIgnored() throws Exception {
		final var methods = Reflections.findMethodsWith(Child.class, Marker.class);
		final var names = methods.stream().map(m -> m.reflect().getName()).toList();

		assertTrue(!names.contains("staticMethod"), names.toString());
	}

	@Test
	void metaAnnotationsAreResolved() throws Exception {
		final var methods = Reflections.findMethodsWith(Child.class, Marker.class);
		final var meta = methods.stream()
				.filter(m -> m.reflect().getName().equals("metaAnnotated"))
				.findFirst();

		assertTrue(meta.isPresent(), methods.toString());
		// The handle exposes the meta annotation, the actual annotation is retrieved with annotatedWith(...).
		assertTrue(meta.get().annotation() instanceof Marker);
	}

	@Test
	void methodsWithoutTheAnnotationAreIgnored() throws Exception {
		final var methods = Reflections.findMethodsWith(Child.class, Marker.class);
		final var names = methods.stream().map(m -> m.reflect().getName()).toList();

		assertTrue(!names.contains("notAnnotated"), names.toString());
	}

	@TypeMarker
	static class AnnotatedChild extends Child {
	}

	@Test
	void classAnnotationsAreResolvedThroughTheirMetaAnnotation() {
		final var annotations = Reflections.findClassAnnotations(AnnotatedChild.class, Marker.class);

		assertEquals(1, annotations.size());
		assertTrue(annotations.get(0) instanceof TypeMarker);
	}

	@Test
	void classesWithoutTheMetaAnnotationHaveNoAnnotations() {
		assertEquals(0, Reflections.findClassAnnotations(Child.class, Marker.class).size());
	}
}
