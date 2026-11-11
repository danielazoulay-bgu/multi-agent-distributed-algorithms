import java.util.ArrayList;
import java.util.List;

/**
 * Manager class for the Asynchronous simulation.
 * Responsible for thread lifecycle management, concurrency control, 
 * and ensuring consistent network topology application.
 */
public class AsyncSystem extends AgentSystem {
    // List to track active thread objects for lifecycle control
    private List<Thread> agentThreads = new ArrayList<>();

    /**
     * Constructor: Initializes the system using a shared topology.
     * Ensures the Asynchronous model solves the exact same graph as the Synchronous model.
     */
    public AsyncSystem(int numOfAgents, List<List<Integer>> topology) {
        super(numOfAgents, 0);
        this.agents.clear();
        createAgents();
        // Applies the pre-generated neighbor connections to all agents
        applyTopology(topology);
    }

    /**
     * Polymorphic override: Instantiates specialized AsyncAgent objects 
     * capable of executing within a dedicated Thread.
     */
    @Override
    public void createAgents() {
        for (int i = 0; i < numOfAgents; i++) {
            this.agents.add(new AsyncAgent(i));
        }
    }

    /**
     * Initiates the simulation by spawning and starting a 
     * unique thread for each agent in the system.
     */
    public void startSimulation() {
        System.out.println("AsyncSystem: Starting agent threads...");
        for (Agent agent : agents) {
            Thread t = new Thread((AsyncAgent) agent);
            agentThreads.add(t);
            t.start();
        }
    }

    /**
     * Performs a graceful shutdown of the concurrent system.
     * Stops agent loops and interrupts waiting threads for resource cleanup.
     */
    public void stopAll() {
        System.out.println("AsyncSystem: Terminating all threads...");
        for (int i = 0; i < agents.size(); i++) {
            // Signal the agent loop to stop
            ((AsyncAgent) agents.get(i)).stop();
            // Interrupt the thread to break any sleep/wait states
            agentThreads.get(i).interrupt();
        }
    }
}