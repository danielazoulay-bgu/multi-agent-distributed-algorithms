import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manager class for the Asynchronous DCOP simulation.
 * Uses a combined State-Based Termination (MGM/DSA), Lamport Clock limits (DMS), 
 * and Deadlock Protection.
 */
public class AsyncSystem extends AgentSystem {
    
    private List<Thread> agentThreads = new ArrayList<>();
    
    // Max-Sum Function Nodes map (Bipartite Graph Post Office)
    private Map<String, FunctionNode> asyncFunctionNodes;
    
    // History matrix: [agentId][logicalTime]
    public int[][] costHistory;
    private int[] lastRecordedTime;

    private int trueConvergenceTime = 0;

    public AsyncSystem(int numOfAgents, double p, int domainSize, int maxCost, long seed) {
        super(numOfAgents, p, domainSize, maxCost, seed);
        this.costHistory = new int[numOfAgents][1001];
        this.lastRecordedTime = new int[numOfAgents];
        this.initializeSystem(seed);
    }

    @Override
    public void createAgents(long seed) {
        for (int i = 0; i < numOfAgents; i++) {
            this.agents.add(new AsyncAgent(i, this, seed));
        }
    }

    /**
     * Safely records the cost of an agent at a specific logical time.
     * Fills any chronological gaps to maintain a continuous history for graphing.
     */
    public synchronized void recordCost(int agentId, int logicalTime, int cost) {
        if (logicalTime > 1000) {
            logicalTime = 1000;
        }
        
        for (int t = lastRecordedTime[agentId] + 1; t <= logicalTime; t++) {
            costHistory[agentId][t] = cost;
        }
        lastRecordedTime[agentId] = logicalTime;
    }

    /**
     * Retrieves the aggregated global cost of the system at a given logical time.
     */
    public int getGlobalCostAtTime(int time) {
        int total = 0;
        for (int i = 0; i < numOfAgents; i++) {
            total += costHistory[i][time];
        }
        return total / 2;
    }

    /**
     * Agent calls this when they actually win and change a physical value, 
     * updating the true convergence time of the system.
     */
    public synchronized void reportActualChange(int logicalTime) {
        if (logicalTime > this.trueConvergenceTime) {
            this.trueConvergenceTime = logicalTime;
        }
    }

    // ==========================================
    // DMS (Max-Sum) Infrastructure Support
    // ==========================================

    /**
     * Initializes all Function Nodes BEFORE any agent threads start.
     * Prevents NullPointerExceptions caused by asynchronous racing.
     */
    public void initializeAsyncDMS() {
        this.asyncFunctionNodes = new HashMap<>();
        
        for (Agent a : this.agents) {
            List<String> myFunctions = new ArrayList<>();
            for (Agent neighbor : a.neighbors) {
                int minId = Math.min(a.id, neighbor.id);
                int maxId = Math.max(a.id, neighbor.id);
                String funcId = minId + "_" + maxId;
                myFunctions.add(funcId);
                
                if (!asyncFunctionNodes.containsKey(funcId)) {
                    List<Integer> scope = new ArrayList<>();
                    scope.add(minId);
                    scope.add(maxId);
                    asyncFunctionNodes.put(funcId, new FunctionNode(funcId, scope, this));
                }
            }
            ((AsyncAgent) a).setupDMSBrain(myFunctions);
        }
    }

    /**
     * The "Postman" mechanism for Max-Sum.
     * Orchestrates receiving a Q message and routing the resulting R messages.
     */
    public void routeQMessageAsync(String funcId, int senderId, double[] qMsg, int senderClock) {
        FunctionNode fn = asyncFunctionNodes.get(funcId);
        if (fn == null) return; 

        // 1. Calculate the new R messages safely
        Map<Integer, double[]> outgoingR = processQAndCalculateR(fn, senderId, qMsg);

        // 2. Dispatch the R messages to the relevant target agents
        dispatchRMessages(funcId, senderId, outgoingR, senderClock);
    }

    /**
     * Synchronizes ONLY the calculation phase to prevent thread freezing (Storm prevention).
     */
    private Map<Integer, double[]> processQAndCalculateR(FunctionNode fn, int senderId, double[] qMsg) {
        synchronized(fn) { 
            fn.receiveQMessage(senderId, qMsg);
            return fn.calculateRMessages();
        }
    }

    /**
     * Routes the calculated R messages outside of the synchronized block to maintain high concurrency.
     */
    private void dispatchRMessages(String funcId, int senderId, Map<Integer, double[]> outgoingR, int senderClock) {
        for (Map.Entry<Integer, double[]> entry : outgoingR.entrySet()) {
            int targetAgentId = entry.getKey();
            
            // In standard Max-Sum, a message from X to F doesn't bounce back to X immediately
            if (targetAgentId == senderId) continue; 

            AsyncAgent targetAgent = (AsyncAgent) this.agents.get(targetAgentId);
            if (targetAgent != null) {
                AsyncAgent.messagesInFlight.incrementAndGet();
                targetAgent.receiveMessageDMSAsync(funcId, entry.getValue().clone(), senderClock);
            }
        }
    }

    // ==========================================
    // Core Simulation Engine
    // ==========================================

    /**
     * Main entry point for the asynchronous simulation.
     * Orchestrates setup, execution, and termination monitoring.
     */
    public void runSimulation(String algorithmName) {
        System.out.println("AsyncSystem: Preparing agents for " + algorithmName + "...");
        
        prepareAgents(algorithmName);
        
        if (algorithmName.equals("DMS")) {
            initializeAsyncDMS();
        }

        resetGlobalCounters();
        startAgentThreads();
        
        System.out.println("AsyncSystem: Sparking the algorithm...");
        kickstartAlgorithms(algorithmName);
        
        monitorTermination(algorithmName);
        finalizeSimulation();
    }

    /**
     * Assigns the chosen algorithm to all agents.
     */
    private void prepareAgents(String algorithmName) {
        for (Agent a : this.agents) {
            ((AsyncAgent) a).setAlgorithm(algorithmName);
        }
    }

    /**
     * Resets all global tracking counters before starting a new run.
     */
    private void resetGlobalCounters() {
        AsyncAgent.messagesInFlight.set(0);
        AsyncAgent.activeWorkers.set(0);
        AsyncAgent.agentsWithPositiveGain.set(0);
        AsyncAgent.realUpdatesInFlight.set(0);
        
        this.trueConvergenceTime = 0;
        this.agentThreads.clear();
    }

    /**
     * Creates and starts a thread for each agent in the system.
     */
    private void startAgentThreads() {
        for (Agent agent : agents) {
            Thread t = new Thread((AsyncAgent) agent);
            agentThreads.add(t);
            t.start();
        }
    }

    /**
     * Triggers the initial action for the agents to start the asynchronous cascade.
     */
    private void kickstartAlgorithms(String algorithmName) {
        for (Agent agent : agents) {
            if (algorithmName.equals("DMS")) {
                ((AsyncAgent) agent).initDMS();
            } else {
                ((AsyncAgent) agent).startAlgorithm();
            }
        }
    }

    /**
     * Continuously evaluates system conditions to determine when the algorithm has completed.
     */
    private void monitorTermination(String algorithmName) {
        while (true) {
            if (checkTimeLimitTermination()) break;
            if (checkPerfectConvergence(algorithmName)) break;
            if (checkNetworkQuiescence()) break;

            try { 
                Thread.sleep(10); // Let the CPU breathe
            } catch (InterruptedException e) { 
                Thread.currentThread().interrupt(); 
            }
        }
    }

    /**
     * Condition A: Termination based on Lamport Clock limits (Crucial for DMS).
     */
    private boolean checkTimeLimitTermination() {
        int minClock = 1000;
        for (Agent a : this.agents) {
            minClock = Math.min(minClock, ((AsyncAgent) a).logicalClock);
        }
        if (minClock >= 1000) {
            System.out.println("--> AsyncSystem: Termination Reached (All agents passed 1000 Lamport steps).");
            return true;
        }
        return false;
    }

    /**
     * Condition B: Perfect Logical Convergence (No agent has gain > 0). Primarily for MGM/DSA.
     */
    private boolean checkPerfectConvergence(String algorithmName) {
        if (!algorithmName.equals("DMS") && 
            AsyncAgent.agentsWithPositiveGain.get() == 0 && 
            AsyncAgent.realUpdatesInFlight.get() == 0 && 
            AsyncAgent.activeWorkers.get() == 0) {
            System.out.println("--> AsyncSystem: Perfect State-Based Convergence (No R > 0) reached!");
            return true;
        }
        return false;
    }

    /**
     * Condition C: Deadlock Protection (Messages dropped, network frozen).
     */
    private boolean checkNetworkQuiescence() {
        if (AsyncAgent.messagesInFlight.get() == 0 && 
            AsyncAgent.activeWorkers.get() == 0) {
            System.out.println("--> AsyncSystem: Network Quiescence (Deadlock/Empty Queue) reached!");
            return true;
        }
        return false;
    }

    /**
     * Finalizes the data collection and safely stops all threads.
     */
    private void finalizeSimulation() {
        System.out.println("    Actual Convergence Time: " + this.trueConvergenceTime);
        
        // Final gap fill: Ensure all agents reach time 1000 in the history array for Excel graphs
        for (Agent a : this.agents) {
            recordCost(a.id, 1000, a.getLocalCost());
        }

        stopAll();
    }

    /**
     * Instructs all agents to halt and waits for their threads to join safely.
     */
    public void stopAll() {
        for (Agent a : this.agents) {
            ((AsyncAgent) a).stop();
        }
        for (Thread t : agentThreads) {
            try { 
                t.join(); 
            } catch (InterruptedException e) { 
                e.printStackTrace(); 
            }
        }
    }
}