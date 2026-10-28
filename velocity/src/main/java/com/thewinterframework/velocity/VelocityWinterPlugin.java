package com.thewinterframework.velocity;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.thewinterframework.expression.ExpressionResolver;
import com.thewinterframework.expression.JexlExpressionResolver;
import com.thewinterframework.plugin.DataFolder;
import com.thewinterframework.plugin.WinterPlugin;
import com.thewinterframework.plugin.module.PluginModuleManager;
import com.thewinterframework.plugin.platform.PlatformPluginManager;
import com.thewinterframework.scheduler.PluginScheduler;
import com.thewinterframework.velocity.platform.VelocityPlatform;
import com.thewinterframework.velocity.scheduler.VelocityPluginScheduler;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Base class for Velocity plugins annotated with {@code @WinterBootPlugin} and
 * Velocity's {@code @Plugin}. A concrete plugin must provide an injected
 * constructor accepting Velocity's {@link Injector}. Winter creates a child of
 * that injector during proxy initialization and inherits all platform bindings.
 */
public abstract class VelocityWinterPlugin implements WinterPlugin {

	private final ProxyServer server;
	private final Logger logger;
	private final Path dataDirectory;
	private final Injector velocityInjector;
	protected final PluginModuleManager moduleManager;
	protected final PluginScheduler scheduler;
	protected final PlatformPluginManager platformPluginManager;
	protected ExpressionResolver expressionResolver = new JexlExpressionResolver();
	protected Injector injector;
	private PluginContainer pluginContainer;
	private String resolvedNamespace;
	private boolean started;
	private boolean modulesLoaded;
	private boolean enabled;
	private boolean stopped;

	protected VelocityWinterPlugin(final Injector velocityInjector) {
		this.velocityInjector = requireNonNull(velocityInjector, "velocityInjector");
		this.server = velocityInjector.getInstance(ProxyServer.class);
		this.logger = velocityInjector.getInstance(Logger.class);
		this.dataDirectory = velocityInjector.getInstance(Key.get(Path.class, DataDirectory.class));
		this.moduleManager = new PluginModuleManager(this);
		this.scheduler = new VelocityPluginScheduler(this, server);
		this.platformPluginManager = new VelocityPlatform(server);
	}

	@Subscribe
	public final void onProxyInitialize(final ProxyInitializeEvent event) {
		if (started) {
			return;
		}
		started = true;
		final var startTime = System.currentTimeMillis();
		try {
			if (!moduleManager.scanModules() || !moduleManager.loadModules()) {
				throw new IllegalStateException("Failed to load Winter modules");
			}
			modulesLoaded = true;
			onPluginLoad();
			injector = createInjector();
			injector.injectMembers(this);
			if (!moduleManager.injectModules() || !moduleManager.enableModules()) {
				throw new IllegalStateException("Failed to enable Winter modules");
			}
			enabled = true;
			onPluginEnable();
			logger.info("Plugin enabled in {}ms", System.currentTimeMillis() - startTime);
		} catch (final RuntimeException | LinkageError e) {
			logger.error("Failed to initialize Winter plugin", e);
			try {
				shutdown();
			} catch (final RuntimeException | LinkageError cleanupFailure) {
				e.addSuppressed(cleanupFailure);
			}
			throw e;
		}
	}

	@Subscribe(priority = Short.MAX_VALUE)
	public final void onProxyShutdown(final ProxyShutdownEvent event) {
		shutdown();
	}

	private void shutdown() {
		if (!started || stopped) {
			return;
		}
		stopped = true;
		try {
			// The disable phase does not need the injector, and it is also the call that releases the manager, so it
			// must run even when the injector could not be created.
			if (modulesLoaded && !moduleManager.disableModules()) {
				logger.error("Failed to disable Winter modules");
			}
			if (enabled) {
				onPluginDisable();
			}
		} finally {
			try {
				scheduler.cancelAllTasks();
			} finally {
				server.getEventManager().unregisterListeners(this);
				enabled = false;
			}
		}
	}

	protected Injector createInjector() {
		return velocityInjector.createChildInjector(getGuiceModules());
	}

	protected List<Module> getGuiceModules() {
		final var modules = new ArrayList<Module>();
		modules.add(binder -> {
			binder.bind(WinterPlugin.class).toInstance(this);
			binder.bind(VelocityWinterPlugin.class).toInstance(this);
			binder.bind(Path.class).annotatedWith(DataFolder.class).toInstance(dataDirectory);
			binder.bind(PluginScheduler.class).toInstance(scheduler);
			binder.bind(PlatformPluginManager.class).toInstance(platformPluginManager);
			binder.bind(ExpressionResolver.class).toInstance(expressionResolver);
		});
		modules.addAll(moduleManager.asGuiceModules());
		modules.add(this);
		return modules;
	}

	/** Returns the original injector supplied to the plugin by Velocity. */
	public final Injector getVelocityInjector() {
		return velocityInjector;
	}

	public final ProxyServer getServer() {
		return server;
	}

	public final Path getDataDirectory() {
		return dataDirectory;
	}

	/**
	 * Returns the Velocity container of this plugin.
	 * <p>
	 * The container is read from the injector Velocity created for the plugin, where it is always bound, instead of
	 * relying only on {@link com.velocitypowered.api.plugin.PluginManager#fromInstance(Object)}. The identity lookup
	 * misses every instance the proxy did not create itself, which is common while testing or embedding Winter, and it
	 * throws as soon as the proxy has not registered the instance yet.
	 *
	 * @return the plugin container
	 * @throws IllegalStateException if the plugin is not known to Velocity at all
	 */
	public final PluginContainer getPluginContainer() {
		final var cached = pluginContainer;
		if (cached != null) {
			return cached;
		}

		final var container = injectedPluginContainer()
				.or(() -> server.getPluginManager().fromInstance(this))
				.or(() -> annotatedPluginId().flatMap(server.getPluginManager()::getPlugin))
				.orElseThrow(() -> new IllegalStateException("The plugin has not been registered with Velocity"));

		pluginContainer = container;
		return container;
	}

	/**
	 * Reads the {@link PluginContainer} bound by Velocity in the plugin injector.
	 *
	 * @return the bound container, if Velocity provided one
	 */
	private Optional<PluginContainer> injectedPluginContainer() {
		if (velocityInjector.getExistingBinding(Key.get(PluginContainer.class)) == null) {
			return Optional.empty();
		}

		return Optional.ofNullable(velocityInjector.getInstance(PluginContainer.class));
	}

	/**
	 * Reads the plugin ID from the {@link Plugin} annotation of the plugin class.
	 *
	 * @return the annotated plugin ID, if the class declares one
	 */
	private Optional<String> annotatedPluginId() {
		for (var type = getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
			final var annotation = type.getAnnotation(Plugin.class);

			if (annotation != null && !annotation.id().isBlank()) {
				return Optional.of(annotation.id());
			}
		}

		return Optional.empty();
	}

	@Override
	public final Logger getSLF4JLogger() {
		return logger;
	}

	@Override
	public final PluginModuleManager getModuleManager() {
		return moduleManager;
	}

	/** Returns Winter's child injector, or {@code null} before initialization. */
	@Override
	public Injector getInjector() {
		return injector;
	}

	@Override
	public PluginScheduler getScheduler() {
		return scheduler;
	}

	@Override
	public PlatformPluginManager getPlatformManager() {
		return platformPluginManager;
	}

	@Override
	public ExpressionResolver getExpressionResolver() {
		return expressionResolver;
	}

	@Override
	public void setExpressionResolver(final ExpressionResolver resolver) {
		this.expressionResolver = requireNonNull(resolver, "resolver");
	}

	/**
	 * Returns the plugin ID, which Winter uses as the plugin namespace.
	 * <p>
	 * The {@link Plugin} annotation is authoritative and is read without querying the proxy, therefore the namespace is
	 * available before the plugin is registered, during {@code onPluginLoad()} and inside unit tests.
	 */
	@Override
	public String namespace() {
		final var cached = resolvedNamespace;
		if (cached != null) {
			return cached;
		}

		final var namespace = annotatedPluginId().orElseGet(() -> getPluginContainer().getDescription().getId());
		resolvedNamespace = namespace;
		return namespace;
	}
}
