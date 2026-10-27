package com.thewinterframework.plugin.module;

import com.google.inject.Module;
import com.thewinterframework.component.meta.ComponentDescriptor;
import com.thewinterframework.plugin.WinterPlugin;
import com.thewinterframework.processor.WinterProcessor;
import com.thewinterframework.utils.graph.DfsGraph;
import com.thewinterframework.wire.condition.Conditions;
import com.thewinterframework.wire.condition.context.ConditionContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Discovers and executes plugin modules in dependency order. */
public class PluginModuleManager implements Iterable<Class<? extends PluginModule>> {

	private final WinterPlugin plugin;
	private final Logger logger;
	private final Map<Class<? extends PluginModule>, PluginModule> registeredModules = new LinkedHashMap<>();
	private final DfsGraph<Class<? extends PluginModule>> moduleGraph = new DfsGraph<>();
	private final Set<Class<? extends PluginModule>> expandedModules = new HashSet<>();
	private final Set<Class<? extends PluginModule>> expandingModules = new HashSet<>();
	/** Modules whose load phase completed. */
	private final Set<Class<? extends PluginModule>> loadedModules = ConcurrentHashMap.newKeySet();
	/** Modules whose enable phase completed. */
	private final Set<Class<? extends PluginModule>> enabledModules = ConcurrentHashMap.newKeySet();
	private ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
	private boolean alreadyLoaded;

	public PluginModuleManager(final WinterPlugin plugin) {
		this.plugin = plugin;
		this.logger = plugin.getSLF4JLogger();
	}

	@SuppressWarnings("unchecked")
	public boolean scanModules() {
		if (alreadyLoaded) {
			return false;
		}

		try {
			final var providers = WinterProcessor.getModuleProvider(plugin).getWiredClasses();
			for (final var provider : providers) {
				if (!registerModule((Class<? extends PluginModule>) provider)) {
					logger.error("Failed to register module {}", provider.getCanonicalName());
					return false;
				}
			}
			logger.info("Scanned {} modules", providers.size());
			return true;
		} catch (final Exception exception) {
			logger.error("Failed to scan modules", exception);
			return false;
		}
	}

	@SuppressWarnings("unchecked")
	public boolean registerModule(final Class<? extends PluginModule> moduleType) {
		if (alreadyLoaded) {
			return false;
		}

		if (!loadStageConditionsMatch(moduleType)) {
			// A module that is not active during the load stage must not be registered, otherwise its dependencies and
			// its bindings would be loaded unconditionally.
			logger.debug("Skipped LOAD (condition failed) for {}", moduleType.getSimpleName());
			return true;
		}

		var module = registeredModules.get(moduleType);
		if (module == null) {
			module = createInstance(moduleType);
			if (module == null) {
				return false;
			}
			registeredModules.put(moduleType, module);
			moduleGraph.addNode(moduleType);
		}

		if (expandedModules.contains(moduleType) || !expandingModules.add(moduleType)) {
			return true;
		}

		try {
			for (final var dependency : module.depends(plugin)) {
				if (!registerModule(dependency)) {
					return registrationFailure(dependency, moduleType);
				}
				moduleGraph.addAfter(moduleType, dependency);
			}

			for (final var after : module.before(plugin)) {
				if (!registerModule(after)) {
					return registrationFailure(after, moduleType);
				}
				moduleGraph.addBefore(moduleType, after);
			}

			if (ComponentDescriptor.isComponent(moduleType)) {
				final var descriptor = ComponentDescriptor.from(moduleType);
				for (final var dependency : descriptor.after()) {
					if (PluginModule.class.isAssignableFrom(dependency)) {
						final var dependencyType = (Class<? extends PluginModule>) dependency;
						if (!registerModule(dependencyType)) {
							return registrationFailure(dependencyType, moduleType);
						}
						moduleGraph.addAfter(moduleType, dependencyType);
					}
				}
				for (final var after : descriptor.before()) {
					if (PluginModule.class.isAssignableFrom(after)) {
						final var afterType = (Class<? extends PluginModule>) after;
						if (!registerModule(afterType)) {
							return registrationFailure(afterType, moduleType);
						}
						moduleGraph.addBefore(moduleType, afterType);
					}
				}
			}

			expandedModules.add(moduleType);
			return true;
		} finally {
			expandingModules.remove(moduleType);
		}
	}

	public boolean loadModules() {
		if (!runModules(ModulePhase.LOAD, orderedModules())) {
			return false;
		}
		alreadyLoaded = true;
		return true;
	}

	public boolean injectModules() {
		if (!alreadyLoaded) {
			return false;
		}
		final var start = System.currentTimeMillis();
		for (final var moduleType : orderedModules()) {
			plugin.getInjector().injectMembers(registeredModules.get(moduleType));
		}
		logger.info("Injected modules in {}ms", System.currentTimeMillis() - start);
		return true;
	}

	public boolean enableModules() {
		return alreadyLoaded && runModules(ModulePhase.ENABLE, orderedModules());
	}

	public boolean disableModules() {
		if (!alreadyLoaded) {
			return false;
		}

		// Modules that never completed their enable phase have nothing to tear down, but a plugin whose enable phase
		// failed halfway through still has the modules that did complete, so those are the ones to disable.
		final var completed = enabledModules.isEmpty() ? loadedModules : enabledModules;
		final var reverse = orderedModules().stream()
				.filter(completed::contains)
				.collect(java.util.stream.Collectors.toCollection(ArrayList::new));
		Collections.reverse(reverse);

		final var result = runModules(ModulePhase.DISABLE, reverse);
		executor.close();

		// The platform can enable the plugin again (for instance after a /reload), so the manager has to be able to
		// walk through its life cycle once more instead of reporting every phase as already executed.
		alreadyLoaded = false;
		loadedModules.clear();
		enabledModules.clear();
		expandedModules.clear();
		executor = Executors.newVirtualThreadPerTaskExecutor();
		return result;
	}

	public boolean isRegistered(final Class<? extends PluginModule> type) {
		return registeredModules.containsKey(type);
	}

	@Nullable
	public <T extends PluginModule> T getModule(final Class<T> type) {
		return type.cast(registeredModules.get(type));
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	public Set<Module> asGuiceModules() {
		final Set<Module> modules = new LinkedHashSet<>(registeredModules.values());
		// The manager owns the module instances, so the injector has to hand out those instances instead of creating a
		// second one through a JIT binding whose life cycle nobody drives.
		modules.add(binder -> {
			for (final var entry : registeredModules.entrySet()) {
				binder.bind((Class) entry.getKey()).toInstance(entry.getValue());
			}
		});
		return Collections.unmodifiableSet(modules);
	}

	@NotNull
	@Override
	public Iterator<Class<? extends PluginModule>> iterator() {
		return orderedModules().iterator();
	}

	private List<Class<? extends PluginModule>> orderedModules() {
		return moduleGraph.ordered(Comparator
				.comparingInt(this::moduleOrder)
				.thenComparing(Class::getName));
	}

	private int moduleOrder(final Class<? extends PluginModule> type) {
		return ComponentDescriptor.isComponent(type) ? ComponentDescriptor.from(type).order() : 0;
	}

	private boolean isAsync(final Class<? extends PluginModule> type) {
		return ComponentDescriptor.isComponent(type) && ComponentDescriptor.from(type).async();
	}

	private boolean runModules(
			final ModulePhase phase,
			final List<Class<? extends PluginModule>> ordered
	) {
		final var start = System.currentTimeMillis();
		final var completion = new HashMap<Class<? extends PluginModule>, CompletableFuture<Boolean>>();

		try {
			for (final var moduleType : ordered) {
				final var dependencies = moduleGraph.dependenciesOf(moduleType).stream()
						.map(completion::get)
						.filter(java.util.Objects::nonNull)
						.toArray(CompletableFuture[]::new);
				final var ready = CompletableFuture.allOf(dependencies);
				final CompletableFuture<Boolean> result;
				if (isAsync(moduleType) && phase != ModulePhase.DISABLE) {
					result = ready.thenApplyAsync(
							unused -> dependenciesSucceeded(dependencies) && invokeModule(phase, moduleType),
							executor
					);
				} else {
					ready.join();
					result = CompletableFuture.completedFuture(
							dependenciesSucceeded(dependencies) && invokeModule(phase, moduleType)
					);
				}
				completion.put(moduleType, result);
			}

			final var successful = CompletableFuture.allOf(completion.values().toArray(CompletableFuture[]::new))
					.thenApply(unused -> completion.values().stream().allMatch(CompletableFuture::join))
					.join();
			logger.info("{} modules in {}ms", phase.pastTense, System.currentTimeMillis() - start);
			return successful;
		} catch (final CompletionException exception) {
			logger.error("Failed to {} modules", phase.action, exception.getCause());
			return false;
		}
	}

	private boolean dependenciesSucceeded(final CompletableFuture<?>[] dependencies) {
		for (final var dependency : dependencies) {
			if (!Boolean.TRUE.equals(dependency.join())) {
				return false;
			}
		}
		return true;
	}

	private boolean invokeModule(final ModulePhase phase, final Class<? extends PluginModule> moduleType) {
		final var module = registeredModules.get(moduleType);
		if (module == null) {
			throw new IllegalStateException("Module not registered: " + moduleType.getName());
		}

		try {
			final var successful = switch (phase) {
				case LOAD -> module.onLoad(plugin);
				case ENABLE -> module.onEnable(plugin);
				case DISABLE -> module.onDisable(plugin);
			};
			if (!successful) {
				logger.error("Module '{}' failed to {}", moduleType.getSimpleName(), phase.action);
				return false;
			}

			// Tracking the completed phases lets the manager tear down exactly the modules that were started, even
			// when the enable phase failed halfway through.
			switch (phase) {
				case LOAD -> loadedModules.add(moduleType);
				case ENABLE -> enabledModules.add(moduleType);
				case DISABLE -> {
					loadedModules.remove(moduleType);
					enabledModules.remove(moduleType);
				}
			}
			logger.info("Module '{}' {}", moduleType.getSimpleName(), phase.pastTense);
			return true;
		} catch (final Exception exception) {
			throw new CompletionException("Failed to " + phase.action + " module " + moduleType.getName(), exception);
		}
	}

	/**
	 * Evaluates the load stage conditions of a module.
	 *
	 * @param moduleType the module class
	 * @return {@code true} when the module is active during the load stage
	 */
	private boolean loadStageConditionsMatch(final Class<? extends PluginModule> moduleType) {
		try {
			return Conditions.matches(
					moduleType,
					Stage.LOAD,
					new ConditionContext(plugin, moduleType),
					null
			);
		} catch (final Exception exception) {
			logger.error("Failed to evaluate the load conditions of module {}", moduleType.getName(), exception);
			return false;
		}
	}

	private boolean registrationFailure(
			final Class<? extends PluginModule> dependency,
			final Class<? extends PluginModule> owner
	) {
		logger.error("Failed to register module {} required by {}", dependency.getCanonicalName(), owner.getCanonicalName());
		return false;
	}

	private <T> T createInstance(final Class<T> type) {
		try {
			return type.getDeclaredConstructor().newInstance();
		} catch (final Exception exception) {
			logger.error("Failed to create module {}", type.getName(), exception);
			return null;
		}
	}

	private enum ModulePhase {
		LOAD("load", "loaded"),
		ENABLE("enable", "enabled"),
		DISABLE("disable", "disabled");

		private final String action;
		private final String pastTense;

		ModulePhase(final String action, final String pastTense) {
			this.action = action;
			this.pastTense = pastTense;
		}
	}
}
