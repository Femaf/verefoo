package it.polito.verefoo.test;

import static org.junit.Assert.*;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.List;
import java.util.Random;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import it.polito.verefoo.VerefooSerializer;
import it.polito.verefoo.extra.TestCaseGeneratorFatTree;
import it.polito.verefoo.jaxb.Configuration;
import it.polito.verefoo.jaxb.Elements;
import it.polito.verefoo.jaxb.Firewall;
import it.polito.verefoo.jaxb.FunctionalTypes;
import it.polito.verefoo.jaxb.NFV;
import it.polito.verefoo.jaxb.Node;
import it.polito.verefoo.jaxb.Property;
import it.polito.verefoo.utils.TestResults;

public class TestPerformanceFatTree {
    private static final String LOG_FILE = "reconfiguration_test_output.log";
    private String algo;
    private int numberOfClients = 8;
    private int numberOfServers = 8;
    private int securityRequirements = 8;
    private int nodesPerSwitch = 4;
    private int runs = 1;
    private int seed = 12345;
    private double percReqKept = 0.7;
    private PrintWriter fileWriter;

    private int modifiedFWs;
    private int unchangedFWs;
    private int newlyAllocatedFWs;

    @Before
    public void setUp() {
        algo = "AP";
        try {
            fileWriter = new PrintWriter(new FileWriter(LOG_FILE, true));
        } catch (Exception e) {
            e.printStackTrace();
            fail("Impossibile creare il file di log");
        }
    }

    @Test
    public void testReconfigurationWithLogging() {
        Random rand = new Random(seed);
        int[] seeds = new int[runs];
        for (int m = 0; m < runs; m++) {
            seeds[m] = Math.abs(rand.nextInt());
        }

        try {
            for (int k = 0; k < runs; k++) {
                String phaseLogFile = "Run_#" + k + ".log";
                fileWriter.println("=== Run #" + k + " ===");

                // Fase 1: Generazione della configurazione iniziale
                TestCaseGeneratorFatTree generator = new TestCaseGeneratorFatTree(
                        "Phase 1", numberOfClients, numberOfServers, securityRequirements, nodesPerSwitch, seeds[k]);
                NFV initialNfv = generator.generateFatTree(numberOfClients, numberOfServers, securityRequirements);


                if (initialNfv == null) {
                    fail("Errore: initialNfv è null dopo la generazione.");
                }

                // Logging della rete iniziale
                logNetworkState(initialNfv, "Phase 1: Initial Network", k, phaseLogFile);

                VerefooSerializer serializer = new VerefooSerializer(initialNfv, algo);
                boolean isSat = serializer.isSat();
                fileWriter.println("Risultato fase 1 (Initial Configuration): " + (isSat ? "SAT" : "UNSAT"));

                // Logging della rete configurata nella fase 1
                logNetworkState(initialNfv, "Fase 1: Configured Network", k, phaseLogFile);

                // Verifica eventuali modifiche ai firewall tra la fase iniziale e la fase 1	
                logFirewallChanges(initialNfv, "Fase 1: Initial to Phase 1", k);

                // Fase 2: Riconfigurazione della rete
                NFV reconfiguredNfv = modifyNetworkConfiguration(generator, initialNfv, percReqKept);

                // Logging della rete riconfigurata nella fase 2
                logNetworkState(reconfiguredNfv, "Fase 2: Reconfigured Network", k, phaseLogFile);

                serializer = new VerefooSerializer(reconfiguredNfv, algo);
                isSat = serializer.isSat();
                fileWriter.println("Risultato fase 2 (Reconfiguration): " + (isSat ? "SAT" : "UNSAT"));

                logResults(initialNfv, reconfiguredNfv, "Fase 2: Reconfiguration", k);

                if (!isSat) {
                    fileWriter.println("Errore: Iterazione #" + k + " produce risultati UNSAT in fase 2.");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            fail("Errore durante il test: " + e.toString());
        }
    }


    private void logNetworkState(NFV nfv, String phase, int iteration, String fileName) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(fileName, true))) {
            writer.println("=== " + phase + " | Iteration: " + iteration + " ===");
            writer.println("Descrizione della rete:");
            for (Node node : nfv.getGraphs().getGraph().get(0).getNode()) {
                writer.println("- Nodo: " + node.getName() +
                               ", Tipo: " + (node.getFunctionalType() != null ? node.getFunctionalType().value() : "N/A") +
                               ", Vicini: " + node.getNeighbour().stream()
                                                   .map(neighbour -> neighbour.getName())
                                                   .reduce((a, b) -> a + ", " + b)
                                                   .orElse("Nessuno"));

                // Controllo se il nodo è un firewall
                if (isFirewall(node)) {
                    writer.println("  **Regole Firewall:**");
                    if (node.getConfiguration() != null && node.getConfiguration().getFirewall() != null) {
                        Firewall firewall = node.getConfiguration().getFirewall();
                        List<Elements> rules = firewall.getElements();
                        if (rules.isEmpty()) {
                            writer.println("    Nessuna regola configurata.");
                        } else {
                            for (Elements rule : rules) {
                                writer.println("    * Regola: ");
                                writer.println("        Origine: " + (rule.getSource() != null ? rule.getSource() : "Non specificato"));
                                writer.println("        Destinazione: " + (rule.getDestination() != null ? rule.getDestination() : "Non specificato"));
                                writer.println("        Azione: " + (rule.getAction() != null ? rule.getAction().value() : "DENY")); 
                                writer.println("        Protocollo: " + (rule.getProtocol() != null ? rule.getProtocol().value() : "ANY"));
                                writer.println("        Porta Sorgente: " + (rule.getSrcPort() != null ? rule.getSrcPort() : "*"));
                                writer.println("        Porta Destinazione: " + (rule.getDstPort() != null ? rule.getDstPort() : "*"));
                            }
                        }
                    } else {
                        writer.println("  Nessuna configurazione di firewall associata.");
                    }

                }

            }
            writer.println();

            // Logging dei requisiti con riferimento a rete e fase
            writer.println("Requisiti (Property) [" + phase + " | Iteration: " + iteration + "]:");
            for (Property property : nfv.getPropertyDefinition().getProperty()) {
                writer.println("- Tipo: " + property.getName() +
                               ", Origine: " + property.getSrc() +
                               ", Destinazione: " + property.getDst() +
                               ", Azione: " + property.getBody() +
                               ", Porta Sorgente: " + property.getSrcPort() +
                               ", Porta Destinazione: " + property.getDstPort());
            }
            writer.println("=== Fine descrizione della rete e dei requisiti ===");
            writer.println();
        } catch (Exception e) {
            e.printStackTrace();
            fail("Errore durante la scrittura del log della rete: " + e.toString());
        }
    }




    private void logFirewallChanges(NFV nfv, String phaseDescription, int iteration) {
        int firewallCount = 0;
        for (Node node : nfv.getGraphs().getGraph().get(0).getNode()) {
            if (isFirewall(node)) {
                firewallCount++;
            }
        }
        fileWriter.println(phaseDescription + " | Iteration: " + iteration + " | Firewall count: " + firewallCount);
    }

    private void logResults(NFV originalNfv, NFV resultNfv, String phase, int iteration) throws Exception {
        long begin = System.currentTimeMillis();
        VerefooSerializer test = new VerefooSerializer(resultNfv, algo);
        long end = System.currentTimeMillis();
        TestResults results = test.getTestTimeResults();

        long totalTime = end - begin;
        long atomicPredCompTime = results.getAtomicPredCompTime();
        long atomicFlowsCompTime = results.getAtomicFlowsCompTime();
        long maxSMTtime = end - results.getBeginMaxSMTTime();

        String resString = phase + " | Iteration: " + iteration + 
                           " | Total time: " + totalTime + "ms, atomicPredCompTime: " + atomicPredCompTime + "ms, " +
                           "atomicFlowsCompTime: " + atomicFlowsCompTime + "ms, maxSMT time: " + maxSMTtime + "ms" + 
                           ", Modified FWs: " + modifiedFWs + ", Unchanged FWs: " + unchangedFWs + 
                           ", Newly Allocated FWs: " + newlyAllocatedFWs;

        fileWriter.println(resString);
        System.out.println(resString);
    }

    private NFV modifyNetworkConfiguration(TestCaseGeneratorFatTree generator, NFV nfv, double percReqKept) {
        modifiedFWs = 0;
        unchangedFWs = 0;
        newlyAllocatedFWs = 0;

        NFV modifiedNfv = generator.modifyNetworkPolicies(nfv, percReqKept);

        for (Node modifiedNode : modifiedNfv.getGraphs().getGraph().get(0).getNode()) {
            Node originalNode = findMatchingNode(nfv, modifiedNode.getName());

            if (isFirewall(modifiedNode)) {
                if (originalNode == null || !isFirewall(originalNode)) {
                    // **Nuovo Firewall Allocato**
                    newlyAllocatedFWs++;
                    logNewFirewall(modifiedNode, "Run_#" + runs + ".log"); // Log dell'allocazione del nuovo firewall con configurazione
                } else if (firewallModified(originalNode, modifiedNode)) {
                    // **Firewall Esistente Modificato**
                    modifiedFWs++;
                } else {
                    // **Firewall Non Modificato**
                    unchangedFWs++;
                }
            }
        }

        return modifiedNfv;
    }


 // Metodo per loggare un nuovo firewall allocato e le sue regole nel file di log specifico del run
    private void logNewFirewall(Node firewall, String phaseLogFile) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(phaseLogFile, true))) {
            writer.println("Nuovo firewall allocato:");
            writer.println("- Nome: " + firewall.getName());
            if (firewall.getConfiguration() != null && firewall.getConfiguration().getFirewall() != null) {
                writer.println("  Configurazione:");
                firewall.getConfiguration().getFirewall().getElements().forEach(element -> {
                    writer.println("    * Regola - Origine: " + element.getSource() +
                                   ", Destinazione: " + element.getDestination() +
                                   ", Azione: " + element.getAction() +
                                   ", Protocollo: " + element.getProtocol());
                    // Aggiungi ulteriori dettagli per ogni elemento della configurazione, se necessario
                });
            } else {
                writer.println("  Nessuna configurazione o regole trovate.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            fail("Errore durante la scrittura del log del nuovo firewall: " + e.toString());
        }
    }



    
    private Node findMatchingNode(NFV nfv, String nodeName) {
        // Scorri tutti i nodi nel grafo della rete nfv
        for (Node node : nfv.getGraphs().getGraph().get(0).getNode()) {
            if (node.getName().equals(nodeName)) {
                return node;  // Restituisci il nodo che corrisponde al nome
            }
        } 
        return null;  // Se non viene trovato, restituisci null
    }

    private boolean isFirewall(Node node) {
        if (node.getFunctionalType() == null) {
            return false;
        }
        // Controlla se il tipo funzionale del nodo è un firewall
        FunctionalTypes type = node.getFunctionalType();
        return type == FunctionalTypes.FIREWALL ||
               type == FunctionalTypes.STATEFUL_FIREWALL ||
               type == FunctionalTypes.PRIORITY_FIREWALL ||
               type == FunctionalTypes.WEB_APPLICATION_FIREWALL;
    }


    private boolean firewallModified(Node originalNode, Node modifiedNode) {
        // Logica per determinare se il firewall è stato modificato
        return !originalNode.getConfiguration().equals(modifiedNode.getConfiguration());
    }
    
    

    @After
    public void tearDown() {
        if (fileWriter != null) {
            fileWriter.close();
        }
    }
} 