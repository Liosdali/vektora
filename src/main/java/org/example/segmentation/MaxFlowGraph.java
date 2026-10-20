package org.example.segmentation;

import java.util.Arrays;

/**
 * Minimum s-t cut of a graph whose nodes may also connect to the source and sink terminals, using
 * the Boykov–Kolmogorov algorithm ("An Experimental Comparison of Min-Cut/Max-Flow Algorithms for
 * Energy Minimization in Vision", 2004). It grows search trees from both terminals and reuses them
 * after each augmentation, which suits the grid graphs of image segmentation.
 *
 * <p>Arcs are stored in pairs, so an arc's reverse ("sister") is {@code arc ^ 1}.
 */
final class MaxFlowGraph {
    private static final int NONE = -1;
    private static final int TERMINAL = -2;
    private static final int ORPHAN = -3;
    private static final int INFINITE_DISTANCE = Integer.MAX_VALUE;

    private final int nodeCount;
    /** Residual terminal capacity: positive toward the source, negative toward the sink. */
    private final double[] terminalCapacity;
    private final int[] firstArc;
    private int[] arcHead;
    private int[] arcNext;
    private double[] residual;
    private int arcCount;
    private double flow;

    private final int[] parent;
    private final boolean[] sinkTree;
    private final int[] nextActive;
    private final int[] timestamp;
    private final int[] distance;
    private int firstActive = NONE;
    private int lastActive = NONE;
    private int[] orphans = new int[64];
    private int orphanStart;
    private int orphanEnd;
    private int time;
    private boolean solved;

    MaxFlowGraph(int nodeCount) {
        this(nodeCount, 16);
    }

    /** A graph sized for about {@code expectedEdges} edges, so building it does not reallocate. */
    MaxFlowGraph(int nodeCount, int expectedEdges) {
        if (nodeCount < 1) {
            throw new IllegalArgumentException("A graph needs at least one node");
        }
        this.nodeCount = nodeCount;
        terminalCapacity = new double[nodeCount];
        firstArc = new int[nodeCount];
        Arrays.fill(firstArc, NONE);
        parent = new int[nodeCount];
        sinkTree = new boolean[nodeCount];
        nextActive = new int[nodeCount];
        timestamp = new int[nodeCount];
        distance = new int[nodeCount];
        int arcs = Math.max(64, 2 * expectedEdges);
        arcHead = new int[arcs];
        arcNext = new int[arcs];
        residual = new double[arcs];
    }

    /** Adds capacity from the source to the node and from the node to the sink. */
    void addTerminalWeights(int node, double toSource, double toSink) {
        requireUnsolved();
        double current = terminalCapacity[node];
        if (current > 0) {
            toSource += current;
        } else {
            toSink -= current;
        }
        flow += Math.min(toSource, toSink);
        terminalCapacity[node] = toSource - toSink;
    }

    void addEdge(int from, int to, double capacity, double reverseCapacity) {
        requireUnsolved();
        if (from == to || capacity < 0 || reverseCapacity < 0) {
            throw new IllegalArgumentException("Edges join two different nodes with non-negative capacities");
        }
        if (arcCount + 2 > arcHead.length) {
            int size = arcHead.length * 2;
            arcHead = Arrays.copyOf(arcHead, size);
            arcNext = Arrays.copyOf(arcNext, size);
            residual = Arrays.copyOf(residual, size);
        }
        addArc(from, to, capacity);
        addArc(to, from, reverseCapacity);
    }

    private void addArc(int from, int to, double capacity) {
        arcHead[arcCount] = to;
        arcNext[arcCount] = firstArc[from];
        residual[arcCount] = capacity;
        firstArc[from] = arcCount++;
    }

    double maxFlow() {
        requireUnsolved();
        solved = true;
        for (int node = 0; node < nodeCount; node++) {
            nextActive[node] = NONE;
            timestamp[node] = 0;
            if (terminalCapacity[node] != 0) {
                sinkTree[node] = terminalCapacity[node] < 0;
                parent[node] = TERMINAL;
                setActive(node);
                distance[node] = 1;
            } else {
                parent[node] = NONE;
            }
        }

        int current = NONE;
        while (true) {
            int node = current;
            if (node != NONE) {
                nextActive[node] = NONE;
                if (parent[node] == NONE) {
                    node = NONE;
                }
            }
            if (node == NONE && (node = nextActiveNode()) == NONE) {
                break;
            }

            int bridge = grow(node);
            time++;
            if (bridge != NONE) {
                // Keep processing this node: it may reach the other tree again.
                nextActive[node] = node;
                current = node;
                augment(bridge);
                adoptOrphans();
            } else {
                current = NONE;
            }
        }
        return flow;
    }

    /** Whether the node ends on the source side of the minimum cut. */
    boolean isSource(int node) {
        if (!solved) {
            throw new IllegalStateException("Run maxFlow first");
        }
        return parent[node] != NONE && !sinkTree[node];
    }

    /** Grows the node's tree one step; returns the arc leading from the source tree into the sink tree. */
    private int grow(int node) {
        boolean sink = sinkTree[node];
        for (int arc = firstArc[node]; arc != NONE; arc = arcNext[arc]) {
            if ((sink ? residual[arc ^ 1] : residual[arc]) <= 0) {
                continue;
            }
            int neighbor = arcHead[arc];
            if (parent[neighbor] == NONE) {
                sinkTree[neighbor] = sink;
                parent[neighbor] = arc ^ 1;
                timestamp[neighbor] = timestamp[node];
                distance[neighbor] = distance[node] + 1;
                setActive(neighbor);
            } else if (sinkTree[neighbor] != sink) {
                return sink ? arc ^ 1 : arc;
            } else if (timestamp[neighbor] <= timestamp[node] && distance[neighbor] > distance[node]) {
                // A shorter way to the terminal: re-hang the neighbor under this node.
                parent[neighbor] = arc ^ 1;
                timestamp[neighbor] = timestamp[node];
                distance[neighbor] = distance[node] + 1;
            }
        }
        return NONE;
    }

    /** Pushes the bottleneck along source tree, bridge and sink tree, and orphans saturated links. */
    private void augment(int bridge) {
        double bottleneck = residual[bridge];
        int node = arcHead[bridge ^ 1];
        for (int arc = parent[node]; arc != TERMINAL; arc = parent[node]) {
            bottleneck = Math.min(bottleneck, residual[arc ^ 1]);
            node = arcHead[arc];
        }
        bottleneck = Math.min(bottleneck, terminalCapacity[node]);
        node = arcHead[bridge];
        for (int arc = parent[node]; arc != TERMINAL; arc = parent[node]) {
            bottleneck = Math.min(bottleneck, residual[arc]);
            node = arcHead[arc];
        }
        bottleneck = Math.min(bottleneck, -terminalCapacity[node]);

        residual[bridge ^ 1] += bottleneck;
        residual[bridge] -= bottleneck;
        node = arcHead[bridge ^ 1];
        for (int arc = parent[node]; arc != TERMINAL; arc = parent[node]) {
            residual[arc] += bottleneck;
            residual[arc ^ 1] -= bottleneck;
            int next = arcHead[arc];
            if (residual[arc ^ 1] <= 0) {
                makeOrphan(node);
            }
            node = next;
        }
        terminalCapacity[node] -= bottleneck;
        if (terminalCapacity[node] == 0) {
            makeOrphan(node);
        }
        node = arcHead[bridge];
        for (int arc = parent[node]; arc != TERMINAL; arc = parent[node]) {
            residual[arc ^ 1] += bottleneck;
            residual[arc] -= bottleneck;
            int next = arcHead[arc];
            if (residual[arc] <= 0) {
                makeOrphan(node);
            }
            node = next;
        }
        terminalCapacity[node] += bottleneck;
        if (terminalCapacity[node] == 0) {
            makeOrphan(node);
        }
        flow += bottleneck;
    }

    private void adoptOrphans() {
        while (orphanStart < orphanEnd) {
            adopt(orphans[orphanStart++]);
        }
        orphanStart = 0;
        orphanEnd = 0;
    }

    /** Finds the orphan a new parent in its own tree, or frees it and orphans its children. */
    private void adopt(int orphan) {
        boolean sink = sinkTree[orphan];
        int bestArc = NONE;
        int bestDistance = INFINITE_DISTANCE;
        for (int arc = firstArc[orphan]; arc != NONE; arc = arcNext[arc]) {
            if ((sink ? residual[arc] : residual[arc ^ 1]) <= 0) {
                continue;
            }
            int neighbor = arcHead[arc];
            if (sinkTree[neighbor] != sink || parent[neighbor] == NONE) {
                continue;
            }
            int origin = originDistance(neighbor);
            if (origin < INFINITE_DISTANCE) {
                if (origin < bestDistance) {
                    bestArc = arc;
                    bestDistance = origin;
                }
                // Remember the distances found along this chain for later orphans.
                int marked = origin;
                for (int node = neighbor; timestamp[node] != time; node = arcHead[parent[node]]) {
                    timestamp[node] = time;
                    distance[node] = marked--;
                }
            }
        }

        if (bestArc != NONE) {
            parent[orphan] = bestArc;
            timestamp[orphan] = time;
            distance[orphan] = bestDistance + 1;
            return;
        }
        parent[orphan] = NONE;
        for (int arc = firstArc[orphan]; arc != NONE; arc = arcNext[arc]) {
            int neighbor = arcHead[arc];
            int link = parent[neighbor];
            if (sinkTree[neighbor] != sink || link == NONE) {
                continue;
            }
            if ((sink ? residual[arc] : residual[arc ^ 1]) > 0) {
                setActive(neighbor);
            }
            if (link != TERMINAL && link != ORPHAN && arcHead[link] == orphan) {
                makeOrphanRear(neighbor);
            }
        }
    }

    /** Distance from the node to its terminal, or infinity when its chain reaches an orphan. */
    private int originDistance(int start) {
        int node = start;
        int steps = 0;
        while (true) {
            if (timestamp[node] == time) {
                return steps + distance[node];
            }
            int link = parent[node];
            steps++;
            if (link == TERMINAL) {
                timestamp[node] = time;
                distance[node] = 1;
                return steps;
            }
            if (link == ORPHAN) {
                return INFINITE_DISTANCE;
            }
            node = arcHead[link];
        }
    }

    private void makeOrphan(int node) {
        parent[node] = ORPHAN;
        if (orphanStart == 0) {
            ensureOrphanRoom();
            System.arraycopy(orphans, 0, orphans, 1, orphanEnd);
            orphanEnd++;
            orphans[0] = node;
        } else {
            orphans[--orphanStart] = node;
        }
    }

    private void makeOrphanRear(int node) {
        parent[node] = ORPHAN;
        ensureOrphanRoom();
        orphans[orphanEnd++] = node;
    }

    private void ensureOrphanRoom() {
        if (orphanEnd + 1 >= orphans.length) {
            orphans = Arrays.copyOf(orphans, orphans.length * 2);
        }
    }

    private void setActive(int node) {
        if (nextActive[node] != NONE) {
            return;
        }
        if (lastActive != NONE) {
            nextActive[lastActive] = node;
        } else {
            firstActive = node;
        }
        lastActive = node;
        nextActive[node] = node;
    }

    private int nextActiveNode() {
        while (firstActive != NONE) {
            int node = firstActive;
            if (nextActive[node] == node) {
                firstActive = NONE;
                lastActive = NONE;
            } else {
                firstActive = nextActive[node];
            }
            nextActive[node] = NONE;
            if (parent[node] != NONE) {
                return node;
            }
        }
        return NONE;
    }

    private void requireUnsolved() {
        if (solved) {
            throw new IllegalStateException("The graph has already been cut");
        }
    }
}
