import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Main Simulator Class for IIOT Multi-Agent Routing System.
 * Coordinates experimental runs for Synchronous and Asynchronous models 
 * using a shared topology for fair benchmarking.
 */
public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("--- IIOT Multi-Agent Routing Simulator 2026 ---");

        // --- STEP 1: Parameter Selection ---
        System.out.print("Enter number of agents (N): ");
        int n = scanner.nextInt();
        System.out.print("Enter neighbor probability (P: 0.0 - 1.0): ");
        double p = scanner.nextDouble();

        // --- STEP 2: Topology Generation ---
        // Generates a fixed adjacency list used by both simulation modes
        List<List<Integer>> sharedTopology = generateSharedTopology(n, p);

        // ============================================================
        // PHASE 1: SYNCHRONOUS SIMULATION (Round-based)
        // ============================================================
        System.out.println("\n\n>>> STARTING PHASE 1: SYNCHRONOUS MODE <<<");
        SyncSystem syncSystem = new SyncSystem(n, sharedTopology); 
        syncSystem.printNetwork();

        long syncStartTime = System.currentTimeMillis();
        boolean systemChanged = true;
        int iteration = 0;

        // Loop execution: round-by-round until global convergence
        while (systemChanged) {
            systemChanged = false;
            iteration++;
            System.out.println("\n--- Sync Iteration " + iteration + " ---");

            // Sub-round A: Information propagation (Simulated message exchange)
            for (Agent a : syncSystem.agents) {
                SyncAgent sa = (SyncAgent) a;
                RoutingTable tableSnapshot = sa.getTableForSending(); 
                for (Agent neighbor : sa.neighbors) {
                    ((SyncAgent) neighbor).receiveData(sa.id, tableSnapshot);
                }
            }

            // Sub-round B: Local processing and table updates
            for (Agent a : syncSystem.agents) {
                SyncAgent sa = (SyncAgent) a;
                if (sa.processMailbox()) {
                    systemChanged = true; // Signal another iteration if any change occurred
                }
            }

            // Output state after each round
            System.out.println("Routing Tables after Iteration " + iteration + ":");
            for (Agent a : syncSystem.agents) {
                a.table.printTable();
            }
        }
        long syncEndTime = System.currentTimeMillis();
        long syncTotalTime = syncEndTime - syncStartTime;
        
        System.out.println("\n[SYNC-COMPLETE] System converged after " + iteration + " iterations.");
        System.out.println("Total Synchronous Convergence Time: " + syncTotalTime + " ms.");


        // ============================================================
        // PHASE 2: ASYNCHRONOUS SIMULATION (Thread-based)
        // ============================================================
        System.out.println("\n\n>>> STARTING PHASE 2: ASYNCHRONOUS MODE <<<");
        System.out.println("Press ENTER to start the thread-based simulation...");
        scanner.nextLine(); 
        scanner.nextLine(); 

        // Reset tracking counters for quiescence detection
        AsyncAgent.messagesInFlight.set(0); 
        AsyncAgent.activeWorkers.set(0);
        
        AsyncSystem asyncSystem = new AsyncSystem(n, sharedTopology);
        asyncSystem.printNetwork();

        long asyncStartTime = System.currentTimeMillis();
        
        // Spawn and start concurrent agent threads
        asyncSystem.startSimulation();

        System.out.println("[MONITOR] System initialized. Tracking activity and message flow...");

        // Quiescence Detection: Polls global state until no messages are in-flight and no workers are active
        int heartBeat = 0;
        while (true) {
            int inFlight = AsyncAgent.messagesInFlight.get();
            int active = AsyncAgent.activeWorkers.get();

            if (inFlight == 0 && active == 0) {
                // Short micro-delay to validate stability and prevent premature termination
                try { Thread.sleep(5); } catch (Exception e) {}
                if (AsyncAgent.messagesInFlight.get() == 0 && AsyncAgent.activeWorkers.get() == 0) {
                    break; 
                }
            }

            try {
                // High-frequency polling (1ms) for accurate timing measurement
                Thread.sleep(1); 
                heartBeat++;
                if (heartBeat % 1000 == 0) {
                    System.out.println("   > Monitoring: " + inFlight + " messages in transit, " + active + " agents active.");
                }
            } catch (InterruptedException e) {
                break;
            }
        }

        long asyncEndTime = System.currentTimeMillis();
        long asyncTotalTime = asyncEndTime - asyncStartTime;
        
        System.out.println("\n[ASYNCH-STABLE] Convergence detected via Quiescence Monitoring.");
        System.out.println("Total Asynchronous Convergence Time: " + asyncTotalTime + " ms.");

        // Gracefully terminate all threads
        asyncSystem.stopAll(); 

        try { Thread.sleep(500); } catch (Exception e) {}

        // --- Performance Analysis ---
        // Find the maximum Causal Depth (Lamport Clock) reached in the system
        int maxAsyncDepth = 0;
        for (Agent a : asyncSystem.agents) {
            AsyncAgent aa = (AsyncAgent) a;
            if (aa.getEventCounter() > maxAsyncDepth) {
                maxAsyncDepth = aa.getEventCounter();
            }
        }

        // --- Final Summary Report ---
        System.out.println("\n=== PERFORMANCE COMPARISON SUMMARY ===");
        System.out.println("Shared Topology used for both simulations.");
        System.out.println("Synchronous Rounds: " + iteration + " | Time: " + syncTotalTime + " ms.");
        System.out.println("Asynchronous Max Depth: " + maxAsyncDepth + " | Time: " + asyncTotalTime + " ms.");
        System.out.println("======================================\n");

        System.out.println("Final Asynchronous Routing Tables:");
        for (Agent a : asyncSystem.agents) {
            a.table.printTable();
        }

        System.out.println("\nAll simulations completed successfully.");
        
        scanner.close();
        System.exit(0);
    }

    /**
     * Builds a persistent adjacency list representing the network topology.
     * Guaranteed to produce an identical graph structure for both simulations.
     * @param n Number of agents
     * @param p Probability of an edge between any two agents
     * @return Adjacency list (topology)
     */
    private static List<List<Integer>> generateSharedTopology(int n, double p) {
        List<List<Integer>> topology = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            topology.add(new ArrayList<>());
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (Math.random() < p) {
                    topology.get(i).add(j);
                    topology.get(j).add(i);
                }
            }
        }
        return topology;
    }
}