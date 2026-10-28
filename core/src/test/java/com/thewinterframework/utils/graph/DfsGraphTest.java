package com.thewinterframework.utils.graph;

import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DfsGraphTest {

	@Test
	void dependencyIsAlwaysIteratedFirst() {
		final var graph = new DfsGraph<String>();
		graph.addAfter("module", "dependency");

		assertEquals(List.of("dependency", "module"), graph.ordered());
	}

	@Test
	void beforeIsAlwaysIteratedFirst() {
		final var graph = new DfsGraph<String>();
		// Registered in the opposite order so insertion order alone cannot satisfy the assertion.
		graph.addNode("second");
		graph.addBefore("first", "second");

		assertEquals(List.of("first", "second"), graph.ordered());
	}

	@Test
	void iterationUsesTheTopologicalOrder() {
		final var graph = new DfsGraph<String>();
		graph.addAfter("storage", "database");
		graph.addAfter("database", "configuration");

		final var order = List.copyOf(iterableToList(graph));

		assertEquals(List.of("configuration", "database", "storage"), order);
	}

	@Test
	void nodesWithoutDependenciesKeepTheirInsertionOrder() {
		final var graph = new DfsGraph<String>();
		graph.addNode("b");
		graph.addNode("a");
		graph.addNode("c");

		// The default order is stable: independent nodes are returned in the order they were added.
		assertEquals(List.of("b", "a", "c"), graph.ordered());
	}

	@Test
	void aCustomComparatorDecidesBetweenIndependentNodes() {
		final var graph = new DfsGraph<String>();
		graph.addNode("z");
		graph.addAfter("b", "m");
		graph.addNode("a");

		// Insertion order keeps z, m, a and appends b once m completes; natural order reorders both the initial
		// roots (a before m before z) and the newly ready dependent (b before z).
		assertEquals(List.of("z", "m", "a", "b"), graph.ordered());
		assertEquals(List.of("a", "m", "b", "z"), graph.ordered(Comparator.naturalOrder()));
	}

	@Test
	void dependenciesOfReturnsTheDirectDependencies() {
		final var graph = new DfsGraph<String>();
		graph.addAfter("module", "database");
		graph.addAfter("module", "configuration");
		graph.addAfter("database", "driver");

		final var direct = graph.dependenciesOf("module");
		assertEquals(Set.of("database", "configuration"), direct);
		assertThrows(UnsupportedOperationException.class, () -> direct.add("extra"));
		assertTrue(graph.dependenciesOf("configuration").isEmpty());
	}

	@Test
	void cyclesAreReportedInsteadOfLoopingForever() {
		final var graph = new DfsGraph<String>();
		graph.addAfter("a", "b");
		graph.addAfter("b", "a");

		final var exception = assertThrows(IllegalStateException.class, graph::ordered);
		assertTrue(exception.getMessage().contains("Cycle detected"), exception.getMessage());
	}

	@Test
	void aSelfDependencyIsACycle() {
		final var graph = new DfsGraph<String>();
		graph.addAfter("a", "a");

		assertThrows(IllegalStateException.class, graph::ordered);
	}

	private static List<String> iterableToList(final Iterable<String> iterable) {
		final var list = new java.util.ArrayList<String>();
		iterable.forEach(list::add);
		return list;
	}
}
