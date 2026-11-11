import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of a Synchronous Agent for DCOP.
 * Processes messages in discrete rounds using a mailbox system.
 */
public class SyncAgent extends Agent {
    
    private Map<Integer, Integer> mailbox;
    
    // --- Logic Modules ("Chips") ---
    private DSAC_Logic dsacBrain;
    private MGM_Logic mgmBrain;
    private MGM2_Logic mgm2Brain; // Brain for MGM-2
    private DMS_Logic dmsBrain;   // NEW: Brain for Max-Sum (DMS)
    
    // MGM Specific Fields
    private Map<Integer, Integer> neighborGains;
    private int currentGain;
    private int bestValueCandidate;

    // --- MGM-2 Specific Fields ---
    private List<Integer> mgm2ReceivedOffers;
    private int mgm2OfferTarget;
    private int mgm2Partner;
    private boolean isAcceptor;
    private int mgm2PartnerCandidateValue;
    private int mgm2RepId; 
    private Map<Integer, Integer> neighborRepIds;
    
 // --- DMS Specific Fields ---
    // Mailbox specifically for R messages from Function Nodes
    // THE FIX: Initialized here so it's never null!
    private Map<String, double[]> dmsMailbox = new HashMap<>();

    public SyncAgent(int id, AgentSystem system, long seed) { 
        super(id, system, seed); 
        this.mailbox = new HashMap<>();
        this.neighborGains = new HashMap<>();
        
        // Initialize Logic Modules
        this.dsacBrain = new DSAC_Logic(this);
        this.mgmBrain = new MGM_Logic(this);
        this.mgm2Brain = new MGM2_Logic(this); // Initialized the Brain
        
        // MGM-2 initialization
        this.mgm2ReceivedOffers = new ArrayList<>();
        this.neighborRepIds = new HashMap<>();
        resetMGM2State();
    }

    public void receiveData(int senderId, int value) { 
    	mailbox.put(senderId, value); 
    	}

    public void processMailbox() {
        for (Map.Entry<Integer, Integer> entry : mailbox.entrySet()) this.updateNeighborValue(entry.getKey(), entry.getValue());
        mailbox.clear(); 
    }

    // ==========================================
    // DSA-C Algorithm Logic
    // ==========================================
    public void executeDSAC(double p) {
        this.dsacBrain.findBestLocalAssignment();
        if (this.dsacBrain.shouldChange(p)) this.value = this.dsacBrain.bestValueCandidate;
    }

    // ==========================================
    // MGM Logic (Refactored)
    // ==========================================
    public void prepareMGM() {
        this.mgmBrain.calculateSingleGain();
        this.currentGain = this.mgmBrain.currentGain;
        this.bestValueCandidate = this.mgmBrain.bestValueCandidate;
    }

    public void receiveGain(int neighborId, int gain) {
    	neighborGains.put(neighborId, gain); }

    public int getCurrentGain() { 
    	return this.currentGain; }

    public void executeMGM() {
        if (currentGain <= 0) { neighborGains.clear(); return; }
        for (Map.Entry<Integer, Integer> entry : neighborGains.entrySet()) {
            if (!this.mgmBrain.isWinner(currentGain, entry.getKey(), entry.getValue())) {
                neighborGains.clear(); return; 
            }
        }
        this.value = bestValueCandidate; neighborGains.clear();
    }

    // ==========================================
    // MGM-2 Algorithm Logic (Modular PRO Architecture)
    // ==========================================
    
    /**
     * Resets the MGM-2 state variables for a new negotiation round.
     */
    public void resetMGM2State() {
        this.mgm2ReceivedOffers.clear();
        this.mgm2OfferTarget = -1;
        this.mgm2Partner = -1;
        this.isAcceptor = false;
        this.mgm2PartnerCandidateValue = -1;
        this.mgm2RepId = this.id; 
        this.neighborRepIds.clear();
        this.neighborGains.clear();
    }

    // --------------------------------------------------------
    // Phase 1: Partnership Formation - Proposing
    // --------------------------------------------------------
    
    /**
     * Agent locally determines a neighbor to offer a partnership to.
     */
    public void mgm2Phase1_decideOffer() {
        resetMGM2State();
        this.mgm2OfferTarget = this.mgm2Brain.decideOffer();
    }
    
    public int getMgm2OfferTarget() { 
    	return this.mgm2OfferTarget; }
    
    /**
     * Processes incoming partnership offers from neighbors.
     */
    public void receiveOfferMessageMGM2(int senderId, boolean isOffer) {
        if (isOffer && this.mgm2OfferTarget == -1) {
            this.mgm2ReceivedOffers.add(senderId);
        }
    }

    // --------------------------------------------------------
    // Phase 2: Partnership Formation - Accepting
    // --------------------------------------------------------
    
    /**
     * Agent selects one partner from the received pool of offers.
     */
    public void mgm2Phase2_acceptOffer() {
        this.mgm2Partner = this.mgm2Brain.selectPartner(mgm2ReceivedOffers);
        this.isAcceptor = (this.mgm2Partner != -1);
    }

    public int getMgm2AcceptedOffer() { 
    	return this.isAcceptor ? this.mgm2Partner : -1; }

    /**
     * Receives confirmation if the agent's outgoing offer was accepted.
     */
    public void receiveAcceptMGM2(int acceptorId) {
        if (this.mgm2OfferTarget == acceptorId) { 
            this.mgm2Partner = acceptorId; 
            this.isAcceptor = false; 
        }
    }

    // --------------------------------------------------------
    // Phase 3: Gain Calculation (Delegated to Brain)
    // --------------------------------------------------------
    
    /**
     * Triggers the appropriate gain calculation based on the agent's partnership status.
     */
    public void mgm2Phase3_calculateGain() {
        if (this.mgm2Partner == -1) {
            handleSingleAgentGain();
        } else if (this.isAcceptor) {
            handlePairedAgentGain();
        }
    }

    private void handleSingleAgentGain() {
        this.mgm2Brain.calculateSingleGain();
        
        // Retrieve calculated data directly from the Brain's public state
        this.currentGain = this.mgm2Brain.currentGain; 
        this.bestValueCandidate = this.mgm2Brain.bestValueCandidate;
        this.mgm2RepId = this.id;
    }

    private void handlePairedAgentGain() {
        SyncAgent partnerAgent = getNeighborById(this.mgm2Partner);
        this.mgm2Brain.calculateJointGain(partnerAgent);
        
        // Retrieve calculated joint data directly from the Brain's public state
        this.currentGain = this.mgm2Brain.currentGain;
        this.bestValueCandidate = this.mgm2Brain.bestValueCandidate;
        this.mgm2PartnerCandidateValue = this.mgm2Brain.partnerCandidateValue;
        this.mgm2RepId = partnerAgent.id;
    }

    // --------------------------------------------------------
    // Phase 4: State Synchronization and Gain Broadcast
    // --------------------------------------------------------
    
    /**
     * Ensures both agents in a partnership share the exact same gain and candidate values.
     */
    public void shareGainWithPartnerMGM2(SyncAgent offerer) {
        if (this.isAcceptor && this.mgm2Partner != -1) {
            offerer.currentGain = this.currentGain;
            offerer.bestValueCandidate = this.mgm2PartnerCandidateValue;
            offerer.mgm2RepId = this.mgm2RepId;
        }
    }

    public int getMgm2RepId() { 
    	return this.mgm2RepId; }

    public void receiveGainMGM2(int neighborId, int gain, int repId) {
        this.neighborGains.put(neighborId, gain);
        this.neighborRepIds.put(neighborId, repId); 
    }

    // --------------------------------------------------------
    // Phase 5: Final Execution and Tie-Breaking
    // --------------------------------------------------------
    
    /**
     * Executes the value change if the agent possesses the highest local gain.
     * Ties are broken using the representative ID.
     */
    public void executeMGM2() {
        if (this.currentGain <= 0) return;
        
        for (Map.Entry<Integer, Integer> entry : neighborGains.entrySet()) {
            if (entry.getKey() == this.mgm2Partner) continue;
            
            // Tie-breaking mechanism
            if (this.currentGain < entry.getValue() || 
               (this.currentGain == entry.getValue() && this.mgm2RepId > neighborRepIds.get(entry.getKey()))) {
                return; 
            }
        }
        
        this.value = this.bestValueCandidate;
    }

 // ==========================================
    // DMS Algorithm Logic (MAX-SUM)
    // ==========================================

    /**
     * Phase 0: Initialization
     * Initializes the DMS Brain. Must be called AFTER the network 
     * is fully created so the agent knows its neighbors.
     */
    public void initDMS(List<String> connectedFunctions) {
        this.dmsBrain = new DMS_Logic(this, connectedFunctions);
    }
    /**
     * Phase 1: Receiving R Messages
     * Called by the system to deliver an R message from a Function Node.
     */
    public void receiveMessageDMS(String functionId, double[] rMessage) {
        this.dmsMailbox.put(functionId, rMessage.clone());
    }

    /**
     * Phase 2: Execution and Reply
     * Delegates math to the brain, updates the agent's value, and returns the new Q messages.
     */
    public Map<String, double[]> executeDMS() {
        // 1. Tell the brain to calculate using the current mailbox
        this.dmsBrain.calculate(this.dmsMailbox);
        
        // 2. Clear the mailbox for the next round
        this.dmsMailbox.clear();

        // 3. Update the Agent's value from the Brain's public state
        this.value = this.dmsBrain.bestValueCandidate;

        // 4. Return the calculated Q messages to the System (so it can route them)
        return this.dmsBrain.currentQMessagesToSend;
    }

    public SyncAgent getNeighborById(int id) {
        for(Agent n : neighbors) if (n.id == id) return (SyncAgent) n;
        return null;
    }
}