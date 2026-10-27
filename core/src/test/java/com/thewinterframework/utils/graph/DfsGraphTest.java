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

		assertEquals(List.of("b", "a", "c"), graph.ordered());
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
