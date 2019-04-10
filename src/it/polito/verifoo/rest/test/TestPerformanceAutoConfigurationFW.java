/**
 * 
 */
package it.polito.verifoo.rest.test;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;



import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.SAXException;

import com.microsoft.z3.Status;

import it.polito.verifoo.rest.common.BadGraphError;
import it.polito.verifoo.rest.common.Translator;
import it.polito.verifoo.rest.common.VerifooProxy;
import it.polito.verifoo.rest.common.VerifooSerializer;
import it.polito.verifoo.rest.jaxb.FunctionalTypes;
import it.polito.verifoo.rest.jaxb.Graph;
import it.polito.verifoo.rest.jaxb.Host;
import it.polito.verifoo.rest.jaxb.NFV;
import it.polito.verifoo.rest.jaxb.Path;
import it.polito.verifoo.rest.jaxb.Property;
import it.polito.verifoo.rest.jaxb.TypeOfHost;
import it.polito.verigraph.mcnet.components.IsolationResult;
/**
 * 
 * This class runs some tests to collect some data about the performance of the tool 
 *
 */
public class TestPerformanceAutoConfigurationFW {
	private long condTime = 0, checkTimeSAT = 0, checkTimeUNSAT = 0, totTime = 0;
	private long maxCondTime = 0, maxCheckTimeSAT = 0, maxCheckTimeUNSAT = 0, maxTotTime = 0;
	private int nSAT = 0, nUNSAT = 0, i = 0, err = 0, nrOfConditions = 0, maxNrOfConditions = 0;
	NFV root;
	private List<Host> pastClients = new ArrayList<>(), pastServers = new ArrayList<>();
	private Logger logger = LogManager.getLogger("result");
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
	
	private NFV init(NFV root) throws JAXBException, SAXException, FileNotFoundException{
		List<Path> paths = null;
		if(root.getNetworkForwardingPaths() != null)
			paths = root.getNetworkForwardingPaths().getPath();
        for(Graph g:root.getGraphs().getGraph()){
        	long beginVP=System.currentTimeMillis();
        	List<Property> prop = root.getPropertyDefinition().getProperty().stream().filter(p -> p.getGraph()==g.getId()).collect(Collectors.toList());
        	VerifooProxy test = new VerifooProxy(g, root.getHosts(), root.getConnections(),root.getConstraints(), prop, paths);
        	long endVP=System.currentTimeMillis();
        	condTime += (endVP-beginVP);
        	maxCondTime = maxCondTime<(endVP-beginVP)? (endVP-beginVP) : condTime;
            //System.out.println("Graph " + g.getId() + ": creating condition -> " + ((endVP-beginVP)) + "ms");
        	IsolationResult res=test.checkNFFGProperty();
        	nrOfConditions += test.getNrOfConditions();
        	maxNrOfConditions = maxNrOfConditions<test.getNrOfConditions()? test.getNrOfConditions() : maxNrOfConditions;
        	long endCheck=System.currentTimeMillis();
            //System.out.println("Graph " + g.getId() + ": checking property -> " + ((endCheck-endVP)) + "ms");
        	if(res.result != Status.UNSATISFIABLE){
            	checkTimeSAT += (endCheck-endVP);
            	maxCheckTimeSAT = maxCheckTimeSAT<(endCheck-endVP)? (endCheck-endVP) : maxCheckTimeSAT;
        		nSAT++;
        		new Translator(res.model.toString(),root,g).convert();
        	}
        	else{
        		checkTimeUNSAT += (endCheck-endVP);
        		maxCheckTimeUNSAT = maxCheckTimeUNSAT<(endCheck-endVP)? (endCheck-endVP) : maxCheckTimeUNSAT;
        		nUNSAT++;
        	}
        	root.getPropertyDefinition().getProperty().stream().filter(p->p.getGraph()==g.getId()).forEach(p -> p.setIsSat(res.result!=Status.UNSATISFIABLE)); 
        	//long endT=System.currentTimeMillis();
            //System.out.println(g.getId() + ": translating model -> " + ((endT-endCheck)/1000) + "s");
        }
		return root;
		
	}
	
	private void testCoarse(NFV root) throws Exception{
		//System.out.println("===========FILE " + file + "===========");
		long beginAll=System.currentTimeMillis();
		VerifooSerializer test = new VerifooSerializer(root);
		long endAll=System.currentTimeMillis();
		 if(test.isSat()){
			nSAT++;
			maxTotTime = maxTotTime<(endAll-beginAll)? (endAll-beginAll) : maxTotTime;
			logger.info("time: " + (endAll-beginAll) + "ms;");
			totTime += (endAll-beginAll);
		 }
	 	else{
	 		logger.info("UNSAT");	
			nUNSAT++;
	 	}
		
        return;
	}
	
	private void test(NFV root) throws Exception{
        //System.out.println("===========FILE " + file + "===========");
		long beginAll=System.currentTimeMillis();
		NFV rootTest = init(root);
		long endAll=System.currentTimeMillis();
        //System.out.println("Total time -> " + ((endAll-beginAll)/1000) + "s");
		Property p = rootTest.getPropertyDefinition().getProperty().get(0);
    	if(p.isIsSat()){
			maxTotTime = maxTotTime<(endAll-beginAll)? (endAll-beginAll) : maxTotTime;
			System.out.print("time: " + (endAll-beginAll) + "ms;");
			totTime += (endAll-beginAll);
    	}
        return;
	}
	
	private Host changeFixedClient(List<Host> hosts, String client){
		Host currClient = hosts.stream().filter(h -> h.getType().equals(TypeOfHost.CLIENT) && h.getFixedEndpoint().equals(client) ).findAny().orElse(null);

		pastClients.add(currClient);
		Host newClient = hosts.stream().filter(h -> !pastClients.contains(h) && h.getType().equals(TypeOfHost.MIDDLEBOX)).findAny().orElse(null);
		if(newClient != null){
			currClient.setType(TypeOfHost.MIDDLEBOX);
			currClient.setFixedEndpoint(null);
			//System.out.println("Host client changed to " + newClient.getName());
			newClient.setType(TypeOfHost.CLIENT);
			newClient.setFixedEndpoint(client);
		}
		return newClient;
	}
	
	private Host changeEndpoints(List<Host> hosts, String client, String server){
		Host tmp = changeFixedClient(hosts, client);
		if(tmp != null)
			return tmp;
		pastClients.clear();
		tmp = changeFixedClient(hosts, client);
		Host currServer = hosts.stream().filter(h -> h.getType().equals(TypeOfHost.SERVER) && h.getFixedEndpoint().equals(server) ).findAny().orElse(null);
		pastServers.add(currServer);
		currServer.setType(TypeOfHost.MIDDLEBOX);
		currServer.setFixedEndpoint(null);
		Host newServer = hosts.stream().filter(h -> !pastServers.contains(h) && h.getType().equals(TypeOfHost.MIDDLEBOX)).findAny().orElse(null);
		if(newServer != null){
			//System.out.println("Host server changed to " + newServer.getName());
			newServer.setType(TypeOfHost.SERVER);
			newServer.setFixedEndpoint(server);
		}
		return newServer;
	}
	
	@Test
	public void testBGPerformance(){
		try {
			List<String> files = new ArrayList<>();
			
			
			//Extended
			files.add("./testfile/PerformanceTests/FirewallPolicy/Extended/05FW10P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/Extended/05FW20P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/Extended/05FW30P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/Extended/05FW40P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/Extended/05FW50P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/Extended/05FW60P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/Extended/05FW70P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/Extended/05FW80P.xml");
			
			files.add("./testfile/PerformanceTests/FirewallPolicy/Extended/15FW10P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/Extended/15FW20P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/Extended/15FW30P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/Extended/15FW40P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/Extended/15FW50P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/Extended/15FW60P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/Extended/15FW70P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/Extended/15FW80P.xml");
			
			files.add("./testfile/PerformanceTests/FirewallPolicy/Extended/25FW10P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/Extended/25FW20P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/Extended/25FW30P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/Extended/25FW40P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/Extended/25FW50P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/Extended/25FW60P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/Extended/25FW70P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/Extended/25FW80P.xml");
			
			
			files.add("./testfile/PerformanceTests/FirewallPolicy/ExtendedFW/10FW05P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/ExtendedFW/20FW05P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/ExtendedFW/30FW05P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/ExtendedFW/40FW05P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/ExtendedFW/50FW05P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/ExtendedFW/60FW05P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/ExtendedFW/70FW05P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/ExtendedFW/80FW05P.xml");

			files.add("./testfile/PerformanceTests/FirewallPolicy/ExtendedFW/10FW10P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/ExtendedFW/20FW10P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/ExtendedFW/30FW10P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/ExtendedFW/40FW10P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/ExtendedFW/50FW10P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/ExtendedFW/60FW10P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/ExtendedFW/70FW10P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/ExtendedFW/80FW10P.xml");

			files.add("./testfile/PerformanceTests/FirewallPolicy/ExtendedFW/10FW15P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/ExtendedFW/20FW15P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/ExtendedFW/30FW15P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/ExtendedFW/40FW15P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/ExtendedFW/50FW15P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/ExtendedFW/60FW15P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/ExtendedFW/70FW15P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/ExtendedFW/80FW15P.xml");
			
			
			
			/*files.add("./testfile/PerformanceTests/FirewallPolicy/ChainSG/06FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/ChainSG/12FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/ChainSG/18FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/ChainSG/24FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/ChainSG/30FW.xml");*/
			
			
			
			/*files.add("./testfile/PerformanceTests/FirewallPolicy/ChainSG/06FWG.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/ChainSG/12FWG.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/ChainSG/18FWG.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/ChainSG/24FWG.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/ChainSG/30FWG.xml");*/
			
			/*files.add("./testfile/PerformanceTests/FirewallPolicy/05Policy/06FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/05Policy/12FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/05Policy/18FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/05Policy/24FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/05Policy/30FW.xml");
			
			files.add("./testfile/PerformanceTests/FirewallPolicy/03Policy/10FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/06Policy/10FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/09Policy/10FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/12Policy/10FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/15Policy/10FW.xml");*/
			
			/*files.add("./testfile/OldPerformance/Refinement/refNoTopology-1FW1P.xml");
			files.add("./testfile/OldPerformance/Refinement/refNoTopology-1FW2P.xml");
			files.add("./testfile/OldPerformance/Refinement/refNoTopology-1FW3P.xml");
			files.add("./testfile/OldPerformance/Refinement/refNoTopology-1FW4P.xml");
			files.add("./testfile/OldPerformance/Refinement/refNoTopology-2FW1P.xml");
			files.add("./testfile/OldPerformance/Refinement/refNoTopology-2FW2P.xml");
			files.add("./testfile/OldPerformance/Refinement/refNoTopology-2FW3P.xml");
			files.add("./testfile/OldPerformance/Refinement/refNoTopology-2FW4P.xml");
			files.add("./testfile/OldPerformance/Refinement/refNoTopology-3FW1P.xml");
			files.add("./testfile/OldPerformance/Refinement/refNoTopology-3FW2P.xml");
			files.add("./testfile/OldPerformance/Refinement/refNoTopology-3FW3P.xml");
			files.add("./testfile/OldPerformance/Refinement/refNoTopology-3FW4P.xml");*/
			
			/*
			//01Policy
			files.add("./testfile/PerformanceTests/FirewallPolicy/01Policy/01FW01P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/01Policy/02FW01P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/01Policy/03FW01P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/01Policy/04FW01P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/01Policy/05FW01P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/01Policy/06FW01P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/01Policy/07FW01P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/01Policy/08FW01P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/01Policy/09FW01P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/01Policy/10FW01P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/01Policy/11FW01P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/01Policy/12FW01P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/01Policy/13FW01P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/01Policy/14FW01P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/01Policy/15FW01P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/01Policy/16FW01P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/01Policy/17FW01P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/01Policy/18FW01P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/01Policy/19FW01P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/01Policy/20FW01P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/01Policy/21FW01P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/01Policy/22FW01P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/01Policy/23FW01P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/01Policy/24FW01P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/01Policy/25FW01P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/01Policy/26FW01P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/01Policy/27FW01P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/01Policy/28FW01P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/01Policy/29FW01P.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/01Policy/30FW01P.xml");
			
			//02Policy
			files.add("./testfile/PerformanceTests/FirewallPolicy/02Policy/01FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/02Policy/02FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/02Policy/03FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/02Policy/04FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/02Policy/05FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/02Policy/06FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/02Policy/07FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/02Policy/08FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/02Policy/09FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/02Policy/10FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/02Policy/11FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/02Policy/12FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/02Policy/13FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/02Policy/14FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/02Policy/15FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/02Policy/16FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/02Policy/17FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/02Policy/18FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/02Policy/19FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/02Policy/20FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/02Policy/21FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/02Policy/22FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/02Policy/23FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/02Policy/24FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/02Policy/25FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/02Policy/26FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/02Policy/27FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/02Policy/28FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/02Policy/29FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/02Policy/30FW.xml");
			
			//03Policy
			files.add("./testfile/PerformanceTests/FirewallPolicy/03Policy/01FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/03Policy/02FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/03Policy/03FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/03Policy/04FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/03Policy/05FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/03Policy/06FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/03Policy/07FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/03Policy/08FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/03Policy/09FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/03Policy/10FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/03Policy/11FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/03Policy/12FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/03Policy/13FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/03Policy/14FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/03Policy/15FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/03Policy/16FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/03Policy/17FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/03Policy/18FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/03Policy/19FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/03Policy/20FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/03Policy/21FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/03Policy/22FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/03Policy/23FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/03Policy/24FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/03Policy/25FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/03Policy/26FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/03Policy/27FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/03Policy/28FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/03Policy/29FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/03Policy/30FW.xml");
			
			//04Policy
			files.add("./testfile/PerformanceTests/FirewallPolicy/04Policy/01FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/04Policy/02FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/04Policy/03FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/04Policy/04FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/04Policy/05FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/04Policy/06FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/04Policy/07FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/04Policy/08FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/04Policy/09FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/04Policy/10FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/04Policy/11FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/04Policy/12FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/04Policy/13FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/04Policy/14FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/04Policy/15FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/04Policy/16FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/04Policy/17FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/04Policy/18FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/04Policy/19FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/04Policy/20FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/04Policy/21FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/04Policy/22FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/04Policy/23FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/04Policy/24FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/04Policy/25FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/04Policy/26FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/04Policy/27FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/04Policy/28FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/04Policy/29FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/04Policy/30FW.xml");
			
			//05Policy
			files.add("./testfile/PerformanceTests/FirewallPolicy/05Policy/01FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/05Policy/02FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/05Policy/03FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/05Policy/04FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/05Policy/05FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/05Policy/06FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/05Policy/07FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/05Policy/08FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/05Policy/09FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/05Policy/10FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/05Policy/11FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/05Policy/12FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/05Policy/13FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/05Policy/14FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/05Policy/15FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/05Policy/16FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/05Policy/17FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/05Policy/18FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/05Policy/19FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/05Policy/20FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/05Policy/21FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/05Policy/22FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/05Policy/23FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/05Policy/24FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/05Policy/25FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/05Policy/26FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/05Policy/27FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/05Policy/28FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/05Policy/29FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/05Policy/30FW.xml");
			
			//06Policy
			files.add("./testfile/PerformanceTests/FirewallPolicy/06Policy/01FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/06Policy/02FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/06Policy/03FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/06Policy/04FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/06Policy/05FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/06Policy/06FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/06Policy/07FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/06Policy/08FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/06Policy/09FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/06Policy/10FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/06Policy/11FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/06Policy/12FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/06Policy/13FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/06Policy/14FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/06Policy/15FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/06Policy/16FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/06Policy/17FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/06Policy/18FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/06Policy/19FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/06Policy/20FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/06Policy/21FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/06Policy/22FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/06Policy/23FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/06Policy/24FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/06Policy/25FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/06Policy/26FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/06Policy/27FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/06Policy/28FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/06Policy/29FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/06Policy/30FW.xml");
			
			//07Policy
			files.add("./testfile/PerformanceTests/FirewallPolicy/07Policy/01FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/07Policy/02FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/07Policy/03FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/07Policy/04FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/07Policy/05FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/07Policy/06FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/07Policy/07FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/07Policy/08FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/07Policy/09FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/07Policy/10FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/07Policy/11FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/07Policy/12FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/07Policy/13FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/07Policy/14FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/07Policy/15FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/07Policy/16FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/07Policy/17FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/07Policy/18FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/07Policy/19FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/07Policy/20FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/07Policy/21FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/07Policy/22FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/07Policy/23FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/07Policy/24FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/07Policy/25FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/07Policy/26FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/07Policy/27FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/07Policy/28FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/07Policy/29FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/07Policy/30FW.xml");
			
			//08Policy
			files.add("./testfile/PerformanceTests/FirewallPolicy/08Policy/01FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/08Policy/02FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/08Policy/03FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/08Policy/04FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/08Policy/05FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/08Policy/06FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/08Policy/07FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/08Policy/08FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/08Policy/09FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/08Policy/10FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/08Policy/11FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/08Policy/12FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/08Policy/13FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/08Policy/14FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/08Policy/15FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/08Policy/16FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/08Policy/17FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/08Policy/18FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/08Policy/19FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/08Policy/20FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/08Policy/21FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/08Policy/22FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/08Policy/23FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/08Policy/24FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/08Policy/25FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/08Policy/26FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/08Policy/27FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/08Policy/28FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/08Policy/29FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/08Policy/30FW.xml");
			
			//09Policy
			files.add("./testfile/PerformanceTests/FirewallPolicy/09Policy/01FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/09Policy/02FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/09Policy/03FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/09Policy/04FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/09Policy/05FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/09Policy/06FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/09Policy/07FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/09Policy/08FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/09Policy/09FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/09Policy/10FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/09Policy/11FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/09Policy/12FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/09Policy/13FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/09Policy/14FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/09Policy/15FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/09Policy/16FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/09Policy/17FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/09Policy/18FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/09Policy/19FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/09Policy/20FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/09Policy/21FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/09Policy/22FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/09Policy/23FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/09Policy/24FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/09Policy/25FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/09Policy/26FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/09Policy/27FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/09Policy/28FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/09Policy/29FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/09Policy/30FW.xml");
			
			//10Policy
			files.add("./testfile/PerformanceTests/FirewallPolicy/10Policy/01FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/10Policy/02FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/10Policy/03FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/10Policy/04FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/10Policy/05FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/10Policy/06FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/10Policy/07FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/10Policy/08FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/10Policy/09FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/10Policy/10FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/10Policy/11FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/10Policy/12FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/10Policy/13FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/10Policy/14FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/10Policy/15FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/10Policy/16FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/10Policy/17FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/10Policy/18FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/10Policy/19FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/10Policy/20FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/10Policy/21FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/10Policy/22FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/10Policy/23FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/10Policy/24FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/10Policy/25FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/10Policy/26FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/10Policy/27FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/10Policy/28FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/10Policy/29FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/10Policy/30FW.xml");
			
			//11Policy
			files.add("./testfile/PerformanceTests/FirewallPolicy/11Policy/01FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/11Policy/02FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/11Policy/03FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/11Policy/04FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/11Policy/05FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/11Policy/06FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/11Policy/07FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/11Policy/08FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/11Policy/09FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/11Policy/10FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/11Policy/11FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/11Policy/12FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/11Policy/13FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/11Policy/14FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/11Policy/15FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/11Policy/16FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/11Policy/17FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/11Policy/18FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/11Policy/19FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/11Policy/20FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/11Policy/21FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/11Policy/22FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/11Policy/23FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/11Policy/24FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/11Policy/25FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/11Policy/26FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/11Policy/27FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/11Policy/28FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/11Policy/29FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/11Policy/30FW.xml");
			
			//12Policy
			files.add("./testfile/PerformanceTests/FirewallPolicy/12Policy/01FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/12Policy/02FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/12Policy/03FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/12Policy/04FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/12Policy/05FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/12Policy/06FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/12Policy/07FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/12Policy/08FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/12Policy/09FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/12Policy/10FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/12Policy/11FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/12Policy/12FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/12Policy/13FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/12Policy/14FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/12Policy/15FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/12Policy/16FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/12Policy/17FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/12Policy/18FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/12Policy/19FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/12Policy/20FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/12Policy/21FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/12Policy/22FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/12Policy/23FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/12Policy/24FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/12Policy/25FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/12Policy/26FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/12Policy/27FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/12Policy/28FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/12Policy/29FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/12Policy/30FW.xml");
			
			
			//13Policy
			files.add("./testfile/PerformanceTests/FirewallPolicy/13Policy/01FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/13Policy/02FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/13Policy/03FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/13Policy/04FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/13Policy/05FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/13Policy/06FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/13Policy/07FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/13Policy/08FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/13Policy/09FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/13Policy/10FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/13Policy/11FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/13Policy/12FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/13Policy/13FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/13Policy/14FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/13Policy/15FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/13Policy/16FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/13Policy/17FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/13Policy/18FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/13Policy/19FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/13Policy/20FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/13Policy/21FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/13Policy/22FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/13Policy/23FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/13Policy/24FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/13Policy/25FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/13Policy/26FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/13Policy/27FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/13Policy/28FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/13Policy/29FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/13Policy/30FW.xml");
			
			//14Policy
			files.add("./testfile/PerformanceTests/FirewallPolicy/14Policy/01FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/14Policy/02FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/14Policy/03FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/14Policy/04FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/14Policy/05FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/14Policy/06FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/14Policy/07FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/14Policy/08FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/14Policy/09FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/14Policy/10FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/14Policy/11FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/14Policy/12FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/14Policy/13FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/14Policy/14FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/14Policy/15FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/14Policy/16FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/14Policy/17FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/14Policy/18FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/14Policy/19FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/14Policy/20FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/14Policy/21FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/14Policy/22FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/14Policy/23FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/14Policy/24FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/14Policy/25FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/14Policy/26FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/14Policy/27FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/14Policy/28FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/14Policy/29FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/14Policy/30FW.xml");
			
			//15Policy
			files.add("./testfile/PerformanceTests/FirewallPolicy/15Policy/01FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/15Policy/02FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/15Policy/03FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/15Policy/04FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/15Policy/05FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/15Policy/06FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/15Policy/07FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/15Policy/08FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/15Policy/09FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/15Policy/10FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/15Policy/11FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/15Policy/12FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/15Policy/13FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/15Policy/14FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/15Policy/15FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/15Policy/16FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/15Policy/17FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/15Policy/18FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/15Policy/19FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/15Policy/20FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/15Policy/21FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/15Policy/22FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/15Policy/23FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/15Policy/24FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/15Policy/25FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/15Policy/26FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/15Policy/27FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/15Policy/28FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/15Policy/29FW.xml");
			files.add("./testfile/PerformanceTests/FirewallPolicy/15Policy/30FW.xml");
			
			
			
			//Other
			//files.add("./testfile/PerformanceTests/NFWFixedNode/3FW.xml");
			//files.add("./testfile/PerformanceTests/NFWFixedNode/7FW.xml");
			//files.add("./testfile/PerformanceTests/NFWFixedNode/15FW.xml");
			//files.add("./testfile/PerformanceTests/NFWFixedNode/22FW.xml");
			//files.add("./testfile/PerformanceTests/NFWFixedNode/31FW.xml");
			//files.add("./testfile/PerformanceTests/NFWFixedNode/Chain22FW.xml");
			//files.add("./testfile/PerformanceTests/100Nodes/05FW.xml");
			//files.add("./testfile/PerformanceTests/100Nodes/10FW.xml");
			//files.add("./testfile/PerformanceTests/100Nodes/15FW.xml");
			//files.add("./testfile/PerformanceTests/100Nodes/20FW.xml");
			//files.add("./testfile/PerformanceTests/100Nodes/20FW15P.xml");
			
			
		
			*/
			for(String f : files){
				condTime = 0;
				checkTimeSAT = 0;
				checkTimeUNSAT = 0;
				totTime = 0;
				maxCondTime = 0;
				maxCheckTimeSAT = 0;
				maxCheckTimeUNSAT = 0;
				maxTotTime = 0;
				nSAT = 0;
				nUNSAT = 0;
				i = 0;
				err = 0;
				System.out.println("===========FILE " + f + "===========");
				logger.debug("===========FILE " + f + "===========");
				// create a JAXBContext capable of handling the generated classes
				//long beginAll=System.currentTimeMillis();
		        JAXBContext jc = JAXBContext.newInstance( "it.polito.verifoo.rest.jaxb" );
		        // create an Unmarshaller
		        Unmarshaller u = jc.createUnmarshaller();
		        SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI); 
		        Schema schema = sf.newSchema( new File( "./xsd/nfvSchema.xsd" )); 
		        u.setSchema(schema);
		        // unmarshal a document into a tree of Java content objects
		        root = (NFV) u.unmarshal( new FileInputStream( f ) );
		       /* String clientName = root.getGraphs().getGraph().stream()
		        		.flatMap(g -> g.getNode().stream())
		        		.filter(n -> n.getFunctionalType().equals(FunctionalTypes.WEBCLIENT) 
		        				|| n.getFunctionalType().equals(FunctionalTypes.MAILCLIENT)
		        				|| n.getFunctionalType().equals(FunctionalTypes.ENDHOST))
		        		.map(n -> n.getName())
		        		.findFirst().get();
		        String serverName = root.getGraphs().getGraph().stream()
		        		.flatMap(g -> g.getNode().stream())
		        		.filter(n -> n.getFunctionalType().equals(FunctionalTypes.WEBSERVER) 
		        				|| n.getFunctionalType().equals(FunctionalTypes.MAILSERVER))
		        		.map(n -> n.getName())
		        		.findFirst().get();*/
		        
		        do{
					//logger.debug("Simulation nr " + i+" ");
					Thread t = new Thread(){
						public void run(){
							try {
								root = (NFV) u.unmarshal( new FileInputStream( f ) );
								testCoarse(root);
								i++;
								if(i%50 == 0) System.out.println("");
							} catch (Exception e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
								err++;
							}
						}
					};
					t.start();
					//avoid deadlock
					t.join(6000000);
					if(t.isAlive()){
						if(root.getHosts() != null){
							Host currClient = root.getHosts().getHost().stream().filter(h -> h.getType().equals(TypeOfHost.CLIENT)).findAny().orElse(null);
							Host currServer = root.getHosts().getHost().stream().filter(h -> h.getType().equals(TypeOfHost.SERVER)).findAny().orElse(null);
							logger.debug("Simulation " + i + " has deadlock with client on " + currClient.getName() + " and server on " + currServer.getName());
						}
						throw new BadGraphError();
					}
				//}while(changeEndpoints(root.getHosts().getHost(), clientName, serverName) != null);
				}while(i<5);
				
				logger.debug("Simulations -> " + i + " / Errors -> " + err);
				//System.out.println("AVG Nr of Conditions -> " + (nrOfConditions/(i)) + " / MAX Nr Of Conditions -> " + maxNrOfConditions);
				//System.out.println("AVG creating condition -> " + (condTime/(i-err)) + "ms");
				//System.out.println("MAX creating condition -> " + (maxCondTime) + "ms");
				//logger.debug("AVG creating condition -> " + (condTime/(i-err)) + "ms");
				//logger.debug("MAX creating condition -> " + (maxCondTime) + "ms");
				if(nSAT > 0){
					//System.out.println("AVG checking property when SAT -> " + (checkTimeSAT/nSAT) + "ms");
					//System.out.println("MAX checking property when SAT -> " + (maxCheckTimeSAT) + "ms");
					//logger.debug("AVG checking property when SAT -> " + (checkTimeSAT/nSAT) + "ms");
					//logger.debug("MAX checking property when SAT -> " + (maxCheckTimeSAT) + "ms");
				}
				if(nUNSAT > 0){
					//System.out.println("AVG checking property when UNSAT-> " + (checkTimeUNSAT/nUNSAT) + "ms");
					//System.out.println("MAX checking property when UNSAT-> " + (maxCheckTimeUNSAT) + "ms");
					logger.debug("AVG checking property when UNSAT-> " + (checkTimeUNSAT/nUNSAT) + "ms");
					logger.debug("MAX checking property when UNSAT-> " + (maxCheckTimeUNSAT) + "ms");
				}
				logger.debug("AVG total time -> " + (totTime/nSAT) + "ms");
				logger.debug("MAX total time -> " + (maxTotTime) + "ms");
				logger.debug("=====================================");


			}
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.toString());
		}
	}
}
