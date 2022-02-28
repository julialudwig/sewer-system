
package diver;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import graph.Edge;
import graph.Node;

/** This class contains the solution to A6, shortest-path algorithm, <br>
 * and other methods for an undirected graph. */
public class A6 {

    /** Replace "-1" by the time you spent on A2 in hours.<br>
     * Example: for 3 hours 15 minutes, use 3.25<br>
     * Example: for 4 hours 30 minutes, use 4.50<br>
     * Example: for 5 hours, use 5 or 5.0 */
    public static double timeSpent= -1;

    /** = the shortest path from node v to node end <br>
     * ---or the empty list if a path does not exist. <br>
     * Note: The empty list is a list with 0 elements ---it is not "null". */
    public static List<Node> shortest(Node v, Node end) {
        /* TODO Implement this method.
         * Read the A6 assignment handout for all details.
         * Remember, the graph is undirected. */

        // Contains an entry for each node in the frontier set. The priority of
        // a node is the length of the shortest known path from v to the node
        // using only settled nodes except for the last node, which is in F.
        Heap<Node> F= new Heap<>(true);

        // The mapSF keys are all nodes in the settled set S and frontier set F.
        // The values are each node's Info, which contains information
        // such as its distance and its backpointer.
        HashMap<Node, Info> mapSF= new HashMap<>();

        F.insert(v, 0);
        mapSF.put(v, new Info(null, 0));

        while (F.size() > 0) {
            Node f= F.poll();
            if (f == end) return path(mapSF, end);
            int d= mapSF.get(f).dist;
            for (Edge edge : f.getExits()) {
                Node w= edge.getOther(f);
                Info wInfo= mapSF.get(w);
                int wDist= d + edge.length;
                if (wInfo == null) {
                    mapSF.put(w, new Info(f, wDist));
                    F.insert(w, wDist);
                } else if (wDist < wInfo.dist) {
                    wInfo.dist= wDist;
                    wInfo.bkptr= f;
                    F.changePriority(w, wDist);
                }
            }
        }

        // no path from v to end
        return new LinkedList<>();
    }

    /** Returns a HashMap with Nodes in nodes as keys and values are the list of nodes that create
     * the shortest path from a Node v <br>
     * or the empty list if a path does not exist from Node v. <br>
     * Note: The empty list is a list with 0 elements ---it is not "null". */
    public static ConcurrentHashMap<Node, List<Node>> shortestMap(Node v, Collection<Node> nodes) {
        ConcurrentHashMap<Node, List<Node>> map= new ConcurrentHashMap<>();
        for (Node n : nodes) {
            List<Node> sp= shortest(v, n);
            map.put(n, sp);
        }
        return map;
    }

    /** An instance contains information about a node: <br>
     * the Distance of this node from the start node and <br>
     * its Backpointer: the previous node on a shortest path <br>
     * from the first node to this node (null for the start node). */
    private static class Info {
        /** shortest known distance from the start node to this one. */
        private int dist;
        /** backpointer on path (with shortest known distance) from <br>
         * start node to this one */
        private Node bkptr;

        /** Constructor: an instance with dist d from the start node and<br>
         * backpointer p. */
        private Info(Node p, int d) {
            dist= d;     // Distance from start node to this one.
            bkptr= p;    // Backpointer on the path (null if start node)
        }

        /** = a representation of this instance. */
        @Override
        public String toString() {
            return "dist " + dist + ", bckptr " + bkptr;
        }
    }

    /** = the path from the start node to node end.<br>
     * Precondition: mapSF contains all the necessary information about<br>
     * ............. the path. */
    public static List<Node> path(HashMap<Node, Info> mapSF, Node end) {
        List<Node> path= new LinkedList<>();
        Node p= end;
        // invariant: All the nodes from p's successor to node last are in
        // path, in reverse order.
        while (p != null) {
            path.add(0, p);
            p= mapSF.get(p).bkptr;
        }
        return path;
    }

    /** = the sum of the weights of the edges on path p. <br>
     * Precondition: p contains at least 1 node. <br>
     * If 1 node, it's a path of length 0, i.e. with no edges. */
    public static int pathSum(List<Node> p) {
        synchronized (p) {
            Node w= null;
            int sum= 0;
            // invariant: if w is null, n is the start node of the path.<br>
            // .......... if w is not null, w is the predecessor of n on the path.
            // .......... sum = sum of weights on edges from first node to v
            for (Node n : p) {
                if (w != null) sum= sum + w.getEdge(n).length;
                w= n;
            }
            return sum;
        }
    }

}
