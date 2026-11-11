import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Asynchronous Agent implementation for IIOT routing simulation.
 * Each agent runs in a dedicated thread, utilizing Lamport Logical Clocks 
 * to track the Critical Path Depth and Bellman-Ford for route optimization.
 */
public class AsyncAgent extends Agent implements Runnable {
    private final List<RoutingTable> manualQueue = new ArrayList<>();
    private volatile boolean isRunning = true;
    
    // Static counters for Quiescence Detection (Global Termination Detection)
    public static AtomicInteger messagesInFlight = new AtomicInteger(0);
    public static AtomicInteger activeWorkers = new AtomicInteger(0);

    // Local logical clock representing the causal depth of updates
    private int eventCounter = 0;

    public AsyncAgent(int id) {
        super(id);
    }

    /**
     * Main execution loop. Processes incoming routing tables from the queue,
     * updates local routes, increments logical clocks, and propagates changes.
     */
    @Override
    public void run() {
        // Initial state propagation
        broadcastToNeighbors();

        while (isRunning) {
            RoutingTable receivedTable = null;

            // Wait for messages in the queue (Thread-safe block)
            synchronized (manualQueue) {
                while (manualQueue.isEmpty() && isRunning) {
                    try {
                        manualQueue.wait();
                    } catch (InterruptedException e) {
                        isRunning = false;
                    }
                }
                if (isRunning && !manualQueue.isEmpty()) {
                    receivedTable = manualQueue.remove(0);
                }
            }

            if (receivedTable != null) {
                activeWorkers.incrementAndGet(); // Mark agent as busy
                
                try {
                    int senderId = receivedTable.getOwnerId(); 

                    // Apply Bellman-Ford update logic
                    if (this.update(senderId, receivedTable)) {
                        
                        /* * LAMPORT CLOCK LOGIC: 
                         * local_depth = max(local_depth, message_depth + 1)
                         * Ensures we track the longest chain of causal dependencies.
                         */
                        int incomingDepth = receivedTable.getEventCounter();
                        this.eventCounter = Math.max(this.eventCounter, incomingDepth + 1);

                        // Synchronize the table's internal counter for reporting
                        this.table.setEventCounter(this.eventCounter);               

                        // Trigger propagation of the improved routing state
                        broadcastToNeighbors();
                    }
                } finally {
                    messagesInFlight.decrementAndGet();
                    activeWorkers.decrementAndGet();
                }
            }
        }
    }

    /**
     * Enqueues an incoming routing table and notifies the agent thread.
     * @param receivedTable The routing table snapshot sent by a neighbor.
     */
    public void receiveData(RoutingTable receivedTable) {
        synchronized (manualQueue) {
            manualQueue.add(receivedTable);
            manualQueue.notifyAll(); // Wake up the processing thread
        }
    }

    /**
     * Creates a snapshot of the current local table, attaches the 
     * current logical depth, and sends it to all adjacent neighbors.
     */
    private void broadcastToNeighbors() {
        RoutingTable snapshot = this.table.copy();
        snapshot.setEventCounter(this.eventCounter);
        
        messagesInFlight.addAndGet(neighbors.size());
        
        for (Agent neighbor : neighbors) {
            ((AsyncAgent) neighbor).receiveData(snapshot);
        }
    }
    
    /**
     * Gracefully signals the thread to stop execution.
     */
    public void stop() {
        this.isRunning = false;
        synchronized (manualQueue) {
            manualQueue.notifyAll();
        }
    }

    /**
     * @return The final causal depth (Lamport value) reached by this agent.
     */
    public int getEventCounter() {
        return eventCounter;
    }
}