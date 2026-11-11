import java.util.ArrayList;
import java.util.List;

public class RoutingTable {
    private List<RoutingRow> rows;
    private int ownerId;
    // Logical counter to track the critical path depth
    private int eventCounter;

    /**
     * Standard constructor for a new routing table.
     * @param ownerId The ID of the agent owning this table.
     */
    public RoutingTable(int ownerId) {
        this.ownerId = ownerId;
        this.rows = new ArrayList<>();
        this.eventCounter = 0; 
        this.rows.add(new RoutingRow(ownerId, 0, ownerId));
    }

    /**
     * Private constructor used for deep copying.
     * @param ownerId The ID of the agent.
     * @param rows The list of routing rows.
     * @param eventCounter The current causal depth value.
     */
    private RoutingTable(int ownerId, List<RoutingRow> rows, int eventCounter) {
        this.ownerId = ownerId;
        this.rows = rows;
        this.eventCounter = eventCounter;
    }

    /**
     * Creates a deep copy of the routing table to ensure thread safety.
     * The eventCounter is preserved in the copied instance.
     * @return A new RoutingTable instance with identical data.
     */
    public RoutingTable copy() {
        List<RoutingRow> copiedRows = new ArrayList<>();
        for (RoutingRow r : this.rows) {
            copiedRows.add(new RoutingRow(r.targetId, r.distance, r.nextHop));
        }
        return new RoutingTable(this.ownerId, copiedRows, this.eventCounter);
    }

    // --- Counter Management ---

    public int getEventCounter() {
        return eventCounter;
    }

    public void setEventCounter(int eventCounter) {
        this.eventCounter = eventCounter;
    }

    // --- Table Management ---

    public List<RoutingRow> getRows() {
        return rows;
    }

    public RoutingRow getRowByTarget(int targetId) {
        for (RoutingRow r : rows) {
            if (r.targetId == targetId) return r;
        }
        return null;
    }

    public void addRow(int targetId, int distance, int nextHop) {
        rows.add(new RoutingRow(targetId, distance, nextHop));
    }
    
    public int getOwnerId() {
        return ownerId;
    }


    /**
     * Prints the routing table content and the current causal depth.
     */
    public void printTable() {
        System.out.println("Table for Agent " + ownerId + " (Causal Depth: " + eventCounter + "):");
        for (RoutingRow r : rows) {
            System.out.println("  " + r);
        }
    }
}