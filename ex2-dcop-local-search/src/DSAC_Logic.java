public class DSAC_Logic {
    
    private Agent myAgent;
    
    public int bestValueCandidate;
    public int bestCostCalculated;
    public int currentCostBeforeChange;

    /**
     * Constructs a new DSA-C logic module for a specific agent.
     */
    public DSAC_Logic(Agent agent) {
        this.myAgent = agent;
    }

    /**
     * Step 1: Evaluates all domain values to find the optimal local assignment.
     * (Previously named 'calculate')
     */
    public void findBestLocalAssignment() {
        this.currentCostBeforeChange = myAgent.getLocalCost();
        this.bestCostCalculated = this.currentCostBeforeChange;
        this.bestValueCandidate = myAgent.value;

        for (int v = 0; v < myAgent.system.domainSize; v++) {
            if (v == myAgent.value) continue;
            
            int tempCost = calculateCostForValue(v);
            
            if (tempCost < this.bestCostCalculated) {
                this.bestCostCalculated = tempCost;
                this.bestValueCandidate = v;
            }
        }
    }

    /**
     * Helper method to calculate the theoretical cost of a specific candidate value 
     * against the current known states of all neighbors.
     */
    private int calculateCostForValue(int candidateValue) {
        int tempCost = 0;
        for (Agent neighbor : myAgent.neighbors) {
            int neighborVal = myAgent.neighborValues.get(neighbor.id);
            tempCost += myAgent.system.getConstraintCost(myAgent.id, neighbor.id, candidateValue, neighborVal);
        }
        return tempCost;
    }

    /**
     * Step 2: Implements the stochastic decision-making rule of DSA-C.
     */
    public boolean shouldChange(double p) {
        if (this.bestCostCalculated < this.currentCostBeforeChange) {
            return (myAgent.random.nextDouble() < p);
        }
        return false;
    }
}