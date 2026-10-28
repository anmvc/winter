package com.thewinterframework.plugin.module;

import com.google.inject.Guice;
import com.thewinterframework.plugin.WinterPlugin;
import com.thewinterframework.plugin.platform.PlatformPluginManager;
import com.thewinterframework.wire.condition.common.plugin.RequiresPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginModuleManagerTest {

	static final List<String> CALLS = new ArrayList<>();

	@BeforeEach
	void clearCalls() {
		CALLS.clear();
	}

	public static class RecordingModule implements PluginModule {
		@Override
		public boolean onLoad(final WinterPlugin plugin) {
			CALLS.add("load:" + getClass().getSimpleName());
			return true;
		}

		@Override
		public boolean onEnable(final WinterPlugin plugin) {
			CALLS.add("enable:" + getClass().getSimpleName());
			return true;
		}

		@Override
		public boolean onDisable(final WinterPlugin plugin) {
			CALLS.add("disable:" + getClass().getSimpleName());
			return true;
		}
	}

	public static final class FailingEnableModule extends RecordingModule {
		@Override
		public boolean onEnable(final WinterPlugin plugin) {
			CALLS.add("enable:" + getClass().getSimpleName());
			return false;
		}
	}

	public static final class FailingDependentModule extends RecordingModule {
		@Override
		public List<Class<? extends PluginModule>> depends(final WinterPlugin plugin) {
			return List.of(RecordingModule.class);
		}

		@Override
		public boolean onEnable(final WinterPlugin plugin) {
			CALLS.add("enable:" + getClass().getSimpleName());
			return false;
		}
	}

	public static final class DownstreamModule extends RecordingModule {
		@Override
		public List<Class<? extends PluginModule>> depends(final WinterPlugin plugin) {
			return List.of(FailingDependentModule.class);
		}
	}

	@RequiresPlugin("missing")
	public static final class MissingDependencyModule extends RecordingModule {
		public MissingDependencyModule() {
			throw new AssertionError("Skipped module must not be instantiated");
		}
	}

	public static final class OptionalConsumerModule extends RecordingModule {
		@Override
		public List<Class<? extends PluginModule>> depends(final WinterPlugin plugin) {
			return List.of(MissingDependencyModule.class);
		}
	}

	@Test
	void theInjectorHandsOutTheManagedModuleInstances() {
		final var manager = new PluginModuleManager(plugin());
		assertTrue(manager.registerModule(RecordingModule.class));

		final var injector = Guice.createInjector(manager.asGuiceModules());

		// A JIT binding would create a second instance whose life cycle is never driven by the manager.
		assertSame(manager.getModule(RecordingModule.class), injector.getInstance(RecordingModule.class));
	}

	@Test
	void modulesCanGoThroughTheLifeCycleAgainAfterTheyWereDisabled() {
		final var manager = new PluginModuleManager(plugin());
		assertTrue(manager.registerModule(RecordingModule.class));

		assertTrue(manager.loadModules());
		assertTrue(manager.injectModules());
		assertTrue(manager.enableModules());
		assertTrue(manager.disableModules());

		// The platform can enable the plugin again (a /reload does exactly that), so every phase has to be runnable.
		assertTrue(manager.loadModules());
		assertTrue(manager.enableModules());
		assertTrue(manager.disableModules());

		assertEquals(List.of(
				"load:RecordingModule", "enable:RecordingModule", "disable:RecordingModule",
				"load:RecordingModule", "enable:RecordingModule", "disable:RecordingModule"
		), CALLS);
	}

	@Test
	void modulesThatWereOnlyLoadedAreNotTornDown() {
		final var manager = new PluginModuleManager(plugin());
		assertTrue(manager.registerModule(RecordingModule.class));
		assertTrue(manager.loadModules());

		// The plugin can fail between the load and the enable phase (creating the injector, for instance), the module
		// was never enabled so tearing it down would run onDisable on a half initialized module.
		assertTrue(manager.disableModules());
		assertEquals(List.of("load:RecordingModule"), CALLS);

		// Releasing the manager must still allow a subsequent clean cycle.
		assertTrue(manager.loadModules());
		assertTrue(manager.enableModules());
		assertTrue(manager.disableModules());
		assertEquals(List.of(
				"load:RecordingModule",
				"load:RecordingModule", "enable:RecordingModule", "disable:RecordingModule"
		), CALLS);
	}

	@Test
	void onlyTheModulesThatCompletedTheirEnablePhaseAreDisabled() {
		final var manager = new PluginModuleManager(plugin());
		assertTrue(manager.registerModule(DownstreamModule.class));
		assertTrue(manager.loadModules());

		assertFalse(manager.enableModules());
		assertTrue(manager.disableModules());

		// RecordingModule completed onEnable before FailingDependentModule failed; DownstreamModule never started.
		assertEquals(List.of(
				"load:RecordingModule", "load:FailingDependentModule", "load:DownstreamModule",
				"enable:RecordingModule", "enable:FailingDependentModule",
				"disable:RecordingModule"
		), CALLS);
	}

	@Test
	void aFailedFirstModuleIsNotDisabledWhenNothingWasEnabled() {
		final var manager = new PluginModuleManager(plugin());
		assertTrue(manager.registerModule(FailingEnableModule.class));
		assertTrue(manager.loadModules());

		assertFalse(manager.enableModules());
		assertTrue(manager.disableModules());

		// Even when enabledModules is empty after enableModules() fails, disableModules() must not fall back to
		// tearing down loaded modules that never completed onEnable.
		assertEquals(List.of("load:FailingEnableModule", "enable:FailingEnableModule"), CALLS);
	}

	@Test
	void skippedConditionalDependenciesDoNotBreakModuleOrdering() {
		final var manager = new PluginModuleManager(plugin());
		assertTrue(manager.registerModule(OptionalConsumerModule.class));
		assertFalse(manager.isRegistered(MissingDependencyModule.class));
		assertNull(manager.getModule(MissingDependencyModule.class));

		assertTrue(manager.loadModules());
		assertTrue(manager.injectModules());
		assertTrue(manager.enableModules());
		assertTrue(manager.disableModules());

		assertEquals(List.of(
				"load:OptionalConsumerModule", "enable:OptionalConsumerModule", "disable:OptionalConsumerModule"
		), CALLS);
	}

	private static WinterPlugin plugin() {
		final PlatformPluginManager platform = name -> false;
		return (WinterPlugin) Proxy.newProxyInstance(
				PluginModuleManagerTest.class.getClassLoader(),
				new Class<?>[]{WinterPlugin.class},
				(proxy, method, args) -> switch (method.getName()) {
					case "toString" -> "TestPlugin";
					case "getSLF4JLogger" -> NOPLogger.NOP_LOGGER;
					case "getInjector" -> Guice.createInjector();
					case "getPlatformManager" -> platform;
					default -> {
						if (method.getReturnType().equals(boolean.class)) {
							yield false;
						}

						yield null;
					}
				}
		);
	}
}
