import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manager for the Synchronous DCOP simulation.
 * Extends the base AgentSystem to inherit network and constraint generation.
 */
public class SyncSystem extends AgentSystem {

    private Map<String, FunctionNode> functionNodes;

    public SyncSystem(int numOfAgents, double p, int domainSize, int maxCost, long seed) {
        super(numOfAgents, p, domainSize, maxCost, seed);
        this.initializeSystem(seed);
    }

    @Override
    public void createAgents(long seed) {
        for (int i = 0; i < numOfAgents; i++) {
            this.agents.add(new SyncAgent(i, this, seed));
        }
    }
    
    /**
     * Main execution dispatcher. 
     * Runs one round of the chosen algorithm.
     * Returns true if the system HAS NOT converged yet (a change was made).
     */
    public boolean runOneRound(String algorithmName, double p) {
        if (algorithmName.equals("DSA-C")) {
            return runDSA(p);
        } else if (algorithmName.equals("MGM")) {
            return runMGM();
        } else if (algorithmName.equals("MGM-2")) {
            return runMGM2(); 
        } else if (algorithmName.equals("DMS")) {
            return runDMS();
        } else {
            System.out.println("Unknown algorithm: " + algorithmName);
            return false;
        }
    }

    // ==========================================
    // DSA-C Execution Manager
    // ==========================================
    
    /**
     * Executes one round of DSA-C: Exchange values -> Decide stochastic change.
     */
    private boolean runDSA(double p) {
        dsaPhase1ExchangeValues();
        return dsaPhase2ExecuteAndTrack(p);
    }

    /**
     * DSA Phase 1: All agents send their current values to their neighbors.
     */
    private void dsaPhase1ExchangeValues() {
        for (Agent a : this.agents) {
            SyncAgent sender = (SyncAgent) a;
            for (Agent neighbor : sender.neighbors) {
                ((SyncAgent) neighbor).receiveData(sender.id, sender.value);
            }
        }
        for (Agent a : this.agents) ((SyncAgent) a).processMailbox();
    }

    /**
     * DSA Phase 2: Agents execute their logic based on probability 'p' and update values.
     * Returns true if any agent changed its value.
     */
    private boolean dsaPhase2ExecuteAndTrack(double p) {
        boolean hasChanged = false;
        for (Agent a : this.agents) {
            SyncAgent sa = (SyncAgent) a;
            int oldVal = sa.value;
            sa.executeDSAC(p);
            if (oldVal != sa.value) hasChanged = true;
        }
        return hasChanged;
    }

    // ==========================================
    // MGM Execution Manager
    // ==========================================

    /**
     * Executes one round of MGM: Exchange values -> Calculate Gain -> Broadcast -> Decide.
     */
    private boolean runMGM() {
        mgmPhase1ExchangeValues();
        mgmPhase2CalculateGain();
        mgmPhase3BroadcastGain();
        return mgmPhase4DecisionAndTrack();
    }

    /**
     * MGM Phase 1: Agents share current states (reuses DSA logic).
     */
    private void mgmPhase1ExchangeValues() {
        dsaPhase1ExchangeValues(); 
    }

    /**
     * MGM Phase 2: Each agent independently calculates its maximum possible local gain.
     */
    private void mgmPhase2CalculateGain() {
        for (Agent a : this.agents) ((SyncAgent) a).prepareMGM();
    }

    /**
     * MGM Phase 3: Agents send their calculated gain to all neighbors.
     */
    private void mgmPhase3BroadcastGain() {
        for (Agent a : this.agents) {
            SyncAgent sender = (SyncAgent) a;
            int myGain = sender.getCurrentGain();
            for (Agent neighbor : sender.neighbors) {
                ((SyncAgent) neighbor).receiveGain(sender.id, myGain);
            }
        }
    }

    /**
     * MGM Phase 4: Agents change values ONLY if they have the highest gain in their neighborhood.
     */
    private boolean mgmPhase4DecisionAndTrack() {
        boolean hasChanged = false;
        for (Agent a : this.agents) {
            SyncAgent sa = (SyncAgent) a;
            int oldVal = sa.value;
            sa.executeMGM();
            if (oldVal != sa.value) hasChanged = true;
        }
        return hasChanged;
    }
    
    // ==========================================
    // MGM-2 Execution Manager
    // ==========================================
    
    /**
     * Executes one round of MGM-2. It involves 5 communication phases to form pairs and act.
     */
    private boolean runMGM2() {
        mgm2Phase0ExchangeValues();
        mgm2Phase1DistributeOffers();
        mgm2Phase2AcceptOffers();
        mgm2Phase3ComputeGains();
        mgm2Phase4SyncAndBroadcast();
        return mgm2Phase5ExecuteAndTrack();
    }

    /**
     * MGM-2 Phase 0: Initial state sharing before negotiations begin.
     */
    private void mgm2Phase0ExchangeValues() {
        dsaPhase1ExchangeValues(); 
    }

    /**
     * MGM-2 Phase 1: Agents randomly decide to offer a partnership to a neighbor.
     */
    private void mgm2Phase1DistributeOffers() {
        for (Agent a : this.agents) ((SyncAgent) a).mgm2Phase1_decideOffer();
        
        for (Agent a : this.agents) {
            SyncAgent sender = (SyncAgent) a;
            int targetId = sender.getMgm2OfferTarget();
            for (Agent neighbor : sender.neighbors) {
                SyncAgent targetNeighbor = (SyncAgent) neighbor;
                boolean isOffer = (targetNeighbor.id == targetId);
                targetNeighbor.receiveOfferMessageMGM2(sender.id, isOffer);
            }
        }
    }

    /**
     * MGM-2 Phase 2: Agents that received offers choose ONE partner to accept.
     */
    private void mgm2Phase2AcceptOffers() {
        for (Agent a : this.agents) ((SyncAgent) a).mgm2Phase2_acceptOffer();
        
        for (Agent a : this.agents) {
            SyncAgent acceptor = (SyncAgent) a;
            int partnerId = acceptor.getMgm2AcceptedOffer();
            if (partnerId != -1) {
                SyncAgent partner = acceptor.getNeighborById(partnerId);
                if (partner != null) partner.receiveAcceptMGM2(acceptor.id);
            }
        }
    }

    /**
     * MGM-2 Phase 3: Single agents calculate local gain; Paired agents calculate joint gain.
     */
    private void mgm2Phase3ComputeGains() {
        for (Agent a : this.agents) ((SyncAgent) a).mgm2Phase3_calculateGain();
    }

    /**
     * MGM-2 Phase 4: Sync the joint gain within the pair, then broadcast it to ALL neighbors.
     */
    private void mgm2Phase4SyncAndBroadcast() {
        // Sync within pairs
        for (Agent a : this.agents) {
            SyncAgent sa = (SyncAgent) a;
            int partnerId = sa.getMgm2AcceptedOffer();
            if (partnerId != -1) {
                SyncAgent partner = sa.getNeighborById(partnerId);
                if (partner != null) sa.shareGainWithPartnerMGM2(partner);
            }
        }
        // Broadcast to neighbors
        for (Agent a : this.agents) {
            SyncAgent sender = (SyncAgent) a;
            int myGain = sender.getCurrentGain();
            int myRepId = sender.getMgm2RepId();
            for (Agent neighbor : sender.neighbors) {
                ((SyncAgent) neighbor).receiveGainMGM2(sender.id, myGain, myRepId);
            }
        }
    }

    /**
     * MGM-2 Phase 5: Execute the change if the agent/pair has the highest gain locally.
     */
    private boolean mgm2Phase5ExecuteAndTrack() {
        boolean hasChanged = false;
        for (Agent a : this.agents) {
            SyncAgent sa = (SyncAgent) a;
            int oldVal = sa.value;
            sa.executeMGM2();
            if (oldVal != sa.value) hasChanged = true;
        }
        return hasChanged;
    }

    // ==========================================
    // DMS Execution Manager (Max-Sum)
    // ==========================================

    /**
     * Executes one round of DMS (Max-Sum). Passes messages back and forth 
     * between Variables (Agents) and Functions (Constraints).
     */
    private boolean runDMS() {
        initializeDMSIfNeeded();
        boolean hasChanged = dmsPhase1VariablesToFunctions();
        dmsPhase2FunctionsToVariables();
        syncAllValues();
        return hasChanged;
    }

    /**
     * Creates Function Nodes (factor graph) the first time DMS runs.
     */
    private void initializeDMSIfNeeded() {
        if (this.functionNodes != null) return; 

        this.functionNodes = new HashMap<>();

        for (Agent a : this.agents) {
            List<String> myFunctions = new ArrayList<>(); 
            
            for (Agent neighbor : a.neighbors) {
                int minId = Math.min(a.id, neighbor.id);
                int maxId = Math.max(a.id, neighbor.id);
                String funcId = minId + "_" + maxId;
                
                myFunctions.add(funcId); 
                
                if (!functionNodes.containsKey(funcId)) {
                    List<Integer> scope = new ArrayList<>();
                    scope.add(minId);
                    scope.add(maxId);
                    FunctionNode fn = new FunctionNode(funcId, scope, this);
                    functionNodes.put(funcId, fn);
                }
            }
            ((SyncAgent) a).initDMS(myFunctions);
        }
    }

    /**
     * DMS Phase 1: Variable Nodes (Agents) calculate and send Q messages to Function Nodes.
     */
    private boolean dmsPhase1VariablesToFunctions() {
        boolean hasChanged = false;
        for (Agent a : this.agents) {
            SyncAgent sa = (SyncAgent) a;
            int oldVal = sa.value;
            
            Map<String, double[]> outgoingQ = sa.executeDMS();
            
            for (Map.Entry<String, double[]> entry : outgoingQ.entrySet()) {
                String funcId = entry.getKey();
                double[] qMsg = entry.getValue();
                functionNodes.get(funcId).receiveQMessage(sa.id, qMsg);
            }
            
            if (oldVal != sa.value) hasChanged = true;
        }
        return hasChanged;
    }

    /**
     * DMS Phase 2: Function Nodes calculate and send R messages back to Variable Nodes.
     */
    private void dmsPhase2FunctionsToVariables() {
        for (FunctionNode fn : functionNodes.values()) {
            Map<Integer, double[]> outgoingR = fn.calculateRMessages();
            
            for (Map.Entry<Integer, double[]> entry : outgoingR.entrySet()) {
                int targetAgentId = entry.getKey();
                double[] rMsg = entry.getValue();
                SyncAgent targetAgent = (SyncAgent) this.agents.get(targetAgentId);
                targetAgent.receiveMessageDMS(fn.functionId, rMsg);
            }
        }
    }

    /**
     * Utility method: Syncs current values so global costs can be calculated accurately.
     */
    public void syncAllValues() {
        dsaPhase1ExchangeValues(); // Exact same logic, reusing method
    }
}