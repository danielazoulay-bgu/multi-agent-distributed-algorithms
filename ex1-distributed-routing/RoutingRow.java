/**
 * Represents a single row in the routing table.
 * Contains the target ID, distance to target, and the next-hop agent.
 */
public class RoutingRow {
    public int targetId; // Column 1: Destination Agent ID
    public int distance; // Column 2: Distance to destination
    public int nextHop;  // Column 3: The agent through whom the message is sent

    public RoutingRow(int targetId, int distance, int nextHop) {
        this.targetId = targetId;
        this.distance = distance;
        this.nextHop = nextHop;
    }

    @Override
    public String toString() {
        return String.format("Target: %d | Distance: %d | Next Hop: %d", 
                             targetId, distance, nextHop);
    }
}