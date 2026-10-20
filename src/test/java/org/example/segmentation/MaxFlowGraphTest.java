package org.example.segmentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class MaxFlowGraphTest {
    @Test
    void findsTheTextbookMinimumCut() {
        // s->0 (3), s->1 (2), 0->1 (1), 0->t (2), 1->t (3): three augmenting paths carry 5.
        MaxFlowGraph graph = new MaxFlowGraph(2);
        graph.addTerminalWeights(0, 3, 2);
        graph.addTerminalWeights(1, 2, 3);
        graph.addEdge(0, 1, 1, 0);

        assertEquals(5, graph.maxFlow(), 1e-9);
    }

    @Test
    void aStrongEdgeKeepsBothNodesOnTheSameSide() {
        MaxFlowGraph graph = new MaxFlowGraph(2);
        graph.addTerminalWeights(0, 5, 0);
        graph.addTerminalWeights(1, 0, 1);
        graph.addEdge(0, 1, 10, 10);

        assertEquals(1, graph.maxFlow(), 1e-9);
        assertTrue(graph.isSource(0));
        assertTrue(graph.isSource(1));
    }

    @Test
    void aWeakEdgeLetsEachNodeFollowItsOwnTerminal() {
        MaxFlowGraph graph = new MaxFlowGraph(2);
        graph.addTerminalWeights(0, 5, 1);
        graph.addTerminalWeights(1, 1, 5);
        graph.addEdge(0, 1, 2, 2);

        assertEquals(4, graph.maxFlow(), 1e-9);
        assertTrue(graph.isSource(0));
        assertFalse(graph.isSource(1));
    }

    @Test
    void matchesTheBruteForceMinimumCutOnRandomGraphs() {
        Random random = new Random(11);
        for (int round = 0; round < 200; round++) {
            int nodes = 2 + random.nextInt(7);
            double[] source = new double[nodes];
            double[] sink = new double[nodes];
            double[][] capacity = new double[nodes][nodes];
            MaxFlowGraph graph = new MaxFlowGraph(nodes);
            for (int node = 0; node < nodes; node++) {
                source[node] = random.nextInt(4) == 0 ? 0 : random.nextInt(10);
                sink[node] = random.nextInt(4) == 0 ? 0 : random.nextInt(10);
                graph.addTerminalWeights(node, source[node], sink[node]);
            }
            for (int from = 0; from < nodes; from++) {
                for (int to = from + 1; to < nodes; to++) {
                    if (random.nextBoolean()) {
                        capacity[from][to] = random.nextInt(8);
                        capacity[to][from] = random.nextInt(8);
                        graph.addEdge(from, to, capacity[from][to], capacity[to][from]);
                    }
                }
            }

            double flow = graph.maxFlow();

            double best = Double.MAX_VALUE;
            for (int sourceSide = 0; sourceSide < 1 << nodes; sourceSide++) {
                best = Math.min(best, cut(sourceSide, source, sink, capacity));
            }
            assertEquals(best, flow, 1e-9, "round " + round);
            int found = 0;
            for (int node = 0; node < nodes; node++) {
                found |= graph.isSource(node) ? 1 << node : 0;
            }
            assertEquals(best, cut(found, source, sink, capacity), 1e-9, "segmentation of round " + round);
        }
    }

    private static double cut(int sourceSide, double[] source, double[] sink, double[][] capacity) {
        double total = 0;
        for (int node = 0; node < source.length; node++) {
            boolean inSource = (sourceSide >> node & 1) == 1;
            total += inSource ? sink[node] : source[node];
            for (int other = 0; other < source.length; other++) {
                if (inSource && (sourceSide >> other & 1) == 0) {
                    total += capacity[node][other];
                }
            }
        }
        return total;
    }
}
