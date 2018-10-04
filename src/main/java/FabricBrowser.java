import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.PIP;
import com.xilinx.rapidwright.device.Tile;

import java.util.*;

public class FabricBrowser {


    public static HashMap<String, ArrayList<PIP>> pipCache = new HashMap<>();
    public static HashMap<String, Set<String>> exitFanOutCache = new HashMap<>();
    public static HashMap<String, Set<String>> entranceFanOutCache = new HashMap<>();

    private static final int TILE_TRAVERSAL_MAX_DEPTH = 8;
    private static class NodeDepthPair {
        /*
         * Internal class used to track depth of BFS searches
         */
        private String nodeName;
        private int depth;

        public NodeDepthPair(String nodeName) {
            this.nodeName = nodeName;
            depth = 0;
        }

        public NodeDepthPair(String nodeName, int depth) {
            this.nodeName = nodeName;
            this.depth = depth;
        }

        public String getNodeName() {
            return nodeName;
        }

        public int getDepth() {
            return depth;
        }
    }

    public static Set<String> globalNodeFootprint = new HashSet<>();

    public static void setGlobalNodeFootprint(Set<String> footprint) {
        globalNodeFootprint = footprint;
    }

    public static ArrayList<PIP> getTilePIPs(Design d, String tileName) {
        if (!pipCache.containsKey(tileName))
            pipCache.put(tileName, d.getDevice().getTile(tileName).getPIPs());
        return pipCache.get(tileName);
    }

    public static Set<PIP> getFwdPIPs(Design d, String tileName, String nodeName) {
        Set<PIP> pipSet = new HashSet<>();

        for (PIP pip : getTilePIPs(d, tileName)) {
            if (pip.getStartNode().getName().equals(nodeName))
                pipSet.add(pip);
        }
        return pipSet;
    }

    public static Set<PIP> getBkwdPIPs(Design d, String tileName, String nodeName) {
        Set<PIP> pipSet = new HashSet<>();

        for (PIP pip : getTilePIPs(d, tileName)) {
            if (pip.getEndNode().getName().equals(nodeName))
                pipSet.add(pip);
        }
        return pipSet;
    }

    /*
     * Find all entering wire junctions that can be routed to the exit junction
     *   Checks cache first before searching
     */
    public static Set<EnterWireJunction> getExitFanOut(Design d, ExitWireJunction exit) {

        if (!exitFanOutCache.containsKey(exit.getWireName()))
            return searchExitFanOut(d, exit);

        Set<EnterWireJunction> entrances = new HashSet<>();
        String tileName = exit.getTileName();
        for (String wireName : exitFanOutCache.get(exit.getWireName())) {
            entrances.add(new EnterWireJunction(d, tileName, wireName));
        }
        return entrances;
    }

    /*
     * Find all exiting wire junctions that can be routed from the entrance junction
     *   Checks cache first before searching
     */
    public static Set<ExitWireJunction> getEntranceFanOut(Design d, EnterWireJunction entrance) {

        if (!entranceFanOutCache.containsKey(entrance.getWireName()))
            return searchEntranceFanOut(d, entrance);

        Set<ExitWireJunction> exits = new HashSet<>();
        String tileName = entrance.getTileName();
        for (String wireName : entranceFanOutCache.get(entrance.getWireName())) {
            exits.add(new ExitWireJunction(d, tileName, wireName));
        }

        return exits;
    }

    /*
     * BFS search for all entering wire junctions that can be routed to the exit junction
     *   Results are cached in exitFanOutCache, replacing previous cache if there are any
     */
    public static Set<EnterWireJunction> searchExitFanOut(Design d, ExitWireJunction exit) {
        Set<EnterWireJunction> entrances = new HashSet<>();
        Tile tile = d.getDevice().getTile(exit.getTileName());
        String tileName = tile.getName();

        Set<String> results = new HashSet<>();

        Queue<NodeDepthPair> queue = new LinkedList<>();
        queue.add(new NodeDepthPair(exit.getNodeName()));

        HashSet<String> footprint = new HashSet<>();

        while (!queue.isEmpty()) {
            NodeDepthPair trav = queue.remove();

            if (trav.getDepth() >= TILE_TRAVERSAL_MAX_DEPTH)
                break;

            for (PIP pip : getBkwdPIPs(d, tileName, trav.getNodeName())) {
                String nextNodeName = pip.getStartNode().getName();

                WireDirection dir = RouteUtil.extractEnterWireDirection(d, tileName, pip.getStartWireName());
                int wireLength = RouteUtil.extractEnterWireLength(d, tileName, pip.getStartWireName());

                if (globalNodeFootprint.contains(nextNodeName) || footprint.contains(nextNodeName)
                        || CustomRouter.isLocked(nextNodeName))
                    continue;

                if (dir != null && dir!= WireDirection.SELF && wireLength != 0
                        && !RouteUtil.isClkNode(nextNodeName)) {
                    results.add(pip.getStartWireName());
                    //entrances.add(new EnterWireJunction(tileName, pip.getStartWireName(), wireLength, dir));
                }
                if (RouteUtil.isNodeBuffer(d, tileName, nextNodeName))
                    queue.add(new NodeDepthPair(nextNodeName, trav.getDepth() + 1));

                footprint.add(nextNodeName);
            }
        }

        exitFanOutCache.put(exit.getWireName(), results);

        for (String wireName : results) {
            entrances.add(new EnterWireJunction(d, tileName, wireName));
        }

        return entrances;

    }

    /*
     * BFS search for all exiting wire junctions that can be routed from the entrance junction
     *   Results are cached in entranceFanOutCache, replacing previous cache if there are any
     */
    public static Set<ExitWireJunction> searchEntranceFanOut(Design d, EnterWireJunction entrance) {
        Set<ExitWireJunction> exits = new HashSet<>();
        Tile tile = d.getDevice().getTile(entrance.getTileName());
        String tileName = tile.getName();

        Set<String> results = new HashSet<>();

        Queue<NodeDepthPair> queue = new LinkedList<>();
        queue.add(new NodeDepthPair(entrance.getNodeName()));

        HashSet<String> footprint = new HashSet<>();

        while (!queue.isEmpty()) {
            NodeDepthPair trav = queue.remove();

            if (trav.getDepth() >= TILE_TRAVERSAL_MAX_DEPTH)
                break;

            for (PIP pip : getFwdPIPs(d, tileName, trav.getNodeName())) {
                String nextNodeName = pip.getEndNode().getName();

                WireDirection dir = RouteUtil.extractExitWireDirection(d, tileName, pip.getEndWireName());
                int wireLength = RouteUtil.extractExitWireLength(d, tileName, pip.getEndWireName());

                if (globalNodeFootprint.contains(nextNodeName) || footprint.contains(nextNodeName)
                        || CustomRouter.isLocked(nextNodeName))
                    continue;

                if (dir != null && dir != WireDirection.SELF && wireLength != 0
                        && !RouteUtil.isClkNode(nextNodeName)) {
                    results.add(pip.getEndWireName());
                }
                if (RouteUtil.isNodeBuffer(d, tileName, nextNodeName))
                    queue.add(new NodeDepthPair(nextNodeName, trav.getDepth() + 1));

                footprint.add(nextNodeName);
            }
        }

        entranceFanOutCache.put(entrance.getWireName(), results);

        for (String wireName : entranceFanOutCache.get(entrance.getWireName())) {
            exits.add(new ExitWireJunction(d, tileName, wireName));
        }

        return exits;
    }

    /*
     * BFS search for all possible INT tile paths from entrance to exit, that are sufficiently fast
     *   Returned list is in order of lowest-to-highest cost
     */
    public static ArrayList<TilePath> findTilePaths(Design d, EnterWireJunction entrance,
                                                    ExitWireJunction exit) {
        ArrayList<TilePath> results = new ArrayList<>();

        // Not applicable unless entrance and exit are on the same INT tile.
        if (!entrance.getTileName().equals(exit.getTileName()))
            return results;

        String tileName = entrance.getTileName();

        Queue<TilePath> queue = new LinkedList<>();
        queue.add(new TilePath(entrance, exit));

        int maxDepth = TILE_TRAVERSAL_MAX_DEPTH;
        int minDepth = 0;
        int extraDepthTolerance = 3;

        while (!queue.isEmpty()) {
            TilePath trav = queue.remove();

            if (trav.getCost() >= maxDepth + 1)
                break;

            for (PIP pip : getFwdPIPs(d, tileName, trav.getNodeName(-2))) {
                String nextNodeName = pip.getEndNode().getName();

                if (nextNodeName.equals(exit.getNodeName())) {
                    results.add(new TilePath(trav));

                    // Some slack is given such that slightly slower routes are still recorded
                    minDepth = trav.getCost() - 1;
                    maxDepth = Math.min(minDepth + extraDepthTolerance, TILE_TRAVERSAL_MAX_DEPTH);
                }
                else if (RouteUtil.isNodeBuffer(d, tileName, nextNodeName)) {

                    if (globalNodeFootprint.contains(nextNodeName)
                            || (CustomRouter.isLocked(nextNodeName)))
                        continue;

                    TilePath travCopy = new TilePath(trav);

                    // To prevent cycles in buffer traversal, don't queue previously traversed buffers
                    if (travCopy.addNode(nextNodeName))
                        queue.add(travCopy);
                }
            }
        }

        RouterLog.log("Found " + results.size() + " INT tile paths for " + entrance + " --> " + exit + ".",
                RouterLog.Level.INFO);
        if (!results.isEmpty()) {
            RouterLog.indent();
            RouterLog.log("Minimum cost of tile paths is " + results.get(0).getCost() + ".", RouterLog.Level.VERBOSE);
            RouterLog.indent(-1);
        }

        return results;
    }

    /*
     * Verifies that there is a path between entrance and exit
     */
    public static boolean isPathPossible(Design d, EnterWireJunction entrance, ExitWireJunction exit) {

        // Not applicable unless entrance and exit are on the same INT tile.
        if (!entrance.getTileName().equals(exit.getTileName()))
            return false;

        String tileName = entrance.getTileName();

        Queue<NodeDepthPair> queue = new LinkedList<>();
        queue.add(new NodeDepthPair(entrance.getNodeName()));

        HashSet<String> footprint = new HashSet<>();

        while (!queue.isEmpty()) {
            NodeDepthPair trav = queue.remove();

            if (trav.getDepth() >= TILE_TRAVERSAL_MAX_DEPTH)
                return false;

            for (PIP pip : getFwdPIPs(d, tileName, trav.getNodeName())) {
                String nextNodeName = pip.getEndNode().getName();

                if (nextNodeName.equals(exit.getNodeName())) {
                    return true;
                }

                if (globalNodeFootprint.contains(nextNodeName) || footprint.contains(nextNodeName)
                        || CustomRouter.isLocked(nextNodeName))
                    continue;

                if (RouteUtil.isNodeBuffer(d, tileName, nextNodeName))
                    queue.add(new NodeDepthPair(nextNodeName, trav.getDepth() + 1));

                footprint.add(nextNodeName);
            }
        }


        return false;
    }
}
