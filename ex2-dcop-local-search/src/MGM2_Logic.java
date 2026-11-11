import java.util.List;

/**
 * Logic Module for the MGM-2 Algorithm.
 * Acts as the "Brain" for both Synchronous and Asynchronous agents.
 * Calculates optimal assignments and manages its own internal state.
 */
public class MGM2_Logic {
    
    private Agent myAgent;
    private MGM_Logic singleAgentCalculator;

    // --- State Variables ---
    // These variables hold the results of the latest calculation phase.
    // The Agent reads these directly after commanding the Brain to calculate.
    public int currentGain;
    public int bestValueCandidate;
    public int partnerCandidateValue;

    public MGM2_Logic(Agent agent) {
        this.myAgent = agent;
        this.singleAgentCalculator = new MGM_Logic(agent);
    }

    /**
     * Phase 1 Logic: Decides whether to offer a partnership and selects a random neighbor.
     * @return The ID of the targeted neighbor, or -1 if no offer is made.
     */
    public int decideOffer() {
        if (myAgent.random.nextDouble() < 0.5 && !myAgent.neighbors.isEmpty()) {
            return myAgent.neighbors.get(myAgent.random.nextInt(myAgent.neighbors.size())).id;
        }
        return -1;
    }

    /**
     * Phase 2 Logic: Selects one partner randomly from a list of received offers.
     */
    public int selectPartner(List<Integer> receivedOffers) {
        if (!receivedOffers.isEmpty()) {
            return receivedOffers.get(myAgent.random.nextInt(receivedOffers.size()));
        }
        return -1;
    }

    /**
     * Phase 3 Logic (Paired): Calculates the maximum joint gain for this agent and its partner.
     * Evaluates all domain combinations (v1, v2) to find the optimal global cost reduction.
     * Updates the internal state variables with the optimal configuration.
     */
    public void calculateJointGain(Agent partner) {
        // Calculate current baseline cost
        int currentJointCost = myAgent.getLocalCost() + partner.getLocalCost() - 
                               myAgent.system.getConstraintCost(myAgent.id, partner.id, myAgent.value, partner.value);

        int bestCost = currentJointCost;
        int bestVal1 = myAgent.value;
        int bestVal2 = partner.value;

        // Iterate through all possible joint value combinations
        for (int v1 = 0; v1 < myAgent.system.domainSize; v1++) {
            for (int v2 = 0; v2 < myAgent.system.domainSize; v2++) {
                int totalCost = calculateTotalJointCost(partner, v1, v2);
                
                // Track strictly better joint configurations
                if (totalCost < bestCost) {
                    bestCost = totalCost;
                    bestVal1 = v1;
                    bestVal2 = v2;
                }
            }
        }
        
        // Update Brain's state variables for the Agent to read
        this.bestValueCandidate = bestVal1;
        this.partnerCandidateValue = bestVal2;
        this.currentGain = currentJointCost - bestCost;
    }

    /**
     * Phase 3 Logic (Single): Calculates gain independently if the agent is not paired.
     * Delegates the math to the existing MGM_Logic instance and copies the results over.
     */
    public void calculateSingleGain() {
        this.singleAgentCalculator.calculateSingleGain();
        
        // Align state variables so the Agent always reads from the same place
        this.currentGain = this.singleAgentCalculator.currentGain;
        this.bestValueCandidate = this.singleAgentCalculator.bestValueCandidate;
        this.partnerCandidateValue = -1; // Irrelevant for a single agent
    }

    /**
     * Helper Method: Calculates the theoretical cost of a specific joint assignment.
     */
    private int calculateTotalJointCost(Agent partner, int v1, int v2) {
        int cost1 = 0; 
        for(Agent n : myAgent.neighbors) {
            if(n.id == partner.id) continue;
            cost1 += myAgent.system.getConstraintCost(myAgent.id, n.id, v1, myAgent.neighborValues.get(n.id));
        }
        
        int cost2 = 0; 
        for(Agent n : partner.neighbors) {
            if(n.id == myAgent.id) continue;
            cost2 += myAgent.system.getConstraintCost(partner.id, n.id, v2, partner.neighborValues.get(n.id));
        }
        
        // Return sum of external costs plus the internal constraint between the partners
        return cost1 + cost2 + myAgent.system.getConstraintCost(myAgent.id, partner.id, v1, v2);
    }
}