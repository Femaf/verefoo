package it.polito.verefoo.extra;

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

    public TestCaseGeneratorFatTree(String name, int numberPods, int numberClientsPerPod, int numberServersPerPod, int seed) {
        this.name = name;
        this.rand = new Random(seed);

        allClients = new ArrayList<>();
        allServers = new ArrayList<>();
        allSwitches = new ArrayList<>();

        allIPs = new HashSet<>();
        nfv = generateNFV(numberPods, numberClientsPerPod, numberServersPerPod, rand);
    }

    public NFV changeIP(int numberPods, int numberClientsPerPod, int numberServersPerPod, int seed) {
        this.rand = new Random(seed);
        allClients = new ArrayList<>();
        allServers = new ArrayList<>();
        allSwitches = new ArrayList<>();

        allIPs = new HashSet<>();
        return generateNFV(numberPods, numberClientsPerPod, numberServersPerPod, rand);
    }

    private String createIP() {
        String ip;
        int first, second, third, forth;
        first = rand.nextInt(256);
        if (first == 0) first++;
        second = rand.nextInt(256);
        third = rand.nextInt(256);
        forth = rand.nextInt(256);
        ip = first + "." + second + "." + third + "." + forth;
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

    public NFV generateNFV(int numberPods, int numberClientsPerPod, int numberServersPerPod, Random rand) {
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

        // Create servers
        for (int i = 0; i < numberPods * numberServersPerPod; i++) {
            Node server = createServer();
            allServers.add(server);
        }

        // Create clients
        for (int i = 0; i < numberPods * numberClientsPerPod; i++) {
            Node client = createClient(rand);
            allClients.add(client);
        }

        // Create switches
        for (int i = 0; i < numberPods * 2; i++) { // 2 switches per pod (core and aggregate)
            Node sw = createSwitch();
            allSwitches.add(sw);
        }

        // Attach nodes within each pod
        for (int pod = 0; pod < numberPods; pod++) {
            Node coreSwitch = allSwitches.get(pod * 2);
            Node aggSwitch = allSwitches.get(pod * 2 + 1);

            // Connect core switch to aggregate switch
            addLink(coreSwitch, aggSwitch);

            // Connect aggregate switch to servers
            for (int s = 0; s < numberServersPerPod; s++) {
                Node server = allServers.get(pod * numberServersPerPod + s);
                addLink(aggSwitch, server);
            }

            // Connect aggregate switch to clients
            for (int c = 0; c < numberClientsPerPod; c++) {
                Node client = allClients.get(pod * numberClientsPerPod + c);
                addLink(aggSwitch, client);
            }
        }

        // Add nodes to graph
        graph.getNode().addAll(allClients);
        graph.getNode().addAll(allServers);
        graph.getNode().addAll(allSwitches);
        nfv.getGraphs().getGraph().add(graph);

        // Create properties (policies)
        for (int i = 0; i < numberClientsPerPod * numberPods; i++) {
            createPolicy(PName.REACHABILITY_PROPERTY, nfv, graph, allClients.get(i).getName(), allServers.get(i % allServers.size()).getName());
        }

        return nfv;
    }

    private void addLink(Node from, Node to) {
        Neighbour neigh = new Neighbour();
        neigh.setName(to.getName());
        from.getNeighbour().add(neigh);

        Neighbour reverseNeigh = new Neighbour();
        reverseNeigh.setName(from.getName());
        to.getNeighbour().add(reverseNeigh);
    }

    private void createPolicy(PName type, NFV nfv, Graph graph, String src, String dst) {
        Property property = new Property();
        property.setName(type);
        property.setGraph((long) 0);
        property.setSrc(src);
        property.setDst(dst);
        nfv.getPropertyDefinition().getProperty().add(property);
    }

    private Node createSwitch() {
        String ip = createRandomIP();
        Node sw = new Node();
        sw.setName(ip);
        sw.setFunctionalType(FunctionalTypes.SWITCH);
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

    public NFV getNfv() {
        return nfv;
    }

    public void setNfv(NFV nfv) {
        this.nfv = nfv;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

