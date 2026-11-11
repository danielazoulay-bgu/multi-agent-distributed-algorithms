import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Logic Module for the DMS Algorithm (Max-Sum with Damping).
 * Acts as a pure "Calculator" for the agent.
 * Designed with strict Top-Down Modularity and Single Responsibility Principle.
 */
public class DMS_Logic {

    private Agent myAgent;
    private final double LAMBDA = 0.9; // Damping factor

    public List<String> connectedFunctions; 
    
    // --- State Variables (Read by the Agent after calculation) ---
    public int bestValueCandidate;
    public Map<String, double[]> currentQMessagesToSend;
    
    // Internal memory strictly for the Damping mathematical formula
    private Map<String, double[]> previousQMessages;
    
    // NEW: Symmetry Breaking Noise Vector
    private double[] noiseVector;

    public DMS_Logic(Agent agent, List<String> connectedFunctions) {
        this.myAgent = agent;
        this.connectedFunctions = connectedFunctions;
        this.previousQMessages = new HashMap<>();
        this.noiseVector = new double[agent.system.domainSize];
        
        for (String funcId : connectedFunctions) {
            this.previousQMessages.put(funcId, new double[agent.system.domainSize]);
        }
        
        // Initialize the "dust" - tiny random noise for symmetry breaking
        for (int v = 0; v < agent.system.domainSize; v++) {
            // A very small random value (e.g., 0.0000something)
            this.noiseVector[v] = agent.random.nextDouble() * 0.0001; 
        }
    }

    // =====================================================================
    // LEVEL 1: MAIN EXECUTION
    // =====================================================================

    public void calculate(Map<String, double[]> incomingRMessages) {
        this.bestValueCandidate = determineBestValue(incomingRMessages);
        this.currentQMessagesToSend = generateAllQMessages(incomingRMessages);
    }

    // =====================================================================
    // LEVEL 2: HIGH-LEVEL ORCHESTRATION
    // =====================================================================

    private int determineBestValue(Map<String, double[]> incomingRMessages) {
        double bestCost = Double.MAX_VALUE;
        int candidateValue = myAgent.value; 
        
        for (int v = 0; v < myAgent.system.domainSize; v++) {
            double costForValue = calculateTotalCostForValue(v, incomingRMessages);
            if (costForValue < bestCost) {
                bestCost = costForValue;
                candidateValue = v;
            }
        }
        return candidateValue;
    }

    private Map<String, double[]> generateAllQMessages(Map<String, double[]> incomingRMessages) {
        Map<String, double[]> newQMessages = new HashMap<>();
        
        for (String targetFuncId : connectedFunctions) {
            double[] finalQMessage = createSingleQMessage(targetFuncId, incomingRMessages);
            newQMessages.put(targetFuncId, finalQMessage);
        }
        return newQMessages;
    }

    // =====================================================================
    // LEVEL 3: MESSAGE PIPELINE
    // =====================================================================

    private double calculateTotalCostForValue(int valueIndex, Map<String, double[]> incomingRMessages) {
        // Start the sum with the tiny random noise to break ties!
        double totalCost = noiseVector[valueIndex]; 
        for (String funcId : connectedFunctions) {
            // Uses getOrDefault to safely handle Round 1 when the mailbox is empty
            double[] rVector = incomingRMessages.getOrDefault(funcId, new double[myAgent.system.domainSize]);
            totalCost += rVector[valueIndex];
        }
        return totalCost;
    }

    private double[] createSingleQMessage(String targetFuncId, Map<String, double[]> incomingRMessages) {
        double[] rawQ = buildRawQVector(targetFuncId, incomingRMessages);
        normalizeVector(rawQ);
        return applyDamping(targetFuncId, rawQ);
    }

    // =====================================================================
    // LEVEL 4: CORE MATHEMATICAL OPERATIONS
    // =====================================================================

    private double[] buildRawQVector(String targetFuncId, Map<String, double[]> incomingRMessages) {
        double[] rawQ = new double[myAgent.system.domainSize];
        for (int v = 0; v < myAgent.system.domainSize; v++) {
            rawQ[v] = sumRMessagesExcludingTarget(v, targetFuncId, incomingRMessages);
        }
        return rawQ;
    }

    private void normalizeVector(double[] vector) {
        double minVal = findMinimum(vector);
        for (int i = 0; i < vector.length; i++) {
            vector[i] -= minVal;
        }
    }

    private double[] applyDamping(String targetFuncId, double[] rawQ) {
        double[] qOld = this.previousQMessages.get(targetFuncId);
        double[] qDamped = new double[myAgent.system.domainSize];
        
        for (int v = 0; v < myAgent.system.domainSize; v++) {
            qDamped[v] = (LAMBDA * qOld[v]) + ((1.0 - LAMBDA) * rawQ[v]);
        }
        
        this.previousQMessages.put(targetFuncId, qDamped);
        return qDamped;
    }

    // =====================================================================
    // LEVEL 5: UTILITY / MICRO-OPERATIONS
    // =====================================================================

    private double sumRMessagesExcludingTarget(int valueIndex, String targetFuncId, Map<String, double[]> incomingRMessages) {
        // Start the sum with the tiny random noise to break ties!
        double sum = noiseVector[valueIndex]; 
        for (String otherFuncId : connectedFunctions) {
            if (!otherFuncId.equals(targetFuncId)) {
                // Uses getOrDefault to safely handle Round 1 when the mailbox is empty
                double[] rVector = incomingRMessages.getOrDefault(otherFuncId, new double[myAgent.system.domainSize]);
                sum += rVector[valueIndex];
            }
        }
        return sum;
    }

    private double findMinimum(double[] vector) {
        double minVal = Double.MAX_VALUE;
        for (double val : vector) {
            if (val < minVal) minVal = val;
        }
        return minVal;
    }
}