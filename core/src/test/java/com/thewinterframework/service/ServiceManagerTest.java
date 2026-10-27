package com.thewinterframework.service;

import com.google.inject.Binder;
import com.thewinterframework.plugin.WinterPlugin;
import com.thewinterframework.service.annotation.Service;
import com.thewinterframework.service.decorator.ServiceDecorator;
import com.thewinterframework.service.decorator.ServiceDecoratorHandler;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceManagerTest {

	static final List<String> CALLS = new ArrayList<>();

	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.METHOD, ElementType.TYPE})
	@ServiceDecorator(RecordingHandler.class)
	@interface Recorded {
	}

	public static final class RecordingHandler implements ServiceDecoratorHandler<Recorded> {
		@Override
		public Class<Recorded> getAnnotationType() {
			return Recorded.class;
		}

		@Override
		public void onDiscover(final Class<?> service, final com.thewinterframework.utils.reflect.AnnotatedMethodHandle<Recorded> method) {
			CALLS.add("discover:" + service.getSimpleName() + "#" + method.reflect().getName());
		}

		@Override
		public void onPluginLoad(final WinterPlugin plugin) {
			CALLS.add("load");
		}

		@Override
		public void onPluginEnable(final WinterPlugin plugin) {
			CALLS.add("enable");
		}
	}

	/**
	 * A handler that needs the injector, it can only be created before the binder is configured.
	 */
	public static final class BindingHandler implements ServiceDecoratorHandler<Recorded> {
		@Override
		public Class<Recorded> getAnnotationType() {
			return Recorded.class;
		}

		@Override
		public void onConfigure(final Binder binder) {
			// No-op, the point is that it is overridden.
		}
	}

	@Service
	public static final class SampleService {
		@Recorded
		public void start() {
		}
	}

	@Service
	public static final class UntouchedService {
	}

	@Test
	void discoveredMethodsAreForwardedToTheirHandler() throws Exception {
		CALLS.clear();
		final var manager = new ServiceManager();

		manager.registerService(SampleService.class);

		assertTrue(CALLS.contains("discover:SampleService#start"), CALLS.toString());
		assertEquals(1, manager.services().size());
	}

	@Test
	void handlersAreNotifiedOfThePluginLifeCycle() throws Exception {
		CALLS.clear();
		final var manager = new ServiceManager();
		manager.registerService(SampleService.class);

		final var plugin = plugin();
		manager.loadHandlers(plugin);
		manager.startHandlers(plugin);
		manager.stopHandlers(plugin);

		assertEquals(List.of("discover:SampleService#start", "load", "enable"), CALLS);
	}

	@Test
	void aHandlerCreatedOnDemandIsTheSameInstance() {
		final var manager = new ServiceManager();

		final var first = manager.getOrCreateHandler(RecordingHandler.class);
		final var second = manager.getOrCreateHandler(RecordingHandler.class);

		assertNotNull(first);
		assertSame(first, second);
		assertSame(first, manager.getHandler(RecordingHandler.class));
	}

	@Test
	void aHandlerCreatedAfterTheLoadCatchesUpWithTheLifeCycle() {
		CALLS.clear();
		final var manager = new ServiceManager();
		final var plugin = plugin();

		manager.loadHandlers(plugin);
		manager.startHandlers(plugin);
		// Nothing used the decorator, so the handler is only created now.
		manager.getOrCreateHandler(RecordingHandler.class);

		assertEquals(List.of("load", "enable"), CALLS);
	}

	@Test
	void aHandlerThatNeedsBindingsCannotBeCreatedAfterTheConfiguration() {
		final var manager = new ServiceManager();
		manager.configureHandlers(null);

		final var exception = assertThrows(IllegalStateException.class,
				() -> manager.getOrCreateHandler(BindingHandler.class));

		assertTrue(exception.getMessage().contains(BindingHandler.class.getName()), exception.getMessage());
	}

	@Test
	void resetDropsEverythingThePreviousCycleDiscovered() throws Exception {
		CALLS.clear();
		final var manager = new ServiceManager();
		final var plugin = plugin();
		manager.registerService(SampleService.class);
		manager.loadHandlers(plugin);
		manager.startHandlers(plugin);

		manager.reset();

		// A reload re-runs the discovery on the same manager: keeping the decorators would call onDiscover again on
		// handlers that already registered their commands, listeners or tasks, duplicating them on the next enable.
		assertTrue(manager.services().isEmpty(), manager.services().toString());
		assertNull(manager.getHandler(RecordingHandler.class));

		CALLS.clear();
		manager.registerService(SampleService.class);
		assertEquals(1, manager.services().size());
		assertEquals(List.of("discover:SampleService#start"), CALLS);
	}

	@Test
	void theCollectedServicesAreNotMutable() throws Exception {
		final var manager = new ServiceManager();
		manager.registerService(UntouchedService.class);

		assertThrows(UnsupportedOperationException.class, () -> manager.services().clear());
	}

	private static WinterPlugin plugin() {
		return (WinterPlugin) Proxy.newProxyInstance(
				ServiceManagerTest.class.getClassLoader(),
				new Class<?>[]{WinterPlugin.class},
				(proxy, method, args) -> {
					if (method.getName().equals("toString")) {
						return "TestPlugin";
					}

					if (method.getReturnType().equals(boolean.class)) {
						return false;
					}

					return null;
				}
		);
	}
}
