package com.thewinterframework.service;

import com.google.inject.Binder;
import com.thewinterframework.plugin.WinterPlugin;
import com.thewinterframework.service.decorator.ServiceDecorator;
import com.thewinterframework.service.decorator.ServiceDecoratorHandler;
import com.thewinterframework.utils.reflect.Reflections;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * <p> This class is responsible for managing services. </p>
 */
public class ServiceManager {

	// Insertion ordered: handlers are notified in the order their decorators were first discovered.
	private final Map<Class<? extends ServiceDecoratorHandler<?>>, ServiceDecoratorHandler<?>> handlers = new LinkedHashMap<>();
	private final Set<Class<?>> registeredServices = new LinkedHashSet<>();

	// The phases already completed, so a handler created after them (handlers are created lazily) can catch up
	// instead of silently missing the callbacks.
	private WinterPlugin plugin;
	private boolean loaded;
	private boolean enabled;
	private boolean configured;

	/**
	 * Register a service with the service manager.
	 *
	 * @param service The service to register.
	 */
	@SuppressWarnings("unchecked")
	public synchronized void registerService(final @NotNull Class<?> service) throws IllegalAccessException, NoSuchMethodException {
		final var serviceDecorators = Reflections.findClassAnnotations(service, ServiceDecorator.class);
		for (final var decoratorAnnotation : serviceDecorators) {
			final var serviceDecorator = decoratorAnnotation.annotationType().getAnnotation(ServiceDecorator.class);
			final var handler = (ServiceDecoratorHandler<Annotation>) handlers.computeIfAbsent(serviceDecorator.value(), this::createInstance);
			handler.onDiscoverOnType(service, decoratorAnnotation);
		}

		final var methodServiceDecorators = Reflections.findMethodsWith(service, ServiceDecorator.class);
		for (final var serviceDecoratorMethod : methodServiceDecorators) {
			final var decoratorAnnotation = serviceDecoratorMethod.annotation();
			final var handlerClass = decoratorAnnotation.value();

			final var handler = (ServiceDecoratorHandler<Annotation>) handlers.computeIfAbsent(handlerClass, this::createInstance);
			final var annotatedMethod = serviceDecoratorMethod.annotatedWith(handler.getAnnotationType());
			handler.onDiscover(service, annotatedMethod);
		}

		registeredServices.add(service);
	}

	/**
	 * Load all plugin service handlers.
	 *
	 * @param plugin The plugin to load handlers for.
	 */
	public synchronized void loadHandlers(final WinterPlugin plugin) {
		this.plugin = plugin;
		this.loaded = true;

		for (final var handler : handlers.values()) {
			handler.onPluginLoad(plugin);
		}
	}

	/**
	 * Configure all plugin service handlers.
	 *
	 * @param binder The binder to configure handlers with.
	 */
	public synchronized void configureHandlers(final Binder binder) {
		this.configured = true;

		for (final var handler : handlers.values()) {
			handler.onConfigure(binder);
		}
	}

	/**
	 * Start all plugin service handlers.
	 *
	 * @param plugin The plugin to start handlers for.
	 */
	public synchronized void startHandlers(final WinterPlugin plugin) {
		this.plugin = plugin;
		this.enabled = true;

		for (final var handler : handlers.values()) {
			handler.onPluginEnable(plugin);
		}
	}

	/**
	 * Stop all plugin service handlers.
	 *
	 * @param plugin The plugin to stop handlers for.
	 */
	public synchronized void stopHandlers(final WinterPlugin plugin) {
		for (final var handler : handlers.values()) {
			handler.onPluginDisable(plugin);
		}

		this.enabled = false;
	}

	/**
	 * Get a handler by its class.
	 *
	 * @param handlerClass The handler class.
	 * @param <T>          The type of the handler.
	 * @return The handler instance or null if not found.
	 */
	@Nullable
	public synchronized <T extends ServiceDecoratorHandler<?>> T getHandler(final Class<T> handlerClass) {
		final var handler = handlers.get(handlerClass);
		if (handler == null) {
			return null;
		}

		return handlerClass.cast(handler);
	}

	/**
	 * Gets the handler of the given class, creating (and keeping) a new instance when no component used it yet.
	 * <p>
	 * Handlers are normally created lazily, when a component using the decorator is discovered, so asking for a
	 * handler of a decorator that is not used by any component would otherwise return {@code null} even though
	 * the handler is perfectly usable (for instance to have programmatic reload methods registered).
	 *
	 * @param handlerClass The handler class.
	 * @param <T>          The type of the handler.
	 * @return The handler instance, never {@code null}.
	 */
	@NotNull
	public synchronized <T extends ServiceDecoratorHandler<?>> T getOrCreateHandler(final Class<T> handlerClass) {
		final var existing = handlers.get(handlerClass);
		if (existing != null) {
			return handlerClass.cast(existing);
		}

		final var created = createInstance(handlerClass);
		handlers.put(handlerClass, created);
		replayLifeCycle(created);
		return created;
	}

	/**
	 * Replays the phases a handler created after them missed, so a handler that is created on demand (instead of
	 * while scanning the components) behaves exactly like the ones that were discovered up front.
	 *
	 * @param handler the newly created handler
	 */
	private void replayLifeCycle(final ServiceDecoratorHandler<?> handler) {
		if (configured && overridesOnConfigure(handler.getClass())) {
			// The Guice binder is only usable while the injector is being created: bindings requested after that
			// point would be silently dropped, which is far worse to debug than an explicit failure.
			throw new IllegalStateException(
					"Service decorator handler " + handler.getClass().getName() + " was created after the injector was "
							+ "configured and cannot add bindings anymore, make sure at least one component uses its decorator"
			);
		}

		if (loaded && plugin != null) {
			handler.onPluginLoad(plugin);
		}

		if (enabled && plugin != null) {
			handler.onPluginEnable(plugin);
		}
	}

	private static boolean overridesOnConfigure(final Class<?> handlerClass) {
		try {
			return handlerClass.getMethod("onConfigure", Binder.class).getDeclaringClass() != ServiceDecoratorHandler.class;
		} catch (final NoSuchMethodException e) {
			return false;
		}
	}

	/**
	 * Get the services.
	 *
	 * @return The services.
	 */
	public synchronized Set<Class<?>> services() {
		return Collections.unmodifiableSet(new LinkedHashSet<>(registeredServices));
	}

	/**
	 * Creates an instance of the given class.
	 *
	 * @param clazz the class to create an instance of.
	 * @param <T>   the type of the class.
	 * @return an instance of the given class.
	 */
	private <T> T createInstance(final Class<T> clazz) {
		try {
			return clazz.getDeclaredConstructor().newInstance();
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

}
