package com.thewinterframework.utils.graph;

import org.junit.jupiter.api.Test;

import java.util.List;

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
		graph.addAfter("module", "dependency");
		graph.addNode("z");
		graph.addNode("a");

		assertEquals(List.of("dependency", "module", "z", "a"), graph.ordered((left, right) -> {
			final var rank = List.of("dependency", "module", "z", "a");
			return Integer.compare(rank.indexOf(left), rank.indexOf(right));
		}));
	}

	@Test
	void dependenciesOfReturnsTheDirectDependencies() {
		final var graph = new DfsGraph<String>();
		graph.addAfter("module", "database");
		graph.addAfter("module", "configuration");

		assertEquals(java.util.Set.of("database", "configuration"), graph.dependenciesOf("module"));
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
