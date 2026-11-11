import java.util.LinkedHashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        
        // --- Test Parameters ---
        int n = 50;               // Number of agents
        double p = 0.3;          // Network density
        int domainSize = 10;       // Number of colors/values
        int maxCost = 100;         // Max constraint cost
        long baseSeed = 12345;    // Base seed, will be incremented per run
        int maxRounds = 1000;     // Maximum Lamport iterations
        int step = 5;             // Interval for printing results (high resolution)
        double dsacP = 0.7;       // DSA-C activation probability
        int numRuns = 50;         // NUMBER OF RUNS FOR AVERAGING

        String[] algorithms = {"DSA-C", "MGM", "MGM-2", "DMS"};
        
        // Data structures to store history for the final summary table
        Map<String, int[]> syncResults = new LinkedHashMap<>();
        Map<String, int[]> asyncResults = new LinkedHashMap<>();

        System.out.println("==================================================================");
        System.out.println("  DCOP Ultimate Verification Lab (Averaging " + numRuns + " Runs)  ");
        System.out.println("==================================================================\n");

        for (String algo : algorithms) {
            System.out.println(">>>>>>>>>> TESTING ALGORITHM: " + algo + " (Running " + numRuns + " times) <<<<<<<<<<");
            
            // Accumulators for the 50 runs
            long[] syncSum = new long[maxRounds + 1];
            long[] asyncSum = new long[maxRounds + 1];
            
            for (int run = 0; run < numRuns; run++) {
                long currentSeed = baseSeed + run; 
                
                if (run % 10 == 0) {
                    System.out.println("   [Progress] " + algo + " - Run " + (run + 1) + " / " + numRuns + "...");
                }

                // 1. Synchronous Execution
                SyncSystem syncSystem = new SyncSystem(n, p, domainSize, maxCost, currentSeed);
                syncSum[0] += getGlobalCost(syncSystem);
                boolean syncActive = true;
                
                for (int r = 1; r <= maxRounds; r++) {
                    if (syncActive) {
                        syncActive = syncSystem.runOneRound(algo, dsacP);
                    }
                    syncSum[r] += getGlobalCost(syncSystem);
                }

                // 2. Asynchronous Execution
                AsyncSystem asyncSystem = new AsyncSystem(n, p, domainSize, maxCost, currentSeed);
                int initialAsyncCost = getGlobalCost(asyncSystem);
                
                asyncSystem.runSimulation(algo);
                
                asyncSum[0] += initialAsyncCost; 
                for (int t = 1; t <= maxRounds; t++) {
                    asyncSum[t] += asyncSystem.getGlobalCostAtTime(t);
                }
            }
            
            // --- Calculate Averages ---
            int[] syncAvg = new int[maxRounds + 1];
            int[] asyncAvg = new int[maxRounds + 1];
            
            for (int t = 0; t <= maxRounds; t++) {
                syncAvg[t] = (int) (syncSum[t] / numRuns);
                asyncAvg[t] = (int) (asyncSum[t] / numRuns);
            }
            
            syncResults.put(algo, syncAvg);
            asyncResults.put(algo, asyncAvg);
            
            System.out.println("\n--- " + algo + " Finished All " + numRuns + " Runs ---");
            System.out.println("Final Average Sync Cost: " + syncAvg[maxRounds]);
            System.out.println("Final Average Async Cost: " + asyncAvg[maxRounds] + "\n");
        }

        // ==========================================
        // FINAL SUMMARY TABLE PRINTING
        // ==========================================
        System.out.println("==========================================================================================================================");
        System.out.println("                             FINAL RESULTS SUMMARY (Averaged over " + numRuns + " runs, Jumps of " + step + ")                           ");
        System.out.println("==========================================================================================================================");
        
        System.out.printf("%-10s | %-12s %-12s | %-12s %-12s | %-12s %-12s | %-12s %-12s\n", 
                "Time", "Sync DSA-C", "Async DSA-C", "Sync MGM", "Async MGM", "Sync MGM-2", "Async MGM-2", "Sync DMS", "Async DMS");
        System.out.println("--------------------------------------------------------------------------------------------------------------------------");
        
        for (int t = 0; t <= maxRounds; t += step) {
            System.out.printf("%-10d | %-12d %-12d | %-12d %-12d | %-12d %-12d | %-12d %-12d\n", 
                t,
                syncResults.get("DSA-C")[t], asyncResults.get("DSA-C")[t],
                syncResults.get("MGM")[t],   asyncResults.get("MGM")[t],
                syncResults.get("MGM-2")[t], asyncResults.get("MGM-2")[t],
                syncResults.get("DMS")[t],   asyncResults.get("DMS")[t]
            );
        }
    }

    /**
     * Helper to compute the global cost of an AgentSystem at any given moment.
     */
    private static int getGlobalCost(AgentSystem system) {
        int totalCost = 0;
        for (Agent a : system.agents) {
            totalCost += a.getLocalCost();
        }
        return totalCost / 2; // Each edge is counted twice, so divide by 2
    }

}