package com.thewinterframework.provider;

import com.google.inject.Binder;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.thewinterframework.provider.annotation.ProviderComponent;
import com.thewinterframework.wire.module.AbstractProcessorModule;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.TypeVariable;

public class ProviderModule extends AbstractProcessorModule {
	public ProviderModule() {
		super(ProviderComponent.class);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void configure(final Binder binder) {
		binder.bindScope(ProviderComponent.class, Scopes.SINGLETON);
		// Note: the components are kept, the enable and disable phases of this module still iterate over them.
		for (final var providerClass : this.activeComponents) {
			bindProvider(binder, (Class<? extends Provider<Object>>) providerClass);
		}
	}

	private static <O, T extends Provider<O>> void bindProvider(final @NotNull Binder binder, final Class<T> providerClass) {
		final var typeLiteral = resolveTypeLiteral(providerClass);
		binder.bind(typeLiteral).toProvider(providerClass);
	}

	/**
	 * Resolves the type provided by the given provider class.
	 * <p>
	 * Both {@code implements Provider<Foo>} and {@code extends SomeBaseProvider<Foo>} are supported, Guice takes
	 * care of resolving the type arguments through the class hierarchy.
	 *
	 * @param providerClass the provider class
	 * @param <T>           the type provided by the provider
	 * @return the type literal of the provided type
	 * @throws IllegalStateException if the provided type cannot be resolved
	 */
	@SuppressWarnings("unchecked")
	private static <T> TypeLiteral<T> resolveTypeLiteral(final @NotNull Class<? extends Provider<T>> providerClass) {
		try {
			final var providerType = TypeLiteral.get(providerClass).getSupertype(Provider.class);
			if (providerType.getType() instanceof final ParameterizedType parameterizedType) {
				final var providedType = parameterizedType.getActualTypeArguments()[0];
				if (!(providedType instanceof TypeVariable<?>)) {
					return (TypeLiteral<T>) TypeLiteral.get(providedType);
				}
			}
		} catch (final RuntimeException e) {
			throw new IllegalStateException("Could not resolve the provided type of " + providerClass.getName(), e);
		}

		throw new IllegalStateException(
				"Could not resolve the provided type of " + providerClass.getName()
						+ ", make sure it implements Provider with a concrete type argument"
		);
	}
}
