package dev.foucaultleon.flterraforged.engine.river;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Reduces a dense drainage skeleton to the river network that should become visible terrain. */
final class RiverNetworkFilter {

    private static final int MINIMUM_FILTER_SIZE = 12;
    private static final int MINIMUM_NETWORK_SPAN = 320;
    private static final double MATURE_TRUNK_FACTOR = 2.60D;

    private RiverNetworkFilter() {
    }

    /**
     * Removes isolated first-order fall lines while retaining confluences and mature trunks.
     *
     * <p>The drainage solver intentionally operates on more information than should be visible as
     * water. On broad smooth slopes D8 can create a comb of almost parallel first-order paths. They
     * are useful to accumulate catchment flow, but materializing all of them produces the striped
     * landscape seen in R47. R48 derives Strahler order from the already resolved directed graph and
     * keeps higher-order rivers, the last tributary edge entering a confluence, and sufficiently
     * mature first-order trunks. Small or spatially local creek graphs are left untouched; hierarchy
     * filtering is a macro-catchment operation and must not erase compact drainage systems or the
     * synthetic graphs used to verify hydraulic invariants. The operation is deterministic and does
     * not resample terrain.</p>
     *
     * @param segments resolved directed river segments
     * @return immutable visible subset
     */
    static List<RiverSegment> visibleNetwork(List<RiverSegment> segments) {
        Objects.requireNonNull(segments, "segments");
        if (segments.size() < MINIMUM_FILTER_SIZE || networkSpan(segments) < MINIMUM_NETWORK_SPAN) {
            return List.copyOf(segments);
        }

        Map<Node, List<RiverSegment>> incoming = new HashMap<>();
        double baseFlow = Double.POSITIVE_INFINITY;
        for (RiverSegment segment : segments) {
            incoming.computeIfAbsent(Node.end(segment), ignored -> new ArrayList<>()).add(segment);
            baseFlow = Math.min(baseFlow, segment.flow());
        }
        if (!Double.isFinite(baseFlow) || !(baseFlow > 0.0D)) {
            return List.copyOf(segments);
        }

        Map<RiverSegment, Integer> segmentOrders = new HashMap<>();
        Map<Node, Integer> nodeOrders = new HashMap<>();
        Set<RiverSegment> visiting = new HashSet<>();
        for (RiverSegment segment : segments) {
            streamOrder(segment, incoming, segmentOrders, nodeOrders, visiting);
        }

        double matureTrunkFlow = baseFlow * MATURE_TRUNK_FACTOR;
        List<RiverSegment> visible = new ArrayList<>(segments.size());
        for (RiverSegment segment : segments) {
            int order = segmentOrders.getOrDefault(segment, 1);
            int receiverOrder = nodeOrder(
                    Node.end(segment), incoming, segmentOrders, nodeOrders, visiting);
            if (order >= 2 || receiverOrder >= 2 || segment.flow() >= matureTrunkFlow) {
                visible.add(segment);
            }
        }
        return List.copyOf(visible);
    }

    private static int networkSpan(List<RiverSegment> segments) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (RiverSegment segment : segments) {
            minX = Math.min(minX, Math.min(segment.startX(), segment.endX()));
            minZ = Math.min(minZ, Math.min(segment.startZ(), segment.endZ()));
            maxX = Math.max(maxX, Math.max(segment.startX(), segment.endX()));
            maxZ = Math.max(maxZ, Math.max(segment.startZ(), segment.endZ()));
        }
        return Math.max(maxX - minX, maxZ - minZ);
    }

    private static int streamOrder(
            RiverSegment segment,
            Map<Node, List<RiverSegment>> incoming,
            Map<RiverSegment, Integer> segmentOrders,
            Map<Node, Integer> nodeOrders,
            Set<RiverSegment> visiting) {
        Integer cached = segmentOrders.get(segment);
        if (cached != null) {
            return cached;
        }
        if (!visiting.add(segment)) {
            throw new IllegalStateException("Visible river graph contains a cycle");
        }
        try {
            int order = nodeOrder(Node.start(segment), incoming, segmentOrders, nodeOrders, visiting);
            segmentOrders.put(segment, order);
            return order;
        } finally {
            visiting.remove(segment);
        }
    }

    private static int nodeOrder(
            Node node,
            Map<Node, List<RiverSegment>> incoming,
            Map<RiverSegment, Integer> segmentOrders,
            Map<Node, Integer> nodeOrders,
            Set<RiverSegment> visiting) {
        Integer cached = nodeOrders.get(node);
        if (cached != null) {
            return cached;
        }
        List<RiverSegment> tributaries = incoming.get(node);
        if (tributaries == null || tributaries.isEmpty()) {
            nodeOrders.put(node, 1);
            return 1;
        }

        int maximum = 0;
        int maximumCount = 0;
        for (RiverSegment tributary : tributaries) {
            int order = streamOrder(tributary, incoming, segmentOrders, nodeOrders, visiting);
            if (order > maximum) {
                maximum = order;
                maximumCount = 1;
            } else if (order == maximum) {
                maximumCount++;
            }
        }
        int resolved = maximumCount >= 2 ? maximum + 1 : maximum;
        nodeOrders.put(node, resolved);
        return resolved;
    }

    private record Node(int x, int z) {

        private static Node start(RiverSegment segment) {
            return new Node(segment.startX(), segment.startZ());
        }

        private static Node end(RiverSegment segment) {
            return new Node(segment.endX(), segment.endZ());
        }
    }
}
