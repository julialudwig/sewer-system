package diver;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import graph.FindState;
import graph.FleeState;
import graph.Node;
import graph.NodeStatus;
import graph.SewerDiver;

public class McDiver extends SewerDiver {

    /** Find the ring in as few steps as possible. Once you get there, <br>
     * you must return from this function in order to pick<br>
     * it up. If you continue to move after finding the ring rather <br>
     * than returning, it will not count.<br>
     * If you return from this function while not standing on top of the ring, <br>
     * it will count as a failure.
     *
     * There is no limit to how many steps you can take, but you will receive<br>
     * a score bonus multiplier for finding the ring in fewer steps.
     *
     * At every step, you know only your current tile's ID and the ID of all<br>
     * open neighbor tiles, as well as the distance to the ring at each of <br>
     * these tiles (ignoring walls and obstacles).
     *
     * In order to get information about the current state, use functions<br>
     * currentLocation(), neighbors(), and distanceToRing() in state.<br>
     * You know you are standing on the ring when distanceToRing() is 0.
     *
     * Use function moveTo(long id) in state to move to a neighboring<br>
     * tile by its ID. Doing this will change state to reflect your new position.
     *
     * A suggested first implementation that will always find the ring, but <br>
     * likely won't receive a large bonus multiplier, is a depth-first walk. <br>
     * Some modification is necessary to make the search better, in general. */
    @Override
    public void find(FindState state) {
        // TODO : Find the ring and return.
        // DO NOT WRITE ALL THE CODE HERE. DO NOT MAKE THIS METHOD RECURSIVE.
        // Instead, write your method (it may be recursive) elsewhere, with a
        // good specification, and call it from this one.
        //
        // Working this way provides you with flexibility. For example, write
        // one basic method, which always works. Then, make a method that is a
        // copy of the first one and try to optimize in that second one.
        // If you don't succeed, you can always use the first one.
        //
        // Use this same process on the second method, flee.

        HashSet<Long> visited= new HashSet<>();

        // dfsWalk implementation
        // dfsWalk(state, visited);

        greedyWalk(state, visited);

    }

    /* McDiver is standing at a location given by id (long) u.
     * Visit every tile reachable along paths of unvisited tiles from tile
     * given by id u.
     * End with McDiver standing on tile containing the ring.
     */
    private void dfsWalk(FindState s, HashSet<Long> v) {
        long u= s.currentLocation();
        v.add(u);
        if (s.distanceToRing() == 0) { return; }
        for (NodeStatus n : s.neighbors()) {
            if (!v.contains(n.getId())) {
                s.moveTo(n.getId());
                v.add(n.getId());
                dfsWalk(s, v);
                if (s.distanceToRing() == 0) { return; }
                s.moveTo(u);
            }
        }
    }

    /* McDiver is standing at a location given by id (long) u.
     * Visit every tile reachable along paths of unvisited tiles from tile
     * given by id u beginning with the tiles with shortest distance
     * (the greedy choice).
     * End with McDiver standing on tile containing the ring.
     */
    private void greedyWalk(FindState s, HashSet<Long> v) {
        long u= s.currentLocation();
        v.add(u);
        if (s.distanceToRing() == 0) { return; }
        Object[] ns= s.neighbors().toArray();
        Arrays.sort(ns);
        for (Object on : ns) {
            NodeStatus n= (NodeStatus) on;
            if (!v.contains(n.getId())) {
                s.moveTo(n.getId());
                v.add(n.getId());
                greedyWalk(s, v);
                if (s.distanceToRing() == 0) { return; }
                s.moveTo(u);
            }
        }
    }

    /** Flee --get out of the sewer system before the steps are all used, trying to <br>
     * collect as many coins as possible along the way. McDiver must ALWAYS <br>
     * get out before the steps are all used, and this should be prioritized above<br>
     * collecting coins.
     *
     * You now have access to the entire underlying graph, which can be accessed<br>
     * through FleeState. currentNode() and exit() will return Node objects<br>
     * of interest, and getNodes() will return a collection of all nodes on the graph.
     *
     * You have to get out of the sewer system in the number of steps given by<br>
     * stepToGo(); for each move along an edge, this number is <br>
     * decremented by the weight of the edge taken.
     *
     * Use moveTo(n) to move to a node n that is adjacent to the current node.<br>
     * When n is moved-to, coins on node n are automatically picked up.
     *
     * You must return from this function while standing at the exit. Failing <br>
     * to do so before steps run out or returning from the wrong node will be<br>
     * considered a failed run.
     *
     * Initially, there are enough steps to get from the starting point to the<br>
     * exit using the shortest path, although this will not collect many coins.<br>
     * For this reason, a good starting solution is to use the shortest path to<br>
     * the exit. */
    @Override
    public void flee(FleeState state) {
        // TODO: Get out of the sewer system before the steps are used up.
        // DO NOT WRITE ALL THE CODE HERE. Instead, write your method elsewhere,
        // with a good specification, and call it from this one.

        // unoptimized(state);

        optimized(state);
    }

    /* Moves McDiver to the exit in the flee stage in the shortest amount of steps.
     * This implements the shortest path algorithm without optimizations.
     */
    private void unoptimized(FleeState s) {
        List<Node> path= A6.shortest(s.currentNode(), s.exit());
        path.remove(0);
        for (Node n : path) {
            s.moveTo(n);
        }
    }

    /* Moves McDiver to collect coins by calculating which shortest path to each
     * Node yields the highest value in coins until there are only enough steps
     * to reach the exit. Then, McDiver will take the shortest path from that
     * Node to the exit.
     */
    private void optimized(FleeState s) {
        ConcurrentHashMap<Node, List<Node>> sp;
        Heap<Node> heap;
        int to_steps;
        int exit_steps;
        int value;
        while (!s.currentNode().equals(s.exit())) {
            sp= A6.shortestMap(s.currentNode(), s.allNodes());
            heap= new Heap<>(false);

            for (Node n1 : sp.keySet()) {
                to_steps= pathSteps(sp.get(n1));
                exit_steps= pathSteps(A6.shortest(n1, s.exit()));
                if (to_steps + exit_steps > s.stepsToGo()) {
                    sp.remove(n1);
                } else {
                    value= 0;
                    for (Node n2 : sp.get(n1)) {
                        value+= n2.getTile().coins();
                    }
                    heap.insert(n1, value);
                }
            }

            Node max= heap.poll();
            if (max.equals(s.currentNode())) max= s.exit();

            List<Node> path= sp.get(max);
            path.remove(0);
            for (Node n3 : path) {
                s.moveTo(n3);
            }

        }
    }

    /* Returns the total lengths of all the edges in path. */
    private int pathSteps(List<Node> path) {
        int steps= 0;
        for (int i= 0; i <= path.size() - 2; i++ ) {
            Node current= path.get(i);
            Node next= path.get(i + 1);
            steps+= current.getEdge(next).length();
        }
        return steps;
    }
}
