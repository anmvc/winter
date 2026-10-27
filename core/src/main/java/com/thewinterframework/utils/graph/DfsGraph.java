package com.thewinterframework.utils.graph;

import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * A simple directed graph implementation using Depth-First Search (DFS) for topological sorting.
 * This class allows adding nodes and dependencies, and provides an ordered list of nodes
 * based on their dependencies.
 * <p>
 * Both the nodes and the dependencies keep their insertion order, so the resulting order is stable
 * (and therefore reproducible) for a given graph, instead of depending on the hash of the nodes.
 *
 * @param <T> the type of nodes in the graph
 */
public class DfsGraph<T> implements Iterable<T> {

	/** Map to store the dependencies of each node */
	private final Map<T, Set<T>> dependencies = new LinkedHashMap<>();

	/** Adds a node to the graph
	 * @param node the node to be added
	 */
	public void addNode(final T node) {
		dependencies.computeIfAbsent(node, k -> new LinkedHashSet<>());
	}

	/**
	 * Adds a dependency indicating that 'after' should come after 'before'
	 * @param after - the node that comes after
	 * @param before - the node that comes before
	 */
	public void addAfter(final T after, final T before) {
		addNode(after);
		addNode(before);
		dependencies.get(after).add(before);
	}

	/**
	 * Adds a dependency indicating that 'before' should come before 'after'
	 * @param before - the node that comes before
	 * @param after - the node that comes after
	 */
	public void addBefore(final T before, final T after) {
		addAfter(after, before);
	}

	/**
	 * Returns a list of nodes ordered based on their dependencies using DFS.
	 * <p>
	 * Every node is preceded by the nodes it depends on (a topological order), for example a node added with
	 * {@code addAfter(module, dependency)} comes after {@code dependency} and a node added with
	 * {@code addBefore(before, module)} comes after {@code before}.
	 *
	 * @return a list of nodes in topological order
	 * @throws IllegalStateException if a cycle is detected in the graph
	 */
	public List<T> ordered() {
		final var order = new ArrayList<T>();
		final var visited = new HashSet<T>();
		final var visiting = new LinkedHashSet<T>();

		for (final var node : dependencies.keySet()) {
			if (!visited.contains(node)) {
				if (!dfs(node, visited, visiting, order)) {
					throw new IllegalStateException("Cycle detected: " + String.join(" -> ", visiting.stream().map(String::valueOf).toList()));
				}
			}
		}

		// The nodes are added to the order after their dependencies have been visited, so the list already is a
		// valid topological order: reversing it (as it used to be done) would return the dependents first.
		return order;
	}

	/**
	 * Depth-First Search helper method for topological sorting.
	 * @param node the current node being visited
	 * @param visited set of already visited nodes
	 * @param visiting set of nodes currently being visited (to detect cycles)
	 * @param order list to store the topological order
	 * @return true if successful, false if a cycle is detected
	 */
	private boolean dfs(
			final T node,
			final Set<T> visited,
			final Set<T> visiting,
			final List<T> order
	) {
		visiting.add(node);

		for (final var dep : dependencies.getOrDefault(node, Set.of())) {
			if (visiting.contains(dep)) {
				return false; // cycle found
			}

			if (!visited.contains(dep) && !dfs(dep, visited, visiting, order)) {
				return false;
			}
		}

		visiting.remove(node);
		visited.add(node);
		order.add(node);
		return true;
	}

	/**
	 * Returns an iterator over the nodes in topological order.
	 * @return an iterator of nodes
	 */
	@Override
	public @NotNull Iterator<T> iterator() {
		return ordered().iterator();
	}
}
