import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Abstract Base Class for DCOP Agent Systems.
 */
public abstract class AgentSystem {
    
    public int numOfAgents;
    public double p;
    public int domainSize;
    public int maxCost;
    public List<Agent> agents;
    public Map<String, int[][]> constraints;
    private Random random;

    /**
     * Initializes the system parameters and data structures.
     */
    public AgentSystem(int numOfAgents, double p, int domainSize, int maxCost, long seed) {
        this.numOfAgents = numOfAgents;
        this.p = p;
        this.domainSize = domainSize;
        this.maxCost = maxCost;
        this.random = new Random(seed);
        
        this.agents = new ArrayList<>();
        this.constraints = new HashMap<>();
    }

    /**
     * Creates agents, sets up network topology, and generates constraints.
     */
    public void initializeSystem(long seed) {
        createAgents(seed);
        createNetworkAndConstraints();
    }

    /**
     * Generates the network topology and creates the cost matrices for connected agents.
     */
    public void createNetworkAndConstraints() {
        for (int i = 0; i < numOfAgents; i++) {
            for (int j = i + 1; j < numOfAgents; j++) {
                if (random.nextDouble() < this.p) {
                    connectAgents(i, j);
                    
                    int[][] costMatrix = new int[domainSize][domainSize];
                    for (int val1 = 0; val1 < domainSize; val1++) {
                        for (int val2 = 0; val2 < domainSize; val2++) {
                            costMatrix[val1][val2] = random.nextInt(maxCost) + 1;
                        }
                    }
                    constraints.put(i + "_" + j, costMatrix);
                }
            }
        }
    }

    /**
     * Connects two agents by adding them to each other's neighbor lists.
     */
    private void connectAgents(int id1, int id2) {
        Agent a1 = agents.get(id1);
        Agent a2 = agents.get(id2);
        a1.addNeighbor(a2);
        a2.addNeighbor(a1);
    }

    /**
     * Retrieves the symmetric constraint cost between two agents.
     */
    public int getConstraintCost(int id1, int id2, int val1, int val2) {
        int minId = Math.min(id1, id2);
        int maxId = Math.max(id1, id2);
        int[][] matrix = constraints.get(minId + "_" + maxId);
        
        if (id1 == minId) {
            return matrix[val1][val2];
        } else {
            return matrix[val2][val1];
        }
    }

    /**
     * Abstract method to instantiate the specific type of agents.
     */
    public abstract void createAgents(long seed);
    
    /**
     * Computes the total global cost of the current system configuration.
     */
    public int getGlobalCost() {
        int globalCost = 0;
        for (Agent a : this.agents) {
            globalCost += a.getLocalCost();
        }
        return globalCost / 2;
    }
    
}