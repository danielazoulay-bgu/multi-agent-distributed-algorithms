import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of a Synchronous Agent.
 * Processes messages in discrete rounds and tracks causal depth.
 */
public class SyncAgent extends Agent {
    private List<RoutingTable> mailbox = new ArrayList<>();
    // Local counter to track the synchronous progress (Lamport-style)
    private int eventCounter = 0;

    public SyncAgent(int id) { 
        super(id); 
    }

    public void receiveData(int senderId, RoutingTable receivedTable) {
        mailbox.add(receivedTable);
    }

    /**
     * Processes all tables from the mailbox.
     * Updates the causal depth to match the current iteration logic.
     */
    public boolean processMailbox() {
        boolean changed = false;
        int maxReceivedDepth = 0;

        // Step 1: Find the maximum depth among all neighbors' tables
        for (RoutingTable receivedTable : mailbox) {
            if (receivedTable.getEventCounter() > maxReceivedDepth) {
                maxReceivedDepth = receivedTable.getEventCounter();
            }
        }

        // Step 2: Process the routing updates
        for (RoutingTable receivedTable : mailbox) {
            int neighborId = receivedTable.getOwnerId();
            if (this.update(neighborId, receivedTable)) {
                changed = true;
            }
        }

        // Step 3: Update the local counter and the table
        // In a sync round, the new depth is max(neighbors) + 1
        this.eventCounter = maxReceivedDepth + 1;
        this.table.setEventCounter(this.eventCounter);

        mailbox.clear(); 
        return changed;
    }
}