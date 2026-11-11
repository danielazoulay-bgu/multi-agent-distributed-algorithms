import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Base Agent class for the DCOP (Distributed Constraint Optimization Problem) Simulation.
 * Serves as the foundation for both Synchronous and Asynchronous agents.
 */
public abstract class Agent {
    
    public int id;
    public List<Agent> neighbors;
    
    // --- DCOP Specific Fields ---
    public int value;                                // The current value (color/assignment) chosen by this agent
    public Map<Integer, Integer> neighborValues;     // Tracks the latest known values of all immediate neighbors
    protected AgentSystem system;                    // Reference to the main system to query constraint costs
    protected Random random;
    
    /**
     * Standard constructor for a DCOP agent.
     * * @param id     The unique identifier for this agent.
     * @param system Reference to the governing agent system.
     * @param seed   The base seed for random operations to ensure reproducibility.
     */
    public Agent(int id, AgentSystem system, long seed) {
        this.id = id;
        this.system = system;
        this.neighbors = new ArrayList<>();
        this.neighborValues = new HashMap<>();
        
        // Initialize the class-level Random instance using a unique seed per agent
        // (Base seed + agent ID ensures each agent has a unique but deterministic random sequence)
        this.random = new Random(seed + id); 
        
        // Assign a random initial value based on the available domain size
        this.value = this.random.nextInt(system.domainSize);
    }

    /**
     * Adds a physical neighbor to this agent's network and initializes our belief of their value.
     * * @param n The neighbor Agent object to add.
     */
    public void addNeighbor(Agent n) { 
        neighbors.add(n); 
        
        // Default initial assumption for neighbor's value is 0 (will be accurately updated via first messages)
        neighborValues.put(n.id, 0); 
    }
    
    /**
     * Calculates the total constraint cost this agent is currently paying 
     * based on its own assignment and the known assignments of its neighbors.
     * * @return The sum of all local constraint costs.
     */
    public int getLocalCost() {
        int totalCost = 0;
        for (Agent neighbor : neighbors) {
            int neighborVal = neighborValues.get(neighbor.id);
            totalCost += system.getConstraintCost(this.id, neighbor.id, this.value, neighborVal);
        }
        return totalCost;
    }

    /**
     * Updates the recorded value of a specific neighbor in the local memory.
     * This is triggered whenever an agent receives a state-update message from a neighbor.
     * * @param neighborId    The ID of the neighbor who sent the update.
     * @param neighborValue The new value chosen by that neighbor.
     */
    public void updateNeighborValue(int neighborId, int neighborValue) {
        neighborValues.put(neighborId, neighborValue);
    }
}