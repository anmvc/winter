package com.thewinterframework.utils.reflect;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class ProcessorUtils {
	private ProcessorUtils() {
	}

	public enum ProjectType {
		API,
		IMPL
	}

	public static ProjectType getProjectType(final ProcessingEnvironment env) {
		final var typeRaw = env.getOptions().getOrDefault("plugin.type", "IMPL").trim();
		try {
			return ProjectType.valueOf(typeRaw.toUpperCase(Locale.ROOT));
		} catch (final IllegalArgumentException e) {
			throw new IllegalArgumentException(
					"Invalid 'plugin.type' processor option: '" + typeRaw + "', expected API or IMPL",
					e
			);
		}
	}

	/**
	 * Checks whether the given type implements (directly or not) the given interface, or extends the given class.
	 *
	 * @param child            the type to check
	 * @param rootQualifiedName the canonical name of the interface or class to look for
	 * @return {@code true} if the type is a child of the given type
	 */
	public static boolean isChild(final TypeMirror child, final String rootQualifiedName) {
		if (!(child instanceof final DeclaredType declaredType)) {
			return false;
		}

		if (!(declaredType.asElement() instanceof final TypeElement childElement)) {
			return false;
		}

		final var names = new LinkedHashSet<String>();
		collectSuperTypes(childElement, names);
		return names.contains(rootQualifiedName);
	}

	public static boolean isChild(final TypeMirror child, final TypeMirror root) {
		if (!(root instanceof final DeclaredType rootType)) {
			return false;
		}

		if (!(rootType.asElement() instanceof final TypeElement rootElement)) {
			return false;
		}

		return isChild(child, rootElement.getQualifiedName().toString());
	}

	/**
	 * Collects the canonical names of every super type (interfaces and classes, transitively) of the given type.
	 *
	 * @param type  the type to inspect
	 * @param names the set to fill
	 */
	private static void collectSuperTypes(final TypeElement type, final Set<String> names) {
		if (type == null || !names.add(type.getQualifiedName().toString())) {
			return;
		}

		for (final var anInterface : type.getInterfaces()) {
			if (anInterface instanceof final DeclaredType interfaceType
					&& interfaceType.asElement() instanceof final TypeElement interfaceElement) {
				collectSuperTypes(interfaceElement, names);
			}
		}

		if (type.getSuperclass() instanceof final DeclaredType superType
				&& superType.asElement() instanceof final TypeElement superElement) {
			collectSuperTypes(superElement, names);
		}
	}

	public static Element asElement(final ProcessingEnvironment env, final Class<?> type) {
		return env.getElementUtils().getTypeElement(type.getCanonicalName());
	}
}
