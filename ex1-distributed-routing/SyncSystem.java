import java.util.List;

/**
 * Manager for the Synchronous simulation.
 * Uses inherited topology logic from AgentSystem.
 */
public class SyncSystem extends AgentSystem {

    public SyncSystem(int numOfAgents, List<List<Integer>> topology) {
        // We pass 0 for 'p' because we use a manual topology
        super(numOfAgents, 0);
        this.agents.clear(); 
        createAgents();
        // Simply call the parent method to connect the neighbors
        applyTopology(topology);
    }

    @Override
    public void createAgents() {
        for (int i = 0; i < numOfAgents; i++) {
            this.agents.add(new SyncAgent(i));
        }
    }
}