package com.thewinterframework.utils.graph;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

/**
 * Directed dependency graph with deterministic topological ordering.
 *
 * <p>Both the nodes and their dependencies keep their insertion order, so the default ordering is stable for a given
 * graph. Callers that need their own tie breaking (for instance a component {@code order}) can pass a comparator.</p>
 *
 * @param <T> node type
 */
public class DfsGraph<T> implements Iterable<T> {

	private final Map<T, Set<T>> dependencies = new LinkedHashMap<>();

	public void addNode(final T node) {
		dependencies.computeIfAbsent(node, unused -> new LinkedHashSet<>());
	}

	/** Declares that {@code after} depends on {@code before}. */
	public void addAfter(final T after, final T before) {
		addNode(after);
		addNode(before);
		dependencies.get(after).add(before);
	}

	/** Declares that {@code before} must precede {@code after}. */
	public void addBefore(final T before, final T after) {
		addAfter(after, before);
	}

	/**
	 * Returns a topological order that keeps the insertion order of the nodes when nothing forces them apart.
	 *
	 * @return the ordered nodes
	 * @throws IllegalStateException if the graph contains a cycle
	 */
	public List<T> ordered() {
		final var graph = flatten();
		final var ready = new ArrayDeque<T>();
		graph.inDegree().forEach((node, degree) -> {
			if (degree == 0) {
				ready.add(node);
			}
		});
		return drain(graph.inDegree(), graph.dependents(), ready);
	}

	/**
	 * Returns a deterministic topological order using the comparator to decide between the nodes that are ready.
	 *
	 * @param comparator the comparator used to order the nodes that could be visited next
	 * @return the ordered nodes
	 * @throws IllegalStateException if the graph contains a cycle
	 */
	public List<T> ordered(final Comparator<? super T> comparator) {
		final var graph = flatten();
		final var ready = new PriorityQueue<T>(comparator);
		graph.inDegree().forEach((node, degree) -> {
			if (degree == 0) {
				ready.add(node);
			}
		});
		return drain(graph.inDegree(), graph.dependents(), ready);
	}

	private Flattened<T> flatten() {
		final var inDegree = new LinkedHashMap<T, Integer>();
		final var dependents = new LinkedHashMap<T, Set<T>>();

		for (final var entry : dependencies.entrySet()) {
			inDegree.put(entry.getKey(), entry.getValue().size());
			dependents.computeIfAbsent(entry.getKey(), unused -> new LinkedHashSet<>());
			for (final var dependency : entry.getValue()) {
				dependents.computeIfAbsent(dependency, unused -> new LinkedHashSet<>()).add(entry.getKey());
			}
		}

		return new Flattened<>(inDegree, dependents);
	}

	private List<T> drain(final Map<T, Integer> inDegree, final Map<T, Set<T>> dependents, final Queue<T> ready) {
		final var order = new ArrayList<T>(inDegree.size());

		while (!ready.isEmpty()) {
			final var node = ready.remove();
			order.add(node);
			for (final var dependent : dependents.getOrDefault(node, Set.of())) {
				final var remaining = inDegree.compute(dependent, (unused, degree) -> degree - 1);
				if (remaining == 0) {
					ready.add(dependent);
				}
			}
		}

		if (order.size() != inDegree.size()) {
			final var cycle = inDegree.entrySet().stream()
					.filter(entry -> entry.getValue() > 0)
					.map(entry -> String.valueOf(entry.getKey()))
					.sorted()
					.toList();
			throw new IllegalStateException("Cycle detected: " + String.join(" -> ", cycle));
		}

		return List.copyOf(order);
	}

	public Set<T> dependenciesOf(final T node) {
		return Set.copyOf(dependencies.getOrDefault(node, Set.of()));
	}

	@Override
	public @NotNull java.util.Iterator<T> iterator() {
		return ordered().iterator();
	}

	private record Flattened<T>(Map<T, Integer> inDegree, Map<T, Set<T>> dependents) {
	}
}
