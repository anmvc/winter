package com.thewinterframework.utils.reflect;

import com.google.inject.BindingAnnotation;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Named;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Utility class for reflection operations.
 */
public class Reflections {

	/**
	 * Finds all methods in a class that are annotated with a specific annotation,
	 * or with an annotation that is itself annotated with that annotation (meta-annotation).
	 *
	 * @param clazz      The class to search in.
	 * @param annotation The annotation or meta-annotation to search for.
	 * @param <T>        The annotation type.
	 * @return A list of annotated method handles.
	 * @throws IllegalAccessException If the method cannot be accessed.
	 * @throws NoSuchMethodException  If the method does not exist.
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Annotation> List<AnnotatedMethodHandle<T>> findMethodsWith(final Class<?> clazz, final Class<T> annotation) throws IllegalAccessException, NoSuchMethodException {
		final var list = new ArrayList<AnnotatedMethodHandle<T>>();
		// Overridden methods must be handled once, using the most derived declaration (annotations on methods
		// are not inherited, a subclass must annotate the method again to be picked up).
		final var seenSignatures = new HashSet<String>();

		for (var current = clazz; current != null && current != Object.class; current = current.getSuperclass()) {
			for (final var method : current.getDeclaredMethods()) {
				if (method.isSynthetic() || method.isBridge() || Modifier.isStatic(method.getModifiers())) {
					// Static methods cannot be invoked through a virtual method handle.
					continue;
				}

				if (!Modifier.isPrivate(method.getModifiers()) && !seenSignatures.add(signature(method))) {
					continue;
				}

				for (final var methodAnnotation : method.getDeclaredAnnotations()) {
					final var annotationType = methodAnnotation.annotationType();
					final var metaAnnotation = annotationType.getAnnotation(annotation);

					if (annotationType.equals(annotation) || metaAnnotation != null) {
						list.add(new AnnotatedMethodHandle<>(
								method,
								MethodHandles.privateLookupIn(method.getDeclaringClass(), MethodHandles.lookup())
										.findVirtual(method.getDeclaringClass(), method.getName(),
												MethodType.methodType(method.getReturnType(), method.getParameterTypes())),
								annotationType.equals(annotation)
										? (T) annotationType.cast(methodAnnotation)
										: metaAnnotation,
								findMethodParameters(method)
						));
					}
				}
			}
		}

		return list;
	}

	/**
	 * Builds a signature that identifies a method regardless of the class declaring it.
	 *
	 * @param method The method.
	 * @return The signature of the method.
	 */
	private static String signature(final Method method) {
		final var builder = new StringBuilder(method.getName()).append('(');
		for (final var parameter : method.getParameterTypes()) {
			builder.append(parameter.getName()).append(',');
		}

		return builder.append(')').toString();
	}

	/**
	 * Finds all annotations of a class that are annotated with a specific meta-annotation.
	 *
	 * @param clazz          The class to get the annotations from.
	 * @param metaAnnotation The meta-annotation to get the annotations from.
	 * @param <T>            The annotation type.
	 * @return The annotations.
	 */
	public static <T extends Annotation> List<Annotation> findClassAnnotations(final Class<?> clazz, final Class<T> metaAnnotation) {
		final var list = new ArrayList<Annotation>();

		for (final var annotation : clazz.getAnnotations()) {
			if (annotation.annotationType().isAnnotationPresent(metaAnnotation)) {
				list.add(annotation);
			}
		}

		return list;
	}

	/**
	 * Finds the parameters of a method.
	 *
	 * @param method The method to find the parameters of.
	 * @return A list of keys representing the parameters.
	 */
	public static List<Key<?>> findMethodParameters(final Method method) {
		final var parameters = method.getGenericParameterTypes();
		final var typeParameters = method.getParameters();
		final var keys = new ArrayList<Key<?>>();

		for (var i = 0; i < parameters.length; i++) {
			final var untypedParameter = typeParameters[i];
			final var typedParameter = parameters[i];

			if (untypedParameter.getAnnotations().length != 0) {
				final var bindingAnnotation = findBindingAnnotation(untypedParameter);
				if (bindingAnnotation != null) {
					keys.add(Key.get(typedParameter, bindingAnnotation));
					continue;
				}
			}

			keys.add(Key.get(typedParameter));
		}

		return keys;
	}

	/**
	 * Injects the keys into the injector and returns the objects.
	 *
	 * @param instance The instance to inject the keys into.
	 * @param keys     The keys to inject.
	 * @param injector The injector to get the instances from.
	 * @return The objects.
	 */
	public static Object[] injectKeys(final Class<?> instance, final List<Key<?>> keys, final Injector injector) {
		final var objects = new Object[keys.size() + 1];
		objects[0] = injector.getInstance(instance);
		for (var i = 0; i < keys.size(); i++) {
			objects[i + 1] = injector.getInstance(keys.get(i));
		}
		return objects;
	}

	/**
	 * Gets the generic type of class that implements an interface.
	 *
	 * @param clazz     The class to get the generic type from.
	 * @param interfaze The interface to get the generic type from.
	 * @param index     The index of the generic type.
	 * @return The generic type.
	 */
	public static Type getGenericType(final Class<?> clazz, final Class<?> interfaze, final int index) {
		for (final var type : clazz.getGenericInterfaces()) {
			if (type instanceof final ParameterizedType parameterizedType) {
				final var rawType = parameterizedType.getRawType();
				if (rawType instanceof Class<?> && interfaze.isAssignableFrom((Class<?>) rawType)) {
					return parameterizedType.getActualTypeArguments()[index];
				}
			}
		}
		return null;
	}

	/**
	 * Converts a type to a class.
	 *
	 * @param type The type to convert.
	 * @return The class.
	 */
	public static Class<?> toClass(final Type type) {
		if (type instanceof Class) {
			return (Class<?>) type;
		} else if (type instanceof ParameterizedType) {
			return (Class<?>) ((ParameterizedType) type).getRawType();
		}
		throw new IllegalArgumentException("Unsupported type: " + type);
	}

	/**
	 * Finds the binding annotation of an element.
	 *
	 * @param element The element to find the binding annotation of.
	 * @return The binding annotation.
	 */
	public static Annotation findBindingAnnotation(final AnnotatedElement element) {
		for (final var annotation : element.getAnnotations()) {
			final var annotationType = annotation.annotationType();
			if (annotationType.equals(Named.class) || annotationType.isAnnotationPresent(BindingAnnotation.class)) {
				return annotation;
			}
		}

		return null;
	}

}
