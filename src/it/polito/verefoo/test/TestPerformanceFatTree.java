package it.polito.verefoo.test;

import static org.junit.Assert.*;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import it.polito.verefoo.VerefooSerializer;
import it.polito.verefoo.extra.Package1LoggingClass;
import it.polito.verefoo.extra.TestCaseGeneratorFatTree;
import it.polito.verefoo.jaxb.AllocationConstraints;
import it.polito.verefoo.jaxb.Configuration;
import it.polito.verefoo.jaxb.Constraints;
import it.polito.verefoo.jaxb.Elements;
import it.polito.verefoo.jaxb.Firewall;
import it.polito.verefoo.jaxb.FunctionalTypes;
import it.polito.verefoo.jaxb.Graph;
import it.polito.verefoo.jaxb.Graphs;
import it.polito.verefoo.jaxb.InitialProperty;
import it.polito.verefoo.jaxb.LinkConstraints;
import it.polito.verefoo.jaxb.NFV;
import it.polito.verefoo.jaxb.Nat;
import it.polito.verefoo.jaxb.Neighbour;
import it.polito.verefoo.jaxb.Node;
import it.polito.verefoo.jaxb.NodeConstraints;
import it.polito.verefoo.jaxb.Property;
import it.polito.verefoo.jaxb.PropertyDefinition;
import it.polito.verefoo.jaxb.Webclient;
import it.polito.verefoo.jaxb.Webserver;
import it.polito.verefoo.utils.TestResults;

public class TestPerformanceFatTree {
    private static int runs;
    private static String algo = "AP";
    static int seed = 12345;
    static Random rand;
    static NFV root;
    static String pathfile; // for logging purpose
    static String pathfile_model; // for logging purpose
    private static ch.qos.logback.classic.Logger logger; // for logging purpose
    private static ch.qos.logback.classic.Logger loggerModel; // for logging purpose
    private static boolean isSat;
    private static int numberWC; // number web clients
    private static int numberWS; // number web servers
    private static int numberIPR; // number of isolation requirements
    private static int numberRPR; // number of reachability requirements
    private static int numberPR; // total number of requirements
    private static int nodesPerSwitch;
    private static double percReqWithPorts; // from 0.0 (NO requirement with port data) to 1.0 (all requirements specify
                                            // src/dst ports)
    private static double percReqKept;
    private static boolean deleteOldPolicies; // 0 -> do not delete the requirements enforced on the network but not
                                              // present anymore in FinalPolicy set
    // 1 -> force the reconfiguration of all nodes enforcing requirements in
    // InitialPolicy but not in FinalPolicy

    static boolean DEBUG = true; // Set this for verbose execution

    public static void main(String[] args) {

        /*
         * Define test parameters
         */
        percReqWithPorts = 0.0;
        percReqKept = 0.5;
        deleteOldPolicies = false;
        runs = 1;

        // Case A:
        numberPR = 2;
        numberWC = 2;
        numberWS = 2;
        nodesPerSwitch = 4;
        numberIPR = numberPR / 2;
        numberRPR = numberPR / 2;
        numberPR = numberIPR + numberRPR;
        testPerformanceFTReconfiguration();
                
                        System.out.println("TEST TERMINATI");
                
                    }
                
                    /**
                     * @throws java.lang.Exception
                     */
                    @BeforeClass
                    public static void setUpBeforeClass() throws Exception {
                    }
                
                    /**
                     * @throws java.lang.Exception
                     */
                    @AfterClass
                    public static void tearDownAfterClass() throws Exception {
                    }
                
                    /**
                     * @throws java.lang.Exception
                     */
                    @Before
                    public void setUp() throws Exception {
                    }
                
                    /**
                     * @throws java.lang.Exception
                     */
                    @After
                    public void tearDown() throws Exception {
                    }
                
                    /*
                     * Main method called to start the process. It will initialize the log
                     * parameters, generate a random seed for each one of the runs, and apply the
                     * three phases to each run.
                     */
                    @Test
                    public static void testPerformanceFTReconfiguration() {

        // This is the name of the created log file
        pathfile = "NR" + numberPR + "WC" + numberWC + "WS" + numberWS + "NpS" + nodesPerSwitch + "PRP"
                + percReqWithPorts + "PRK" + percReqKept + "deleteOld" + deleteOldPolicies + "ReconfigurationLogs.log";
        logger = Package1LoggingClass.createLoggerFor(pathfile, "log/" + pathfile);
        // Log file used to store XML representation of unsat cases, used for debugging
        // purposes
        pathfile_model = "model_NR" + numberPR + "WC" + numberWC + "WS" + numberWS + "NpS" + nodesPerSwitch + "PRP"
                + percReqWithPorts + "PRK" + percReqKept + "deleteOld" + deleteOldPolicies + "APLogs.log";
        loggerModel = Package1LoggingClass.createLoggerFor(pathfile_model, "log/" + pathfile_model);

        // Initialize random generator with given seed
        rand = new Random(seed);

        // Generate a new random seed for each run
        int[] seeds = new int[runs];
        for (int m = 0; m < runs; m++) {
            seeds[m] = Math.abs(rand.nextInt());
        }

        try {
            // This is needed for converting the NFV data into the XML representation
            JAXBContext jc = JAXBContext.newInstance("it.polito.verefoo.jaxb");
            Marshaller m = jc.createMarshaller();
            m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            m.setProperty(Marshaller.JAXB_NO_NAMESPACE_SCHEMA_LOCATION, "./xsd/nfvSchema.xsd");

            for (int k = 0; k < runs; k++) {
                try {

                    /*
                     * PHASE 1: generate the NFV and use it as input to the framework
                     */
                    TestCaseGeneratorFatTree ag = new TestCaseGeneratorFatTree(numberWC, numberWS, numberRPR, numberIPR,
                            nodesPerSwitch, percReqWithPorts, deleteOldPolicies, seeds[k]);

                    if (DEBUG) {
                        System.out.println("-------------------------------[ORIGINAL NFV]----------------------");
                        StringWriter sw = new StringWriter();
                        m.marshal(ag.getNfv(), sw);
                        System.out.println(sw.toString());
                    }

                    // Save a copy of the produced NFV
                    NFV originalNFV = createCopy(ag.getNfv());
                    // Start the first configuration with "empty" SG
                    NFV resultNFV = testCoarse(ag.getNfv(), false);

                    if (DEBUG) {
                        System.out
                                .println("-------------------------[First phase result]-----------------------------");
                        StringWriter sw1 = new StringWriter();
                        m.marshal(resultNFV, sw1);
                        System.out.println(sw1.toString());
                    }

                    if (!isSat) {
                        // Store the UNSAT graph for debugging
                        StringWriter sw2 = new StringWriter();
                        m.marshal(ag.getNfv(), sw2);
                        loggerModel.info("PHASE 1\n" + sw2.toString());
                    } else {
                        /*
                         * PHASE 2: use the generated NFV with the produced configuration and run again
                         * the framework with a new set of requirements, so we can test the
                         * reconfiguration
                         */
                        ag.setNfv(resultNFV);
                        NFV newNFV = testReconfiguration(ag);

                        // DEBUG
                        if (DEBUG) {
                            System.out.println(
                                    "-------------------------[Second phase result, RECONFIGURATION]-----------------------------");
                            StringWriter sw3 = new StringWriter();
                            m.marshal(newNFV, sw3);
                            System.out.println(sw3.toString());
                        }

                        if (!isSat) {
                            // Store the UNSAT graph for debugging
                            StringWriter sw4 = new StringWriter();
                            m.marshal(ag.getNfv(), sw4);
                            loggerModel.info("PHASE 2\n" + sw4.toString());
                        } else {
                            /*
                             * PHASE 3: use the original NFV generated in phase 1, modify it with the new
                             * set of policies and then configure it.
                             */

                            originalNFV.setInitialProperty(null);
                            originalNFV.setPropertyDefinition(newNFV.getPropertyDefinition());
                            // DEBUG
                            if (DEBUG) {
                                System.out.println("----------------[MODIFIED ORIGINAL NFV]-------------");
                                StringWriter sw5 = new StringWriter();
                                m.marshal(originalNFV, sw5);
                                System.out.println(sw5.toString());
                            }
                            NFV resultNFV2 = testCoarse(originalNFV, true);
                            // DEBUG
                            if (DEBUG) {
                                System.out.println(
                                        "-------------------------[Second phase result, COMPLETE CONFIGURATION]-----------------------------");
                                StringWriter sw6 = new StringWriter();
                                m.marshal(resultNFV2, sw6);
                                System.out.println(sw6.toString());
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.toString());
        }
    }

    /*
     * Used for running the VerefooSerializer class and then get the results for
     * logging. This same method is called in two situations:
     * - in the first phase for the initial network configuration [activateLog ==
     * false]
     * - in the third phase for getting data to compare reconfiguration with a
     * normal configuration [activateLog == false]
     */

    private static NFV testCoarse(NFV root, boolean activateLog) throws Exception {

        long beginAll = System.currentTimeMillis();
        VerefooSerializer test = new VerefooSerializer(root, algo);
        isSat = test.isSat();
        long endAll = System.currentTimeMillis();
        TestResults results = test.getTestTimeResults();

        long totalTime = endAll - beginAll;
        long atomicPredCompTime = results.getAtomicPredCompTime();
        long atomicFlowsCompTime = results.getAtomicFlowsCompTime();
        long maxSMTtime = endAll - results.getBeginMaxSMTTime();
        long reconfiguredNetworkCompTime = results.getReconfiguredNetworkCompTime();
        long totalFlow = results.getTotalFlows();
        long totalAP = results.getTotalAtomicPredicates();

        List<Node> firewalls = test.getResult().getGraphs().getGraph().get(0).getNode().stream()
                .filter(n -> n.getFunctionalType() != null && n.getFunctionalType().equals(FunctionalTypes.FIREWALL))
                .collect(Collectors.toList());
        int numberFWs = firewalls.size();
        int numberNodes = test.getResult().getGraphs().getGraph().get(0).getNode().size();
        int numberFWRules = 0;
        for (int i = 0; i < firewalls.size(); i++) {
            numberFWRules += firewalls.get(i).getConfiguration().getFirewall().getElements().size();
        }
        int numberReconfiguredNodes = results.getNumberReconfiguredNodes();
        int configuredNodes = results.getConfiguredNodes();

        String resString = new String("Total time " + totalTime + "ms, atomicPredCompTime "
                + atomicPredCompTime + "ms, atomicFlowsCompTime "
                + atomicFlowsCompTime + "ms, maxSMT time "
                + maxSMTtime + "ms, reconfiguredNetworkCompTime "
                + reconfiguredNetworkCompTime + " ms;");
        System.out.println(resString);
        if (activateLog) {
            logger.info("\t" + totalTime + "\t" + atomicPredCompTime + "\t" + atomicFlowsCompTime + "\t" +
                    reconfiguredNetworkCompTime + "\t" + maxSMTtime + "\t" + numberFWs + "\t" + numberFWRules
                    + "\t" + numberNodes + "\t" + numberReconfiguredNodes + "\t" + configuredNodes + "\t" + totalFlow
                    + "\t" + totalAP + "\t" + results.getZ3Result() + "\t");
        }
        return test.getResult();
    }

    private static NFV testReconfiguration(TestCaseGeneratorFatTree ag) {
		long beginAll=System.currentTimeMillis();
		//Generate a new set of policies with given percentage of requirements kept from the starting set of policies
		NFV resultNFV = ag.generateNewPolicySet(percReqKept);
		VerefooSerializer test = new VerefooSerializer(resultNFV, algo);
		isSat = test.isSat();
		long endAll=System.currentTimeMillis();
		TestResults results = test.getTestTimeResults();
		long totalTime = endAll - beginAll;
		long atomicPredCompTime = results.getAtomicPredCompTime();
		long atomicFlowsCompTime = results.getAtomicFlowsCompTime();
		long maxSMTtime = endAll - results.getBeginMaxSMTTime();
		long reconfiguredNetworkCompTime = results.getReconfiguredNetworkCompTime();
		long totalFlow = results.getTotalFlows();
		long totalAP = results.getTotalAtomicPredicates();

		List<Node> firewalls = test.getResult().getGraphs().getGraph().get(0).getNode().stream().filter(n -> n.getFunctionalType()!= null && n.getFunctionalType().equals(FunctionalTypes.FIREWALL)).collect(Collectors.toList());
		int numberFWs = firewalls.size();
		int numberNodes = test.getResult().getGraphs().getGraph().get(0).getNode().size();
		int numberFWRules = 0;
		for(int i=0; i<firewalls.size(); i++) {
			numberFWRules += firewalls.get(i).getConfiguration().getFirewall().getElements().size();
		}
		int numberReconfiguredNodes = results.getNumberReconfiguredNodes();
		int configuredNodes = results.getConfiguredNodes();
		
		String resString = new String("[RECONFIGURATION]Total time " + totalTime +  "ms, atomicPredCompTime " 
				+ atomicPredCompTime +  "ms, atomicFlowsCompTime " 
				+ atomicFlowsCompTime + "ms, maxSMT time " 
				+ maxSMTtime + "ms, reconfiguredNetworkCompTime "
				+ reconfiguredNetworkCompTime + " ms;");
		System.out.println(resString);
		logger.info("[RECONFIGURATION]\t" + totalTime + "\t" + atomicPredCompTime + "\t" + atomicFlowsCompTime + "\t" + 
				reconfiguredNetworkCompTime + "\t" + maxSMTtime + "\t" + numberFWs + "\t" + numberFWRules 
				+ "\t" + numberNodes + "\t" + numberReconfiguredNodes + "\t" +configuredNodes + "\t" + totalFlow + "\t" + totalAP + "\t" + results.getZ3Result() + "\t");
        return test.getResult();
	}

    /*
	 * Utility method to create an NFV copying data from another one (basically a "copy constructor")
	 */
	private static NFV createCopy(NFV nfv) {
		List<Node> allNodes = new ArrayList<Node>();
		
		//Initialize an empty NFV
		NFV res = new NFV();
		Graphs graphs = new Graphs();
		PropertyDefinition pd = new PropertyDefinition();
		InitialProperty ipd = new InitialProperty();
		Constraints cnst = new Constraints();
		NodeConstraints nc = new NodeConstraints();
		LinkConstraints lc = new LinkConstraints();
		AllocationConstraints alc = new AllocationConstraints();
		cnst.setNodeConstraints(nc);
		cnst.setLinkConstraints(lc);
		cnst.setAllocationConstraints(alc);
		res.setGraphs(graphs);
		res.setPropertyDefinition(pd);
		res.setInitialProperty(ipd);
		res.setConstraints(cnst);
		
		//Generate the Graph object
		Graph ng = new Graph();
		ng.setId((long)0);
		ng.setServiceGraph(true);
		//ng.setDeleteOldProperties(false);
		
		//Add nodes
		for(Node n:nfv.getGraphs().getGraph().get(0).getNode()) {
			Node newN = new Node();
			
			//Create a copy for each node
			if(n.getFunctionalType() == null) {
				newN.setName(n.getName());
			} else {
				if(n.getFunctionalType().value().equals("WEBSERVER")) {
					newN.setFunctionalType(FunctionalTypes.WEBSERVER);
					newN.setName(n.getName());
					Configuration nC = new Configuration();
					nC.setName(n.getConfiguration().getName());
					Webserver ws = new Webserver();
					ws.setName(newN.getName());
					nC.setWebserver(ws);
					newN.setConfiguration(nC);
				} else if(n.getFunctionalType().value().equals("WEBCLIENT")) {
					newN.setFunctionalType(FunctionalTypes.WEBCLIENT);
					newN.setName(n.getName());
					Configuration nC = new Configuration();
					nC.setName(n.getConfiguration().getName());
					Webclient ws = new Webclient();
					ws.setNameWebServer(n.getConfiguration().getWebclient().getNameWebServer());
					nC.setWebclient(ws);
					newN.setConfiguration(nC);
				} else if(n.getFunctionalType().value().equals("NAT")) {
					newN.setFunctionalType(FunctionalTypes.NAT);
					newN.setName(n.getName());
					Configuration nC = new Configuration();
					nC.setName(newN.getName());
					Nat ws = new Nat();
					nC.setNat(ws);
					for(String s: n.getConfiguration().getNat().getSource()) {
						nC.getNat().getSource().add(s);
					}
					newN.setConfiguration(nC);
				}
			}
			
			//Add all correct neigh
			for(Neighbour neigh : n.getNeighbour()) {
				Neighbour newNeigh = new Neighbour();
				newNeigh.setName(neigh.getName());
				newN.getNeighbour().add(newNeigh);
			}
			
			allNodes.add(newN);
		}
		
		ng.getNode().addAll(allNodes);
		
		//Add graph to NFV
		res.getGraphs().getGraph().add(ng);
		
		//Add policies
		res.getPropertyDefinition().getProperty().addAll(nfv.getPropertyDefinition().getProperty());
		
		return res;
	}


}
