package it.polito.verefoo.extra;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import it.polito.verefoo.jaxb.*;

/*
 * Modified class to generate Fat Tree-based test cases for testing reconfiguration of Firewalls.
 */
public class TestCaseGeneratorFatTree {
    NFV nfv;
    Random rand;
    Set<String> allIPs;
    List<Node> allClients;
    List<Node> allServers;
    List<Node> allSwitches;
    List<Node> allFWs;

    private final int NODES_PER_SWITCH;

    public TestCaseGeneratorFatTree(int numberWebClients, int numberWebServers,
            int numberReachPolicies, int numberIsPolicies,
            int nodesPerSwitch,
            double percReqWithPorts,
            boolean deleteOldPolicies,
            int seed) {
        this.rand = new Random(seed);
        this.NODES_PER_SWITCH = nodesPerSwitch;
        this.allClients = new ArrayList<>();
        this.allServers = new ArrayList<>();
        this.allSwitches = new ArrayList<>();
        this.allFWs = new ArrayList<Node>();
        this.allIPs = new HashSet<>();

        this.nfv = generateNFV(numberWebClients, numberWebServers,
                numberReachPolicies, numberIsPolicies, nodesPerSwitch,
                percReqWithPorts, deleteOldPolicies);
    }

    private NFV generateNFV(int numberWebClients, int numberWebServers,
            int numberReachPolicies, int numberIsPolicies,
            int nodesPerSwitch,
            double percReqWithPorts, boolean deleteOldPolicies) {
        // Initialize an empty NFV
        NFV nfv = new NFV();
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
		nfv.setGraphs(graphs);
		nfv.setPropertyDefinition(pd);
		nfv.setInitialProperty(ipd);
		nfv.setConstraints(cnst);


        Graph graph = new Graph();
        graph.setId(0L);

        // Calculate the number of switches needed
        int totalNodes = numberWebClients + numberWebServers;
        int numberSwitches = (int) Math.ceil((double) totalNodes / NODES_PER_SWITCH);

        // Create switches
        for (int i = 0; i < numberSwitches; i++) {
            Node switchNode = createSwitch();
            allSwitches.add(switchNode);
        }

        // Assign clients and servers to switches
        assignNodesToSwitches(numberWebClients, numberWebServers);

        // Connect switches
        connectSwitches();

        // Add nodes to graph
        graph.getNode().addAll(allClients);
        graph.getNode().addAll(allServers);
        graph.getNode().addAll(allSwitches);

        // Add the graph to NFV
        nfv.getGraphs().getGraph().add(graph);

        // Generate security policies
        generateSecurityPolicies(nfv, graph, numberReachPolicies, numberIsPolicies, percReqWithPorts);

        return nfv;
    }

    private void assignNodesToSwitches(int numberClients, int numberServers) {
        int switchIndex = 0;

        // Assign servers
        for (int i = 0; i < numberServers; i++) {
            Node server = createServer();
            allServers.add(server);
            addLink(allSwitches.get(switchIndex), server);

            if ((i + 1) % NODES_PER_SWITCH == 0) {
                switchIndex = (switchIndex + 1) % allSwitches.size();
            }
        }

        // Assign clients
        for (int i = 0; i < numberClients; i++) {
            Node client = createClient();
            allClients.add(client);
            addLink(allSwitches.get(switchIndex), client);

            if ((i + 1) % NODES_PER_SWITCH == 0) {
                switchIndex = (switchIndex + 1) % allSwitches.size();
            }
        }
    }

    private void connectSwitches() {
        int half = allSwitches.size() / 2;

        for (int i = 0; i < half; i++) {
            for (int j = half; j < allSwitches.size(); j++) {
                addLink(allSwitches.get(i), allSwitches.get(j));
            }
        }
    }

    private void addLink(Node from, Node to) {
        Neighbour neighbour = new Neighbour();
        neighbour.setName(to.getName());
        from.getNeighbour().add(neighbour);

        Neighbour reverseNeighbour = new Neighbour();
        reverseNeighbour.setName(from.getName());
        to.getNeighbour().add(reverseNeighbour);
    }

    private Node createSwitch() {
        Node switchNode = new Node();
        switchNode.setName(createUniqueIP());
        switchNode.setFunctionalType(FunctionalTypes.FORWARDER);
        return switchNode;
    }

    private Node createClient() {
        Node client = new Node();
        client.setName(createUniqueIP());
        client.setFunctionalType(FunctionalTypes.WEBCLIENT);

        Configuration conf = new Configuration();
        conf.setName("confAutoGen");
        Webclient wc = new Webclient();
        wc.setNameWebServer(allServers.get(rand.nextInt(allServers.size())).getName());
        conf.setWebclient(wc);
        client.setConfiguration(conf);

        return client;
    }

    private Node createServer() {
        Node server = new Node();
        server.setName(createUniqueIP());
        server.setFunctionalType(FunctionalTypes.WEBSERVER);

        Configuration conf = new Configuration();
        conf.setName("confAutoGen");
        Webserver ws = new Webserver();
        ws.setName(server.getName());
        conf.setWebserver(ws);
        server.setConfiguration(conf);

        return server;
    }

    private void generateSecurityPolicies(NFV nfv, Graph graph, int numberReachPolicies, int numberIsPolicies, double percReqWithPorts) {
        Set<String> reachabilityPairs = new HashSet<>();
        Set<String> isolationPairs = new HashSet<>();

        // Isolation Policies
		int numberIsWithPorts = (int) (numberIsPolicies * percReqWithPorts);
		for(int i = 0; i < numberIsPolicies; i++) {
			String srcNode = "", dstNode = "", srcPort = "*", dstPort = "*";
//			srcNode = allClients.get(rand.nextInt(allClients.size())).getName();
//			dstNode = allServers.get(rand.nextInt(allServers.size())).getName();
			if(rand.nextBoolean())
				srcNode = allClients.get(rand.nextInt(allClients.size())).getName();
			else srcNode = allServers.get(rand.nextInt(allServers.size())).getName();
			if(rand.nextBoolean())
				dstNode = allClients.get(rand.nextInt(allClients.size())).getName();
			else dstNode = allServers.get(rand.nextInt(allServers.size())).getName();
			if(numberIsWithPorts > 0) {
				srcPort = String.valueOf(rand.nextInt(65535));
//				if(rand.nextBoolean())
//					srcPort = String.valueOf(rand.nextInt(65535));
//				else dstPort = String.valueOf(rand.nextInt(65535));
				numberIsWithPorts--;
			}
			//control if the policy is not ready inserted
			boolean alreadyInserted = false;
			for(Property prop: nfv.getPropertyDefinition().getProperty()) {
				if(prop.getSrc().equals(srcNode) && prop.getDst().equals(dstNode)) {
					alreadyInserted = true;
					break;
				}
			}
			if(!alreadyInserted && !srcNode.equals(dstNode))
				createPolicy(PName.ISOLATION_PROPERTY, nfv, graph, srcNode, dstNode, srcPort, dstPort);
			else i--;
		}
			// Reachability Policies
		int numberRPWithPorts = (int) (numberReachPolicies * percReqWithPorts);
		for(int i = 0; i < numberReachPolicies; i++) {
			String srcNode = "", dstNode = "", srcPort = "*", dstPort = "*";
//			srcNode = allClients.get(rand.nextInt(allClients.size())).getName();
//			dstNode = allServers.get(rand.nextInt(allServers.size())).getName();
			if(rand.nextBoolean())
				srcNode = allClients.get(rand.nextInt(allClients.size())).getName();
			else srcNode = allServers.get(rand.nextInt(allServers.size())).getName();
			if(rand.nextBoolean())
				dstNode = allClients.get(rand.nextInt(allClients.size())).getName();
			else dstNode = allServers.get(rand.nextInt(allServers.size())).getName();
			if(numberRPWithPorts > 0) {
				srcPort = String.valueOf(rand.nextInt(65535));
//				if(rand.nextBoolean())
//					srcPort = String.valueOf(rand.nextInt(65535));
//				else dstPort = String.valueOf(rand.nextInt(65535));
				numberRPWithPorts--;
			}
			//control if the policy is not ready inserted
			boolean alreadyInserted = false;
			for(Property prop: nfv.getPropertyDefinition().getProperty()) {
				if(prop.getSrc().equals(srcNode) && prop.getDst().equals(dstNode)) {
					alreadyInserted = true;
					break;
				}
			}
			
			if(!alreadyInserted && !srcNode.equals(dstNode))
				createPolicy(PName.REACHABILITY_PROPERTY, nfv, graph, srcNode, dstNode, srcPort, dstPort);
			else i--;
		}

    }


    /*
	 * Utility for creating a Policy and adding it to the NFV
	 */
	private void createPolicy(PName type, NFV nfv, Graph graph, String IPClient, String IPServer, String srcPort, String dstPort) {
		Property property = new Property();
		property.setName(type);
		property.setGraph((long) 0);
		property.setSrc(IPClient);
		property.setDst(IPServer);
		property.setSrcPort(srcPort);
		property.setDstPort(dstPort);
		nfv.getPropertyDefinition().getProperty().add(property);
	}


    private String createUniqueIP() {
        String ip;
        do {
            ip = createIP();
        } while (allIPs.contains(ip));
        allIPs.add(ip);
        return ip;
    }

    private String createIP() {
        int first = rand.nextInt(256);
        if (first == 0)
            first++;
        int second = rand.nextInt(256);
        int third = rand.nextInt(256);
        int fourth = rand.nextInt(256);
        return first + "." + second + "." + third + "." + fourth;
    }

    /*
	 * Getters and setters
	 */
	
	public NFV getNfv() {
		return nfv;
	}

	public void setNfv(NFV nfv) {
		this.nfv = nfv;
	}


    public NFV generateNewPolicySet(double percReqKept) {
        NFV newNfv = new NFV();
        newNfv.setGraphs(nfv.getGraphs());
        newNfv.setConstraints(nfv.getConstraints()); // Assicurati che l'oggetto Constraints sia copiato

        PropertyDefinition pd = new PropertyDefinition();
        newNfv.setPropertyDefinition(pd);

        List<Property> properties = nfv.getPropertyDefinition().getProperty();
        List<Property> newProperties = new ArrayList<>();

        int numKept = (int) (properties.size() * percReqKept);
        for (int i = 0; i < numKept; i++) {
            newProperties.add(properties.get(i));
        }

        int numNew = properties.size() - numKept;
        PName[] pNames = PName.values();
        for (int i = 0; i < numNew; i++) {
            Property newProperty = new Property();
            PName randomPName = pNames[rand.nextInt(pNames.length)];
            newProperty.setName(randomPName);
            newProperty.setSrc("10.0.0." + rand.nextInt(256));
            newProperty.setDst("10.0.1." + rand.nextInt(256));
            newProperties.add(newProperty);
        }

        pd.getProperty().addAll(newProperties);
        newNfv.setPropertyDefinition(pd);

        // Verifica che tutti i nodi siano collegati correttamente
        ensureValidLinks(newNfv);

        return newNfv;
    }

    private void ensureValidLinks(NFV nfv) {
        for (Graph graph : nfv.getGraphs().getGraph()) {
            for (Node node : graph.getNode()) {
                if (node.getNeighbour().isEmpty()) {
                    // Trova un nodo vicino valido e aggiungi un collegamento
                    Node neighbour = findValidNeighbour(graph, node);
                    addLink(node, neighbour);
                }
                for (Neighbour neigh : node.getNeighbour()) {
                    if (neigh.getName() == null) {
                        // Trova un nodo vicino valido e aggiungi un collegamento
                        Node neighbour = findValidNeighbour(graph, node);
                        addLink(node, neighbour);
                    }
                }
            }
        }
    }

    private Node findValidNeighbour(Graph graph, Node node) {
        for (Node potentialNeighbour : graph.getNode()) {
            if (!potentialNeighbour.equals(node) && !potentialNeighbour.getNeighbour().contains(node)) {
                return potentialNeighbour;
            }
        }
        throw new IllegalStateException("Errore: Impossibile trovare un vicino valido per il nodo " + node.getName());
    }

}
