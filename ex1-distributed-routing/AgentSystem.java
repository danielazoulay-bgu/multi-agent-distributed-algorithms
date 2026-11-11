import java.util.ArrayList;
import java.util.List;

/**
 * Abstract Base Class for Agent Systems.
 * Coordinates agent creation and network topology management.
 */
public abstract class AgentSystem {
    public int numOfAgents;
    public double p;
    public List<Agent> agents;

    /**
     * Standard constructor.
     * @param numOfAgents Total number of agents in the network.
     * @param p Neighbor probability (used for legacy random generation).
     */
    public AgentSystem(int numOfAgents, double p) {
        this.numOfAgents = numOfAgents;
        this.p = p;
        this.agents = new ArrayList<>();
    }

    /**
     * Legacy method: Creates neighbors based on probability p.
     */
    public void createNeighbors() {
        for (int i = 0; i < numOfAgents; i++) {
            for (int j = i + 1; j < numOfAgents; j++) {
                if (Math.random() < this.p) {
                    connectAgents(i, j);
                }
            }
        }
    }

    /**
     * New Shared Topology method: Connects agents based on a pre-defined list.
     * This ensures fairness between different simulation modes.
     * @param topology An adjacency list where topology.get(i) contains neighbor IDs for agent i.
     */
    public void applyTopology(List<List<Integer>> topology) {
        for (int i = 0; i < topology.size(); i++) {
            for (Integer neighborId : topology.get(i)) {
                // Ensure we only connect each pair once to avoid duplicates
                if (i < neighborId) {
                    connectAgents(i, neighborId);
                }
            }
        }
    }

    /**
     * Helper method to establish a bidirectional link between two agents.
     */
    private void connectAgents(int id1, int id2) {
        Agent a1 = agents.get(id1);
        Agent a2 = agents.get(id2);
        a1.addNeighbor(a2);
        a2.addNeighbor(a1);
    }

    /**
     * Displays the current network structure in the console.
     */
    public void printNetwork() {
        System.out.println("\n--- Network Topology ---");
        for (Agent agent : agents) {
            System.out.print("Agent " + agent.id + " neighbors: [");
            for (int i = 0; i < agent.neighbors.size(); i++) {
                System.out.print(agent.neighbors.get(i).id + (i < agent.neighbors.size() - 1 ? ", " : ""));
            }
            System.out.println("]");
        }
        System.out.println("------------------------\n");
    }
    
    public abstract void createAgents();
}