package com.thewinterframework.plugin.module;

import com.google.inject.Guice;
import com.thewinterframework.plugin.WinterPlugin;
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginModuleManagerTest {

	static final List<String> CALLS = new ArrayList<>();

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
		CALLS.clear();
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
		CALLS.clear();
		final var manager = new PluginModuleManager(plugin());
		assertTrue(manager.registerModule(RecordingModule.class));
		assertTrue(manager.loadModules());

		// The plugin can fail between the load and the enable phase (creating the injector, for instance), the module
		// was never enabled so tearing it down would run onDisable on a half initialized module.
		assertTrue(manager.disableModules());
		assertFalse(CALLS.contains("disable:RecordingModule"), CALLS.toString());
	}

	@Test
	void onlyTheModulesThatCompletedTheirEnablePhaseAreDisabled() {
		CALLS.clear();
		final var manager = new PluginModuleManager(plugin());
		assertTrue(manager.registerModule(RecordingModule.class));
		assertTrue(manager.registerModule(FailingEnableModule.class));
		assertTrue(manager.loadModules());

		assertFalse(manager.enableModules());
		assertTrue(manager.disableModules());

		assertTrue(CALLS.contains("disable:RecordingModule"), CALLS.toString());
		// The module whose enable phase failed was never started, so it must not be told to shut down.
		assertFalse(CALLS.contains("disable:FailingEnableModule"), CALLS.toString());
	}

	private static WinterPlugin plugin() {
		return (WinterPlugin) Proxy.newProxyInstance(
				PluginModuleManagerTest.class.getClassLoader(),
				new Class<?>[]{WinterPlugin.class},
				(proxy, method, args) -> switch (method.getName()) {
					case "toString" -> "TestPlugin";
					case "getSLF4JLogger" -> NOPLogger.NOP_LOGGER;
					case "getInjector" -> Guice.createInjector();
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
