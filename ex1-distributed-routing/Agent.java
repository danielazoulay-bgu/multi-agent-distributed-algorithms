import java.util.ArrayList;
import java.util.List;

/**
 * Base Agent class for IIOT Routing Simulation.
 * Provides the foundation for both Synchronous and Asynchronous implementations.
 */
public abstract class Agent {
    public int id;
    public List<Agent> neighbors;
    public RoutingTable table;

    public Agent(int id) {
        this.id = id;
        this.neighbors = new ArrayList<>();
        // Initialize the table and add the self-route (distance 0)
        this.table = new RoutingTable(id); 
    }

    /**
     * Processes a RoutingTable received from a neighbor.
     * @param neighborId The ID of the neighbor who sent the data.
     * @param receivedTable The table snapshot from the neighbor.
     * @return true if the local table was modified (discovered a new or shorter path).
     */
    public boolean update(int neighborId, RoutingTable receivedTable) {
        boolean wasChanged = false;

        for (RoutingRow receivedRow : receivedTable.getRows()) {
            int targetId = receivedRow.targetId;
            
            // Skip updates about ourselves
            if (targetId == this.id) continue;

            int newDist = receivedRow.distance + 1;
            RoutingRow myRow = table.getRowByTarget(targetId);

            if (myRow == null) {
                // Rule 1: Discovery - Path found to a previously unknown node
                table.addRow(targetId, newDist, neighborId);
                wasChanged = true;
            } 
            else if (newDist < myRow.distance) {
                // Rule 2: Optimization - A strictly shorter path was found
                myRow.distance = newDist;
                myRow.nextHop = neighborId;
                wasChanged = true;
            }
        }
        return wasChanged;
    }

    public void addNeighbor(Agent n) { 
        neighbors.add(n); 
    }
    
    /**
     * Prepares a thread-safe deep copy of the table for transmission.
     * The copy includes the current eventCounter for critical path tracking.
     */
    public RoutingTable getTableForSending() { 
        return table.copy(); 
    }
}