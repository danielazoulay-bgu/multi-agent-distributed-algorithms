import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementation of an Asynchronous Agent for DCOP using Lamport Clocks.
 * Supports DSA-C, MGM, MGM-2 (Strict), and perfectly isolated DMS (Max-Sum).
 * Includes Advanced Protection: Epsilon Pruning (0.1), Value Drift Normalization, and LOCAL QUORUM.
 */
public class AsyncAgent extends Agent implements Runnable {
    
    private volatile boolean isRunning = true;
    
    // --- Global Counters for System Management ---
    public static AtomicInteger messagesInFlight = new AtomicInteger(0);
    public static AtomicInteger activeWorkers = new AtomicInteger(0);
    public static AtomicInteger agentsWithPositiveGain = new AtomicInteger(0);
    public static AtomicInteger realUpdatesInFlight = new AtomicInteger(0);

    // --- Mailboxes ---
    private final List<int[]> mailboxQueue = new ArrayList<>();
    private final List<Object[]> dmsMailboxQueue = new ArrayList<>();
    
    private Map<String, double[]> currentDmsMailbox = new HashMap<>();
    private Map<String, double[]> lastSentQMessages = new HashMap<>();

    // --- Local Quorum trackers for DMS ---
    private Set<String> dmsReceivedFrom = new HashSet<>();
    private List<String> connectedFunctions = new ArrayList<>(); 

    private String currentAlgorithm = ""; 
    
    public int logicalClock = 0;
    private boolean hasPositiveGain = false; 

    // --- Logic Modules ---
    private DSAC_Logic dsacBrain;
    private MGM_Logic mgmBrain;
    private MGM2_Logic mgm2Brain; 
    private DMS_Logic dmsBrain; 

    // --- State Variables (MGM, MGM-2, DSA) ---
    private Set<Integer> dsacReceivedFrom = new HashSet<>();
    private int mgmPhase = 0; 
    private Set<Integer> mgmReceivedValuesFrom = new HashSet<>();
    private Set<Integer> mgmReceivedGainsFrom = new HashSet<>();
    private Map<Integer, Integer> neighborGains = new HashMap<>();
    private int currentGain = 0;
    private int proposedValue = 0;
    private Set<Integer> mgm2ReceivedOffersFrom = new HashSet<>();
    private Set<Integer> mgm2ReceivedAcceptsFrom = new HashSet<>();
    private Map<Integer, Integer> neighborOffers = new HashMap<>();
    private Map<Integer, Integer> neighborAccepts = new HashMap<>();
    private Map<Integer, Integer> neighborRepIds = new HashMap<>();
    private boolean isOfferer = false;
    private int offerTarget = -1;
    private int mgm2Partner = -1;
    private int mgm2RepId = -1;
    private boolean hasPartnerCalculated = false;
    private int partnerJointGain = 0;
    private int mgm2PartnerCandidateValue = -1;

    public AsyncAgent(int id, AgentSystem system, long seed) {
        super(id, system, seed);
        this.dsacBrain = new DSAC_Logic(this);
        this.mgmBrain = new MGM_Logic(this);
        this.mgm2Brain = new MGM2_Logic(this);
    }

    public void setAlgorithm(String algorithm) {
        this.currentAlgorithm = algorithm;
    }

    /**
     * Safely enqueues a standard message into the agent's mailbox (Thread-safe).
     */
    public void receiveMessageAsync(int type, int senderId, int c1, int c2, int senderClock, int isRealUpdate) {
        synchronized (mailboxQueue) {
            mailboxQueue.add(new int[]{type, senderId, c1, c2, senderClock, isRealUpdate});
            mailboxQueue.notifyAll(); 
        }
    }

    /**
     * Safely enqueues a DMS (Max-Sum) R-message into the dedicated mailbox.
     */
    public void receiveMessageDMSAsync(String functionId, double[] rMessage, int senderClock) {
        synchronized (mailboxQueue) { 
            dmsMailboxQueue.add(new Object[]{functionId, rMessage, senderClock});
            mailboxQueue.notifyAll();
        }
    }

    /**
     * Triggers the initialization protocol of the selected algorithm.
     */
    public void startAlgorithm() {
        switch (this.currentAlgorithm) {
            case "DSA-C": initDSAC(); break;
            case "MGM": initMGM(); break;
            case "MGM-2": initMGM2(); break;
        }
    }

    /**
     * Core Thread Loop: Waits for messages and routes them to the appropriate handler.
     */
    @Override
    public void run() {
        while (isRunning) {
            int[] standardMsg = null;
            Object[] dmsMsg = null;

            // 1. Fetch next message securely
            synchronized (mailboxQueue) {
                while (mailboxQueue.isEmpty() && dmsMailboxQueue.isEmpty() && isRunning) {
                    try { mailboxQueue.wait(); } catch (InterruptedException e) { isRunning = false; }
                }
                if (isRunning) {
                    if (!mailboxQueue.isEmpty()) standardMsg = mailboxQueue.remove(0);
                    else if (!dmsMailboxQueue.isEmpty()) dmsMsg = dmsMailboxQueue.remove(0);
                }
            }

            // 2. Route the fetched message
            if (standardMsg != null) {
                processStandardMessageSafe(standardMsg);
            } else if (dmsMsg != null) {
                processDMSMessageSafe(dmsMsg);
            }
        }
    }

    /**
     * Halts the thread execution and frees locks.
     */
    public void stop() {
        this.isRunning = false;
        synchronized (mailboxQueue) { mailboxQueue.notifyAll(); }
    }

    // ==========================================
    // Core Message Handlers (Extracted from run)
    // ==========================================

    /**
     * Safely processes standard (DSA/MGM) messages, updates clocks, and handles atomic counters.
     */
    private void processStandardMessageSafe(int[] standardMsg) {
        activeWorkers.incrementAndGet(); 
        try {
            int type = standardMsg[0]; int senderId = standardMsg[1]; int c1 = standardMsg[2]; 
            int c2 = standardMsg[3]; int msgClock = standardMsg[4]; int isRealUpdate = standardMsg[5];

            if (isRealUpdate == 1) realUpdatesInFlight.decrementAndGet();
            
            this.logicalClock = Math.max(this.logicalClock, msgClock + 1);
            if (this.logicalClock > 1000) return; 

            if (type == 0) this.updateNeighborValue(senderId, c1);

            switch (this.currentAlgorithm) {
                case "DSA-C": processDSAC(type, senderId, c1); break;
                case "MGM": processMGM(type, senderId, c1); break;
                case "MGM-2": processMGM2(type, senderId, c1, c2); break;
                case "DMS": 
                    ((AsyncSystem) this.system).recordCost(this.id, this.logicalClock, this.getLocalCost());
                    break;
            }
        } catch (Exception e) {
            System.err.println("Agent " + this.id + " Standard Logic Exception: " + e.getMessage());
        } finally {
            messagesInFlight.decrementAndGet(); activeWorkers.decrementAndGet();    
        }
    }

    /**
     * Processes DMS messages, enforcing Local Quorum before triggering calculations.
     */
    private void processDMSMessageSafe(Object[] dmsMsg) {
        activeWorkers.incrementAndGet();
        try {
            if (!updateClockFromDmsMessage(dmsMsg)) return;
            updateQuorumAndProcessIfReady((String) dmsMsg[0], (double[]) dmsMsg[1]);
        } catch (Exception e) {
            System.err.println("Agent " + this.id + " DMS Logic Exception: " + e.getMessage());
        } finally {
            messagesInFlight.decrementAndGet(); activeWorkers.decrementAndGet();
        }
    }

    /**
     * Updates the logical clock from the DMS message, ensuring it doesn't exceed 1000.
     */
    private boolean updateClockFromDmsMessage(Object[] dmsMsg) {
        int msgClock = (int) dmsMsg[2];
        this.logicalClock = Math.max(this.logicalClock, msgClock + 1);
        return this.logicalClock <= 1000;
    }

    /**
     * Saves the DMS message and checks for quorum. If all neighbors replied, triggers calculation.
     */
    private void updateQuorumAndProcessIfReady(String funcId, double[] rMsg) {
        this.currentDmsMailbox.put(funcId, rMsg);
        this.dmsReceivedFrom.add(funcId); 
        
        if (this.dmsReceivedFrom.size() == this.connectedFunctions.size()) {
            processDMS();
            this.dmsReceivedFrom.clear();
        }
    }

    // ==========================================
    // DSA-C Logic blocks
    // ==========================================
    
    /**
     * Initializes DSA-C algorithm and broadcasts the initial state to neighbors.
     */
    private void initDSAC() {
        this.logicalClock = 0;
        ((AsyncSystem) this.system).recordCost(this.id, this.logicalClock, this.getLocalCost());
        broadcastState(1); 
    }
    
    /**
     * Processes incoming DSA-C messages. Calculates and broadcasts only when all neighbors have updated.
     */
    private void processDSAC(int type, int senderId, int content) {
        if (!hasReceivedFromAllNeighbors(senderId)) return; 
        int realUpdate = executeDsacDecision();
        prepareForNextRound();
        broadcastState(realUpdate);
    }
    
    /**
     * Checks if messages were received from all neighbors in the current round.
     */
    private boolean hasReceivedFromAllNeighbors(int senderId) {
        this.dsacReceivedFrom.add(senderId); 
        return this.dsacReceivedFrom.size() == neighbors.size();
    }
    
    /**
     * Executes DSA-C logic to probabilistically decide whether to change the value.
     */
    private int executeDsacDecision() {
        this.dsacBrain.findBestLocalAssignment();
        if (this.dsacBrain.shouldChange(system.p)) {
            this.value = this.dsacBrain.bestValueCandidate; 
            ((AsyncSystem) this.system).reportActualChange(this.logicalClock); return 1;
        } return 0;
    }
    
    /**
     * Records the current cost and resets variables for the next round.
     */
    private void prepareForNextRound() {
        ((AsyncSystem) this.system).recordCost(this.id, this.logicalClock, this.getLocalCost()); this.dsacReceivedFrom.clear();
    }
    
    /**
     * Broadcasts the updated (or maintained) physical value to all neighbors.
     */
    private void broadcastState(int realUpdate) {
        if (realUpdate == 1) realUpdatesInFlight.addAndGet(neighbors.size());
        messagesInFlight.addAndGet(neighbors.size());
        for (Agent neighbor : neighbors) ((AsyncAgent) neighbor).receiveMessageAsync(0, this.id, this.value, 0, this.logicalClock, realUpdate);
    }

    // ==========================================
    // MGM Logic blocks
    // ==========================================

    /**
     * Initializes the MGM algorithm and broadcasts the initial state.
     */
    private void initMGM() {
        this.logicalClock = 0; this.mgmPhase = 0;
        ((AsyncSystem) this.system).recordCost(this.id, this.logicalClock, this.getLocalCost());
        realUpdatesInFlight.addAndGet(neighbors.size()); broadcastMGMMessage(0, this.value, 1);
    }
    
    /**
     * Routes MGM phases: data collection, gain calculation, or decision making.
     */
    private void processMGM(int type, int senderId, int content) {
        collectMGMData(type, senderId, content);
        if (this.mgmPhase == 0) calculateAndBroadcastGain();
        if (this.mgmPhase == 1) evaluateWinnerAndExecuteChange();
    }
    
    /**
     * Collects values or gains from neighbors and stores them.
     */
    private void collectMGMData(int type, int senderId, int content) {
        if (type == 0) this.mgmReceivedValuesFrom.add(senderId);
        else if (type == 1) { this.neighborGains.put(senderId, content); this.mgmReceivedGainsFrom.add(senderId); }
    }
    
    /**
     * Calculates the maximum local gain and broadcasts it to neighbors (MGM Phase 1).
     */
    private void calculateAndBroadcastGain() {
        if (this.mgmReceivedValuesFrom.size() < neighbors.size()) return; 
        this.mgmBrain.calculateSingleGain();
        this.currentGain = this.mgmBrain.currentGain; this.proposedValue = this.mgmBrain.bestValueCandidate;
        updateGodModeGainCounter(this.currentGain > 0);
        this.mgmReceivedValuesFrom.clear(); this.mgmPhase = 1; broadcastMGMMessage(1, this.currentGain, 0);
    }
    
    /**
     * Checks if the agent is the winner (highest gain in neighborhood) and changes value if so.
     */
    private void evaluateWinnerAndExecuteChange() {
        if (this.mgmReceivedGainsFrom.size() < neighbors.size()) return; 
        
        int realUpdate = 0;
        if (determineIfMgmWinner()) {
            realUpdate = executeMgmWinningMove();
        }
        
        finalizeMgmPhase(realUpdate);
    }

    /**
     * Compares the agent's gain with all neighbors' gains to determine the winner.
     */
    private boolean determineIfMgmWinner() {
        if (this.currentGain <= 0) return false;
        for (Agent neighbor : neighbors) {
            int nGain = this.neighborGains.getOrDefault(neighbor.id, 0);
            if (!this.mgmBrain.isWinner(this.currentGain, neighbor.id, nGain)) return false;
        }
        return true;
    }

    /**
     * Updates the physical value after winning and notifies the system.
     */
    private int executeMgmWinningMove() {
        this.value = this.proposedValue; 
        ((AsyncSystem) this.system).reportActualChange(this.logicalClock); 
        updateGodModeGainCounter(false);
        return 1; // 1 means real update occurred
    }

    /**
     * Closes the MGM round, records costs, and resets arrays for the next round.
     */
    private void finalizeMgmPhase(int realUpdate) {
        ((AsyncSystem) this.system).recordCost(this.id, this.logicalClock, this.getLocalCost());
        this.mgmReceivedGainsFrom.clear(); 
        this.mgmPhase = 0;
        if (realUpdate == 1) realUpdatesInFlight.addAndGet(neighbors.size());
        broadcastMGMMessage(0, this.value, realUpdate); 
    }
    
    /**
     * Sends an MGM message (value or gain) to all neighbors.
     */
    private void broadcastMGMMessage(int type, int content, int isRealUpdate) {
        messagesInFlight.addAndGet(neighbors.size());
        for (Agent neighbor : neighbors) ((AsyncAgent) neighbor).receiveMessageAsync(type, this.id, content, 0, this.logicalClock, isRealUpdate);
    }
    
    /**
     * Updates the global counter tracking agents with positive gain (for termination conditions).
     */
    private void updateGodModeGainCounter(boolean nowPositive) {
        if (nowPositive && !this.hasPositiveGain) { agentsWithPositiveGain.incrementAndGet(); this.hasPositiveGain = true; } 
        else if (!nowPositive && this.hasPositiveGain) { agentsWithPositiveGain.decrementAndGet(); this.hasPositiveGain = false; }
    }

    // ==========================================
    // MGM-2 Logic blocks
    // ==========================================

    /**
     * Initializes the MGM-2 algorithm and broadcasts the initial value.
     */
    private void initMGM2() {
        this.logicalClock = 0; this.mgmPhase = 1;
        ((AsyncSystem) this.system).recordCost(this.id, this.logicalClock, this.getLocalCost());
        messagesInFlight.addAndGet(neighbors.size()); realUpdatesInFlight.addAndGet(neighbors.size());
        for (Agent n : neighbors) ((AsyncAgent)n).receiveMessageAsync(0, this.id, this.value, 0, this.logicalClock, 1);
    }
    
    /**
     * Manages the 4 complex negotiation phases of MGM-2.
     */
    private void processMGM2(int type, int senderId, int c1, int c2) {
        sortIncomingMGM2Messages(type, senderId, c1, c2);
        if (this.mgmPhase == 1) evaluateAndSendPartnershipOffers();
        if (this.mgmPhase == 2) evaluateReceivedOffersAndCalculateJointGain();
        if (this.mgmPhase == 3) confirmPartnershipsAndCalculateSingleGain();
        if (this.mgmPhase == 4) compareGainsAndExecuteValueChange();
    }
    
    /**
     * Sorts incoming MGM-2 messages (offers, accepts, gains) into appropriate data structures.
     */
    private void sortIncomingMGM2Messages(int type, int senderId, int c1, int c2) {
        if (type == 0) this.mgmReceivedValuesFrom.add(senderId);
        if (type == 1) { this.neighborOffers.put(senderId, c1); this.mgm2ReceivedOffersFrom.add(senderId); }
        if (type == 2) { this.neighborAccepts.put(senderId, c1); this.mgm2ReceivedAcceptsFrom.add(senderId); }
        if (type == 3) { this.partnerJointGain = c1; this.proposedValue = c2; this.hasPartnerCalculated = true; }
        if (type == 4) { this.neighborGains.put(senderId, c1); this.neighborRepIds.put(senderId, c2); this.mgmReceivedGainsFrom.add(senderId); }
    }
    
    /**
     * Decides whether to offer a partnership and broadcasts the offer.
     */
    private void evaluateAndSendPartnershipOffers() {
        if (!isPhaseReady(this.mgmReceivedValuesFrom)) return;
        resetRoundState(); this.offerTarget = this.mgm2Brain.decideOffer(); this.isOfferer = (this.offerTarget != -1);
        advancePhaseAndClearSet(2, this.mgmReceivedValuesFrom); broadcastMGM2Message(1, this.offerTarget, 0, 0);
    }
    
    /**
     * Evaluates received offers, selects a partner, and calculates joint gain.
     */
    private void evaluateReceivedOffersAndCalculateJointGain() {
        if (!isPhaseReady(this.mgm2ReceivedOffersFrom)) return;
        int acceptedTarget = -1; List<Integer> offersToMe = getOffersDirectedToMe();
        if (!this.isOfferer && !offersToMe.isEmpty()) acceptedTarget = processAcceptedPartnership(offersToMe);
        advancePhaseAndClearSet(3, this.mgm2ReceivedOffersFrom); broadcastMGM2Message(2, acceptedTarget, 0, 0);
    }
    
    /**
     * Verifies partnership acceptance, or calculates single gain if unpartnered.
     */
    private void confirmPartnershipsAndCalculateSingleGain() {
        if (!isPhaseReady(this.mgm2ReceivedAcceptsFrom)) return;
        if (this.isOfferer && !verifySentOfferAcceptance()) return; 
        if (this.mgm2Partner == -1) processSingleAgentFallback();
        updateGodModeGainCounter(this.currentGain > 0);
        advancePhaseAndClearSet(4, this.mgm2ReceivedAcceptsFrom); broadcastMGM2Message(4, this.currentGain, this.mgm2RepId, 0);
    }
    
    /**
     * Compares gains (single or joint) against neighbors to determine the winner.
     */
    private void compareGainsAndExecuteValueChange() {
        if (!isPhaseReady(this.mgmReceivedGainsFrom)) return;
        int realUpdate = 0;
        if (determineWinnerAgainstNeighbors()) {
            this.value = this.proposedValue; realUpdate = 1;
            ((AsyncSystem) this.system).reportActualChange(this.logicalClock); updateGodModeGainCounter(false);
        }
        ((AsyncSystem) this.system).recordCost(this.id, this.logicalClock, this.getLocalCost());
        advancePhaseAndClearSet(1, this.mgmReceivedGainsFrom);
        if (realUpdate == 1) realUpdatesInFlight.addAndGet(neighbors.size());
        broadcastMGM2Message(0, this.value, 0, realUpdate); 
    }
    
    /**
     * Ensures all required messages for the current phase have been received from all neighbors.
     */
    private boolean isPhaseReady(Set<Integer> receivedSet) { return receivedSet.size() == neighbors.size(); }
    
    /**
     * Advances the agent to the next phase and clears the tracking lists.
     */
    private void advancePhaseAndClearSet(int nextPhase, Set<Integer> setToClear) { this.mgmPhase = nextPhase; setToClear.clear(); }
    
    /**
     * Resets MGM-2 negotiation variables for a new round.
     */
    private void resetRoundState() { this.mgm2Partner = -1; this.isOfferer = false; this.hasPartnerCalculated = false; }
    
    /**
     * Sends an MGM-2 message to all neighbors.
     */
    private void broadcastMGM2Message(int type, int c1, int c2, int isRealUpdate) {
        messagesInFlight.addAndGet(neighbors.size());
        for (Agent n : neighbors) ((AsyncAgent)n).receiveMessageAsync(type, this.id, c1, c2, this.logicalClock, isRealUpdate);
    }
    
    /**
     * Retrieves a list of all neighbors who offered a partnership to this agent.
     */
    private List<Integer> getOffersDirectedToMe() {
        List<Integer> offers = new ArrayList<>();
        for (Agent n : neighbors) if (this.neighborOffers.get(n.id) == this.id) offers.add(n.id);
        return offers;
    }
    
    /**
     * Selects a partner from offers, calculates joint gain, and sends an acceptance message.
     */
    private int processAcceptedPartnership(List<Integer> offersToMe) {
        int target = this.mgm2Brain.selectPartner(offersToMe); this.mgm2Partner = target;
        AsyncAgent partnerAgent = getNeighborById(target); this.mgm2Brain.calculateJointGain(partnerAgent);
        this.currentGain = this.mgm2Brain.currentGain; this.proposedValue = this.mgm2Brain.bestValueCandidate;
        this.mgm2PartnerCandidateValue = this.mgm2Brain.partnerCandidateValue;
        messagesInFlight.incrementAndGet();
        partnerAgent.receiveMessageAsync(3, this.id, this.currentGain, this.mgm2PartnerCandidateValue, this.logicalClock, 0);
        return target;
    }
    
    /**
     * Checks if the neighbor accepted the partnership offer sent by this agent.
     */
    private boolean verifySentOfferAcceptance() {
        if (this.neighborAccepts.getOrDefault(this.offerTarget, -1) == this.id) {
            this.mgm2Partner = this.offerTarget; if (!this.hasPartnerCalculated) return false; 
            this.currentGain = this.partnerJointGain; this.mgm2RepId = this.id;
        } else { this.mgm2Partner = -1; }
        return true;
    }
    
    /**
     * Executes a standard single-agent gain calculation if left without a partner.
     */
    private void processSingleAgentFallback() {
        this.mgm2Brain.calculateSingleGain();
        this.currentGain = this.mgm2Brain.currentGain; this.proposedValue = this.mgm2Brain.bestValueCandidate; this.mgm2RepId = this.id;
    }
    
    /**
     * Compares gain against all neighbors, including a tie-breaker using the Rep ID.
     */
    private boolean determineWinnerAgainstNeighbors() {
        if (this.currentGain <= 0) return false;
        for (Agent n : neighbors) {
            if (n.id == this.mgm2Partner) continue;
            int nGain = this.neighborGains.getOrDefault(n.id, 0); int nRep = this.neighborRepIds.getOrDefault(n.id, n.id);
            if (this.currentGain < nGain || (this.currentGain == nGain && this.mgm2RepId > nRep)) return false; 
        } return true;
    }
    
    /**
     * Finds a specific neighbor object by its ID.
     */
    private AsyncAgent getNeighborById(int id) {
        for(Agent n : neighbors) if (n.id == id) return (AsyncAgent) n; return null;
    }

    // ==========================================
    // DMS Algorithm Logic (Pure Async Max-Sum)
    // ==========================================
    
    /** * Initializes the DMS logic brain with its connected function nodes. 
     */
    public void setupDMSBrain(List<String> connectedFunctions) {
        this.connectedFunctions = connectedFunctions; 
        this.dmsBrain = new DMS_Logic(this, connectedFunctions);
    }

    /** * Kickstarts DMS with an initial blind calculation and first Q messages. 
     */
    public void initDMS() {
        this.logicalClock = 0;
        performInitialDmsCalculation();
        notifyNeighborsOfInitialValue();
        dispatchInitialQMessages();
    }

    /**
     * Performs the initial DMS calculation and records the starting cost.
     */
    private void performInitialDmsCalculation() {
        this.dmsBrain.calculate(new HashMap<>());
        this.value = this.dmsBrain.bestValueCandidate;
        ((AsyncSystem) this.system).recordCost(this.id, this.logicalClock, this.getLocalCost());
    }

    /**
     * Broadcasts the initial physical value to neighbors (for system monitoring).
     */
    private void notifyNeighborsOfInitialValue() {
        messagesInFlight.addAndGet(neighbors.size());
        for (Agent n : neighbors) {
            ((AsyncAgent) n).receiveMessageAsync(0, this.id, this.value, 0, this.logicalClock, 0);
        }
    }

    /**
     * Distributes the initial Q messages to the Function Nodes.
     */
    private void dispatchInitialQMessages() {
        Map<String, double[]> initialQ = this.dmsBrain.currentQMessagesToSend;
        for (Map.Entry<String, double[]> entry : initialQ.entrySet()) {
            String funcId = entry.getKey();
            double[] qMsg = entry.getValue().clone();
            
            this.lastSentQMessages.put(funcId, qMsg); // Save history for pruning
            ((AsyncSystem) system).routeQMessageAsync(funcId, this.id, qMsg, this.logicalClock);
        }
    }

    /** * Core DMS loop: calculates beliefs, updates value, and sends new messages. 
     */
    private void processDMS() {
        int oldVal = this.value;
        calculateDmsBeliefs();
        notifyDmsPhysicalChange(oldVal);
        pushSignificantQMessages();
    }

    /**
     * Performs the mathematical DMS calculation based on incoming R messages.
     */
    private void calculateDmsBeliefs() {
        this.dmsBrain.calculate(this.currentDmsMailbox); 
        this.value = this.dmsBrain.bestValueCandidate;
    }

    /**
     * Reports physical value changes to the system and neighbors (for convergence tracking).
     */
    private void notifyDmsPhysicalChange(int oldVal) {
        if (oldVal != this.value) {
            ((AsyncSystem) this.system).reportActualChange(this.logicalClock);
            messagesInFlight.addAndGet(neighbors.size());
            for (Agent n : neighbors) {
                ((AsyncAgent) n).receiveMessageAsync(0, this.id, this.value, 0, this.logicalClock, 0);
            }
        }
    }

    /**
     * Sends new Q messages only if there was a significant change compared to the previous round.
     */
    private void pushSignificantQMessages() {
        Map<String, double[]> outgoingQ = this.dmsBrain.currentQMessagesToSend;
        for (Map.Entry<String, double[]> entry : outgoingQ.entrySet()) {
            String funcId = entry.getKey();
            double[] newQMsg = entry.getValue().clone();
            
            if (hasSignificantChange(this.lastSentQMessages.get(funcId), newQMsg)) {
                this.lastSentQMessages.put(funcId, newQMsg); // Update history
                ((AsyncSystem) system).routeQMessageAsync(funcId, this.id, newQMsg, this.logicalClock);
            }
        }
    }

    /**
     * Protection mechanism: checks if a message changed significantly to prevent message storms.
     */
    private boolean hasSignificantChange(double[] oldMsg, double[] newMsg) {
        if (oldMsg == null || newMsg == null) return true;
        if (oldMsg.length != newMsg.length) return true;

        double oldMin = findMinimumValue(oldMsg);
        double newMin = findMinimumValue(newMsg);

        return hasNormalizedDifference(oldMsg, newMsg, oldMin, newMin);
    }

    /**
     * Finds the minimum value in an array (used for normalization).
     */
    private double findMinimumValue(double[] arr) {
        double min = Double.MAX_VALUE;
        for (double val : arr) if (val < min) min = val;
        return min;
    }

    /**
     * Compares two arrays after normalizing them against their minimum values.
     */
    private boolean hasNormalizedDifference(double[] oldMsg, double[] newMsg, double oldMin, double newMin) {
        for (int i = 0; i < oldMsg.length; i++) {
            double oldNorm = oldMsg[i] - oldMin;
            double newNorm = newMsg[i] - newMin;
            
            if (Math.abs(oldNorm - newNorm) > 0.0001) {
                return true;
            }
        }
        return false;
    }
}