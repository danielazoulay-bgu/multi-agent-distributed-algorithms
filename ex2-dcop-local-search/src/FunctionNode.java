import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a Function Node (Constraint) in the Factor Graph for Max-Sum (DMS).
 */
public class FunctionNode {
    
    public String functionId;
    public List<Integer> scope;
    public AgentSystem system;
    public Map<Integer, double[]> incomingQ;

    /**
     * Initializes the function node and zero-vectors for incoming Q messages.
     */
    public FunctionNode(String functionId, List<Integer> scope, AgentSystem system) {
        this.functionId = functionId;
        this.scope = scope;
        this.system = system;
        this.incomingQ = new HashMap<>();
        
        for (int agentId : scope) {
            this.incomingQ.put(agentId, new double[system.domainSize]);
        }
    }

    /**
     * Stores the Q message sent from a specific variable (agent) to this function.
     */
    public void receiveQMessage(int agentId, double[] qVector) {
        this.incomingQ.put(agentId, qVector.clone());
    }

    /**
     * Calculates and returns the R messages to be sent to all variables in the scope.
     */
    public Map<Integer, double[]> calculateRMessages() {
        Map<Integer, double[]> rMessages = new HashMap<>();

        for (int targetAgentId : scope) {
            double[] rVector = calculateRVectorForTarget(targetAgentId);
            rMessages.put(targetAgentId, rVector);
        }
        return rMessages;
    }

    /**
     * Helper method to calculate the specific R vector for a target agent.
     */
    private double[] calculateRVectorForTarget(int targetAgentId) {
        double[] rVector = new double[system.domainSize];
        List<Integer> otherAgents = new ArrayList<>(scope);
        otherAgents.remove((Integer) targetAgentId);

        for (int vTarget = 0; vTarget < system.domainSize; vTarget++) {
            Map<Integer, Integer> currentAssignment = new HashMap<>();
            rVector[vTarget] = findMinCostRecursive(targetAgentId, vTarget, otherAgents, 0, currentAssignment);
        }
        
        return rVector;
    }

    /**
     * Recursive function to evaluate all combinations of neighbor values.
     */
    private double findMinCostRecursive(int targetAgentId, int targetVal, List<Integer> otherAgents, int depth, Map<Integer, Integer> assignment) {
        if (depth == otherAgents.size()) {
            assignment.put(targetAgentId, targetVal);
            return calculateLeafCost(assignment, otherAgents);
        }

        return exploreNextAgentValues(targetAgentId, targetVal, otherAgents, depth, assignment);
    }

    /**
     * Evaluates the total cost at the leaf of the recursion tree (Base Case).
     */
    private double calculateLeafCost(Map<Integer, Integer> assignment, List<Integer> otherAgents) {
        double totalCost = evaluateConstraintCost(assignment);
        
        for (int otherId : otherAgents) {
            int assignedValue = assignment.get(otherId);
            totalCost += incomingQ.get(otherId)[assignedValue];
        }
        return totalCost;
    }

    /**
     * Iterates through domain values to continue the recursion (Recursive Step).
     */
    private double exploreNextAgentValues(int targetAgentId, int targetVal, List<Integer> otherAgents, int depth, Map<Integer, Integer> assignment) {
        int currentAgentId = otherAgents.get(depth);
        double minCost = Double.MAX_VALUE;
        
        for (int v = 0; v < system.domainSize; v++) {
            assignment.put(currentAgentId, v);
            double cost = findMinCostRecursive(targetAgentId, targetVal, otherAgents, depth + 1, assignment);
            if (cost < minCost) {
                minCost = cost;
            }
        }
        return minCost;
    }

    /**
     * Queries the system for the specific constraint cost.
     */
    private double evaluateConstraintCost(Map<Integer, Integer> assignment) {
        if (scope.size() == 2) {
            int id1 = scope.get(0);
            int id2 = scope.get(1);
            return system.getConstraintCost(id1, id2, assignment.get(id1), assignment.get(id2));
        }
        return 0; 
    }
}