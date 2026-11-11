/**
 * Logic Module for the MGM (Maximum Gain Message) Algorithm.
 * Handles the calculation of gains and the decision-making logic 
 * for both single-agent and joint-agent configurations.
 */
public class MGM_Logic {
    
    private Agent myAgent;

    public int bestValueCandidate;
    public int currentGain;

    public MGM_Logic(Agent agent) {
        this.myAgent = agent;
    }

    /**
     * Calculates the best local gain for a single agent.
     */
    public void calculateSingleGain() {
        int currentCost = myAgent.getLocalCost();
        int bestCost = currentCost;
        this.bestValueCandidate = myAgent.value;

        for (int v = 0; v < myAgent.system.domainSize; v++) {
            if (v == myAgent.value) continue;
            
            int tempCost = 0;
            for (Agent neighbor : myAgent.neighbors) {
                int neighborVal = myAgent.neighborValues.get(neighbor.id);
                tempCost += myAgent.system.getConstraintCost(myAgent.id, neighbor.id, v, neighborVal);
            }
            
            if (tempCost < bestCost) {
                bestCost = tempCost;
                this.bestValueCandidate = v;
            }
        }
        this.currentGain = currentCost - bestCost;
    }

    /**
     * Decides if the agent is the "Winner" among neighbors based on gain comparison.
     */
    public boolean isWinner(int myGain, int neighborId, int neighborGain) {
        if (myGain < neighborGain) return false;
        if (myGain == neighborGain && myAgent.id > neighborId) return false;
        return true;
    }
}