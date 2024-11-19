package it.polito.verefoo.extra;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import it.polito.verefoo.jaxb.*;

public class TestCaseGeneratorFatTree {
    NFV nfv;
    String name;
    Random rand;
    Set<String> allIPs;
    List<Node> allClients;
    List<Node> allServers;
    List<Node> allSwitches;
    List<Node> allFirewalls;

    private final int NODES_PER_SWITCH;

    public TestCaseGeneratorFatTree(String name, int numberClients, int numberServers, int numSecurityRequirements, int nodesPerSwitch, int seed) {
        this.name = name;
        this.rand = new Random(seed);
        this.NODES_PER_SWITCH = nodesPerSwitch;

        allClients = new ArrayList<>();
        allServers = new ArrayList<>();
        allSwitches = new ArrayList<>();
        allFirewalls = new ArrayList<>();
        allIPs = new HashSet<>();
    }

    private String createIP() {
        String ip;
        int first = rand.nextInt(256);
        if (first == 0) first++;
        int second = rand.nextInt(256);
        int third = rand.nextInt(256);
        int fourth = rand.nextInt(256);
        ip = first + "." + second + "." + third + "." + fourth;
        return ip;
    }

    private String createRandomIP() {
        String ip;
        do {
            ip = createIP();
        } while (allIPs.contains(ip));
        allIPs.add(ip);
        return ip;
    }

    public NFV generateFatTree(int numberClients, int numberServers, int numSecurityRequirements) {
        NFV nfv = new NFV();
        Graphs graphs = new Graphs();
        PropertyDefinition pd = new PropertyDefinition();
        Constraints cnst = new Constraints();
        NodeConstraints nc = new NodeConstraints();
        LinkConstraints lc = new LinkConstraints();
        cnst.setNodeConstraints(nc);
        cnst.setLinkConstraints(lc);
        nfv.setGraphs(graphs);
        nfv.setPropertyDefinition(pd);
        nfv.setConstraints(cnst);
        Graph graph = new Graph();
        graph.setId((long) 0);

        int totalNodes = numberClients + numberServers;
        int numberSwitches = (int) Math.ceil((double) totalNodes / NODES_PER_SWITCH);

        for (int i = 0; i < numberSwitches; i++) {
            Node switchNode = createSwitch();
            allSwitches.add(switchNode);
        }

        assignNodesToSwitches(numberClients, numberServers);
        connectSwitches(numberSwitches);
        graph.getNode().addAll(allClients);
        graph.getNode().addAll(allServers);
        graph.getNode().addAll(allSwitches);
        nfv.getGraphs().getGraph().add(graph);
        createSecurityPolicies(nfv, graph, numberClients, numberServers, numSecurityRequirements);

        return nfv;
    }

    private void assignNodesToSwitches(int numberClients, int numberServers) {
        int switchIndex = 0;
        int totalNodes = numberClients + numberServers;
        int clientIndex = 0;
        int serverIndex = 0;

        for (int i = 0; i < totalNodes; i++) {
            if (serverIndex < numberServers) {
                Node server = createServer();
                allServers.add(server);
                addLink(allSwitches.get(switchIndex), server);
                serverIndex++;
            } else if (clientIndex < numberClients) {
                Node client = createClient(rand);
                allClients.add(client);
                addLink(allSwitches.get(switchIndex), client);
                clientIndex++;
            }

            if ((i + 1) % NODES_PER_SWITCH == 0) {
                switchIndex = (switchIndex + 1) % allSwitches.size();
            }
        }

        System.out.println("Number of servers generated: " + allServers.size());
        System.out.println("Number of clients generated: " + allClients.size());
    }
    
    private void connectSwitches(int numberSwitches) {
        int half = numberSwitches / 2;
        for (int i = 0; i < half; i++) {
            for (int j = half; j < numberSwitches; j++) {
                addLink(allSwitches.get(i), allSwitches.get(j));
            }
        }
    }

    private void addLink(Node from, Node to) {
        Neighbour neigh = new Neighbour();
        neigh.setName(to.getName());
        from.getNeighbour().add(neigh);

        Neighbour reverseNeigh = new Neighbour();
        reverseNeigh.setName(from.getName());
        to.getNeighbour().add(reverseNeigh);
    }

    private void createSecurityPolicies(NFV nfv, Graph graph, int numberClients, int numberServers, int numSecurityRequirements) {
        Set<String> uniquePairs = new HashSet<>();
        int generatedRequirements = 0;

        while (generatedRequirements < numSecurityRequirements) {
            int clientIndex = rand.nextInt(numberClients);
            int serverIndex = rand.nextInt(numberServers);
            String clientName = allClients.get(clientIndex).getName();
            String serverName = allServers.get(serverIndex).getName();

            if (!uniquePairs.contains(clientName + serverName)) {
                boolean isReachability = rand.nextBoolean();
                if (isReachability) {
                    createPolicy(PName.REACHABILITY_PROPERTY, nfv, graph, clientName, serverName);
                } else {
                    createPolicy(PName.ISOLATION_PROPERTY, nfv, graph, clientName, serverName);
                }
                uniquePairs.add(clientName + serverName);
                generatedRequirements++;
            }
        }
    }

    private void createPolicy(PName type, NFV nfv, Graph graph, String src, String dst) {
        Property property = new Property();
        property.setName(type);
        property.setGraph((long) 0);
        property.setSrc(src);
        property.setDst(dst);
        property.setLv4Proto(L4ProtocolTypes.ANY);
        property.setSrcPort("*");
        property.setDstPort("*");

        if (type.equals(PName.ISOLATION_PROPERTY)) {
            property.setBody("DENY");
        } else if (type.equals(PName.REACHABILITY_PROPERTY)) {
            property.setBody("ALLOW");
        }

        nfv.getPropertyDefinition().getProperty().add(property);
    }

    private Node createSwitch() {
        String ip = createRandomIP();
        Node sw = new Node();
        sw.setName(ip);
        sw.setFunctionalType(FunctionalTypes.FORWARDER);
        return sw;
    }

    private Node createClient(Random rand) {
        String IPClient = createRandomIP();
        Node client = new Node();
        client.setFunctionalType(FunctionalTypes.WEBCLIENT);
        client.setName(IPClient);

        Configuration confC = new Configuration();
        confC.setName("confC");
        Webclient wc = new Webclient();
        wc.setNameWebServer(allServers.get(rand.nextInt(allServers.size())).getName());
        confC.setWebclient(wc);
        client.setConfiguration(confC);
        return client;
    }

    private Node createServer() {
        String IPServer = createRandomIP();
        Node server = new Node();
        server.setFunctionalType(FunctionalTypes.WEBSERVER);
        server.setName(IPServer);

        Configuration confS = new Configuration();
        confS.setName("confS");
        Webserver ws = new Webserver();
        ws.setName(server.getName());
        confS.setWebserver(ws);
        server.setConfiguration(confS);
        return server;
    }

    public List<Node> getAllClients() {
        return allClients;
    }

    public List<Node> getAllServers() {
        return allServers;
    }

    public NFV modifyNetworkPolicies(NFV nfv, double percReqKept) {
        List<Property> properties = nfv.getPropertyDefinition().getProperty();
        int numberKeptPolicies = (int) (properties.size() * percReqKept);
        int numberNewPolicies = properties.size() - numberKeptPolicies;

        // Mantieni le prime numberKeptPolicies
        List<Property> updatedPolicies = new ArrayList<>(properties.subList(0, numberKeptPolicies));

        // Traccia le coppie uniche di client e server
        Set<String> uniquePairs = new HashSet<>();
        for (Property property : updatedPolicies) {
            uniquePairs.add(property.getSrc() + property.getDst());
        }

        // Genera nuove politiche
        int generatedPolicies = 0;
        while (generatedPolicies < numberNewPolicies) {
            String src = rand.nextBoolean() ? allClients.get(rand.nextInt(allClients.size())).getName()
                    : allServers.get(rand.nextInt(allServers.size())).getName();
            String dst = rand.nextBoolean() ? allClients.get(rand.nextInt(allClients.size())).getName()
                    : allServers.get(rand.nextInt(allServers.size())).getName();
            if (!src.equals(dst) && !uniquePairs.contains(src + dst)) {
                PName policyType = rand.nextBoolean() ? PName.REACHABILITY_PROPERTY : PName.ISOLATION_PROPERTY;
                createPolicy(policyType, nfv, nfv.getGraphs().getGraph().get(0), src, dst);
                uniquePairs.add(src + dst);
                generatedPolicies++;
            }
        }

        nfv.getPropertyDefinition().getProperty().clear();
        nfv.getPropertyDefinition().getProperty().addAll(updatedPolicies);
        return nfv;
    }
}