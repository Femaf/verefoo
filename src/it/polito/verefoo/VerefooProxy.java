package it.polito.verefoo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.microsoft.z3.Context;

import it.polito.verefoo.allocation.AllocationManager;
import it.polito.verefoo.allocation.AllocationNode;
import it.polito.verefoo.extra.BadGraphError;
import it.polito.verefoo.extra.WildcardManager;
import it.polito.verefoo.graph.FlowPath;
import it.polito.verefoo.graph.IPAddress;
import it.polito.verefoo.graph.Predicate;
import it.polito.verefoo.graph.SecurityRequirement;
import it.polito.verefoo.graph.Traffic;
import it.polito.verefoo.graph.Flow;
import it.polito.verefoo.jaxb.*;
import it.polito.verefoo.jaxb.NodeConstraints.NodeMetrics;
import it.polito.verefoo.jaxb.Path.PathNode;
import it.polito.verefoo.solver.*;
import it.polito.verefoo.solver.Checker.Prop;
import it.polito.verefoo.utils.APUtils;
import it.polito.verefoo.utils.VerificationResult;

/**
 * 
 * This is the main class that will interface with the Verefoo classes
 *
 */
public class VerefooProxy {
	private Context ctx;
	private NetContext nctx;
	private List<Property> properties;
	private List<Path> paths;
	private WildcardManager wildcardManager;
	private HashMap<String, AllocationNode> allocationNodes;
	private HashMap<Integer, Flow> trafficFlowsMap;
	private HashMap<Integer, SecurityRequirement> securityRequirements;
	public Checker check;
	private List<Node> nodes;
	private List<NodeMetrics> nodeMetrics;
	private AllocationManager allocationManager;
	private APUtils aputils;
	
	/* Atomic predicates */
	private HashMap<Integer, Predicate> networkAtomicPredicatesNew = new HashMap<>();
	HashMap<String, Node> transformersNode = new HashMap<>();
	
	/**
	 * Public constructor for the Verefoo proxy service
	 * 
	 * @param graph              The graph that will be deployed on the network
	 * @param hosts              The list of hosts in the network
	 * @param conns              The connections between hosts
	 * @param paths              the list of paths that the packet flows needs to
	 *                           follow
	 * @param capacityDefinition The list of the capacity for each node that will be
	 *                           deployed
	 * @throws BadGraphError
	 */
	public VerefooProxy(Graph graph, Hosts hosts, Connections conns, Constraints constraints, List<Property> prop,
			List<Path> paths) throws BadGraphError {
		
		// Initialitation of the variables related to the nodes
		allocationNodes = new HashMap<>();
		nodes = graph.getNode();
		nodes.forEach(n -> allocationNodes.put(n.getName(), new AllocationNode(n)));
		wildcardManager = new WildcardManager(allocationNodes);
		
		// Initialitation of the variables related to the requirements
		properties = prop;
		securityRequirements = new HashMap<>();
		int idRequirement = 0;
		for(Property p : properties) {
			securityRequirements.put(idRequirement, new SecurityRequirement(p, idRequirement));
			idRequirement++;
		}
		
		this.paths = paths;
		this.nodeMetrics = constraints.getNodeConstraints().getNodeMetrics();
		
		//Creation of the z3 context
		HashMap<String, String> cfg = new HashMap<String, String>();
		cfg.put("model", "true");
		ctx = new Context(cfg);
		aputils = new APUtils();
				
		//Creation of the NetContext (z3 variables)
		nctx = nctxGenerate(ctx, nodes, prop, allocationNodes);
		nctx.setWildcardManager(wildcardManager);
		
		/*
		 * Main sequence of methods in VerefooProxy:
		 * 1) given every requirement, all the possible paths of the related flows are computed;
		 * 2) the existing functions are istanciated
		 * 3) the functions to be allocated are associated to Allocation Places
		 * 4) the possible traffic in input to each node is computed
		 * 5) soft and hard constraints are defined for each function
		 * 6) the hard constraints for the requirements are defined
		 */
		
		/* Atomic predicates */
		trafficFlowsMap = generateFlowPaths();
		networkAtomicPredicatesNew = generateAtomicPredicateNew();
		fillTransformationMap();
		printTransformations(); //DEBUG
		computeAtomicFlows();
		
		allocationManager = new AllocationManager(ctx, nctx, allocationNodes, nodeMetrics, prop, wildcardManager);
		allocationManager.instantiateFunctions();
		allocateFunctions();
		distributeTrafficFlows();
		allocationManager.configureFunctions();
		
		check = new Checker(ctx, nctx, allocationNodes);
		formalizeRequirements();
		
	}
	
	private void computeAtomicFlows() {
		for(SecurityRequirement sr : securityRequirements.values()) {
			Property prop = sr.getOriginalProperty();
			System.out.println("\nSource predicates for requirement {"+prop.getSrc()+","+prop.getSrcPort()+","+prop.getDst()+","+prop.getDstPort()+","+prop.getLv4Proto()+"}");
			String pSrc = prop.getSrcPort() != null &&  !prop.getSrcPort().equals("null") ? prop.getSrcPort() : "*";
			//get all atomic predicates that match IPSrc and PSrc
			Predicate srcPredicate = new Predicate(prop.getSrc(), false, "*", false, pSrc, false, "*", false, L4ProtocolTypes.ANY);
			List<Integer> srcPredicateList = new ArrayList<>();
			for(HashMap.Entry<Integer, Predicate> apEntry: networkAtomicPredicatesNew.entrySet()) {
				Predicate intersectionPredicate = aputils.computeIntersection(apEntry.getValue(), srcPredicate);
				if(intersectionPredicate != null && aputils.APCompare(intersectionPredicate, apEntry.getValue())) {
					System.out.print(apEntry.getKey() + " ");
					apEntry.getValue().print();
					srcPredicateList.add(apEntry.getKey());
				}
			}
			
			System.out.println("Destination predicates");
			List<Integer> dstPredicateList = new ArrayList<>();
			String pDst = prop.getDstPort() != null &&  !prop.getDstPort().equals("null") ? prop.getDstPort() : "*";
			Predicate dstPredicate = new Predicate("*", false, prop.getDst(), false, "*", false, pDst, false, prop.getLv4Proto());
			//get all atomic predicates that match IPDst and PDst and prototype
			for(HashMap.Entry<Integer, Predicate> apEntry: networkAtomicPredicatesNew.entrySet()) {
				Predicate intersectionPredicate = aputils.computeIntersection(apEntry.getValue(), dstPredicate);
				if(intersectionPredicate != null && aputils.APCompare(intersectionPredicate, apEntry.getValue())) {
					System.out.print(apEntry.getKey() + " ");
					apEntry.getValue().print();
					dstPredicateList.add(apEntry.getKey());
				}
			}
			
			//Generate atomic flows
			for(Flow flow: sr.getFlowsMap().values()) {
				List<AllocationNode> path = flow.getPath().getNodes();
				List<List<Integer>> resultList = new ArrayList<>();
				List<List<Integer>> resultListToDiscard = new ArrayList<>();
				//now we have the requirement, the path and the list of source predicates -> call recursive function
				int nodeIndex = 0;
				for(Integer ap: srcPredicateList) {
					List<Integer> currentList = new ArrayList<>();
					recursiveGenerateAtomicPath(nodeIndex, sr, path, ap, dstPredicateList, resultList, resultListToDiscard, currentList);
				}
				
				//TODO: here we can insert the results into a map (or other structure)
				//DEBUG: print atomic flows for this path
				System.out.println("Atomic flows accepted");
				for(List<Integer> list: resultList) {
					int index = 0;
					for(Integer ap: list) {
						System.out.print(path.get(index).getIpAddress() + ", " + ap + ", ");
						index++;
					}
					System.out.println(path.get(index).getIpAddress());
				}
				System.out.println("Atomic flows discarded");
				for(List<Integer> list: resultListToDiscard) {
					int index = 0;
					for(Integer ap: list) {
						System.out.print(path.get(index).getIpAddress() + ", " + ap + ", ");
						index++;
					}
					System.out.println();
				}
				//END DEBUG	
			}
		}
	}
	
	private void recursiveGenerateAtomicPath(int nodeIndex, SecurityRequirement sr, List<AllocationNode> path, int ap, List<Integer> dstPredicateList, List<List<Integer>> atomicFlowsList, List<List<Integer>> atomicFlowsListToDiscard, List<Integer> currentList) {
		AllocationNode currentNode = path.get(nodeIndex);
		Predicate currentPredicate = networkAtomicPredicatesNew.get(ap);
		Predicate currentNodeDestPredicate = new Predicate("*", false, currentNode.getIpAddress(), false, "*", false, "*", false, L4ProtocolTypes.ANY);
		
		if(nodeIndex == path.size() -1) {
			//last node of the path
			if(dstPredicateList.contains(ap)) {
				//ALL OK, new atomic flow found
				atomicFlowsList.add(currentList);
				return;
			} else {
				//Discard path
				currentList.add(ap);
				atomicFlowsListToDiscard.add(currentList);
				return;
			}
		}
		
		Predicate intersectionPredicate = aputils.computeIntersection(currentPredicate, currentNodeDestPredicate);
		if(intersectionPredicate != null && aputils.APCompare(intersectionPredicate, currentPredicate)
				&& currentNode.getTransformationMap().isEmpty()) { //not NAT
			//Discard path: destination reached without reaching destination of the path
			currentList.add(ap);
			atomicFlowsListToDiscard.add(currentList);
			return;
		}
		
		//Apply transformation and filtering rules
		if(transformersNode.containsKey(currentNode.getIpAddress()) && transformersNode.get(currentNode.getIpAddress()).getFunctionalType().equals(FunctionalTypes.NAT)) { //NAT
			if(currentNode.getTransformationMap().containsKey(ap)) {
				for(Integer newAp: currentNode.getTransformationMap().get(ap)) {
					List<Integer> newCurrentList = new ArrayList<>(currentList);
					newCurrentList.add(newAp);
					recursiveGenerateAtomicPath(nodeIndex+1, sr, path, newAp, dstPredicateList, atomicFlowsList, atomicFlowsListToDiscard, newCurrentList);
				}
			} else {
				//Discard path: packet dropped
				currentList.add(ap);
				atomicFlowsListToDiscard.add(currentList);
				return;
			}
		} 
		else { //normal node
			List<Integer> newCurrentList = new ArrayList<>(currentList);
			newCurrentList.add(ap);
			recursiveGenerateAtomicPath(nodeIndex+1, sr, path, ap, dstPredicateList, atomicFlowsList, atomicFlowsListToDiscard, newCurrentList);
		}	
	}
		
	//DEBUG
	private void printTransformations() {
		for(String node: transformersNode.keySet()) {
			AllocationNode allocNode = allocationNodes.get(node);
			System.out.println("\nNODE " + node);
			System.out.println("Allowed rules");
			for(Predicate pred: allocNode.getForwardBehaviourPredicateList()) {
				pred.print();
			}
			System.out.println("Transformation map");
			for(HashMap.Entry<Integer, List<Integer>> entry: allocNode.getTransformationMap().entrySet()) {
				System.out.print(entry.getKey() + ":" );
				for(Integer res: entry.getValue())
					System.out.print(res + " ");
				System.out.println();
			}
			System.out.println("Allowed predicates");
			for(Integer i: allocNode.getForwardBehaviourList())
				System.out.print(i + " ");
			System.out.println();
		}
	}
	//END DEBUG
	
	/* Compute the structures of support for transformers: for each NAT compute the transforming map, for each FIREWALL its deny/allow lists 
	 * i.e. a NAT will have a map of entry for example {10: 5} which means that the atomic predicates 10 arrives at the nat and it is transformed in
	 * atomic predicate 5.
	 * a firewall instead will have a list of id foe example [1,2,5,6,10 ...], that are the identifiers of atomic predicates allowed to cross the firewall,
	 * all the other predicates will be dropped */
	private void fillTransformationMap() {
		for(Node node: transformersNode.values()) {
			HashMap<Integer, List<Integer>> resultMap = allocationNodes.get(node.getName()).getTransformationMap();
			if(node.getFunctionalType() == FunctionalTypes.NAT) {
				HashMap<String, List<Integer>> shadowingMap = new HashMap<>(); //grouped by dest address
				HashMap<String, List<Integer>> shadowedMap = new HashMap<>(); //grouped by dest address
				HashMap<String, List<Integer>> reconversionMap = new HashMap<>(); //grouped by source address
				HashMap<String, List<Integer>> reconvertedMap = new HashMap<>();  //grouped by source address
				List<IPAddress> natIPSrcAddressList = new ArrayList<>();
				for(String src: node.getConfiguration().getNat().getSource()) 
					natIPSrcAddressList.add(new IPAddress(src, false));
				IPAddress natIPAddress = new IPAddress(node.getName(), false);
				
				for(HashMap.Entry<Integer, Predicate> apEntry: networkAtomicPredicatesNew.entrySet()) {
					Predicate ap = apEntry.getValue();
					//if source ip address list or dest ip address list have size != 1, it means it is a complex predicates so it can not be a shodowing/reconversion predicates
					if(ap.getIPSrcListSize() != 1 || ap.getIPDstListSize() != 1) continue;
					if(ap.hasIPDstNotIncludedIn(natIPSrcAddressList) && !ap.hasIPDstEqual(natIPAddress)) {
						if(ap.hasIPSrcEqual(natIPAddress)) {
							//2*: if dest is not a src address of the NAT (so it is a public address) and ip source = ip NAT, this is a shadowed predicate
							//{IP NAT, public address}
							if(!shadowedMap.containsKey(ap.firstIPDstToString())) {
								List<Integer> list = new ArrayList<>();
								list.add(apEntry.getKey());
								shadowedMap.put(ap.firstIPDstToString(), list);
							} else {
								shadowedMap.get(ap.firstIPDstToString()).add(apEntry.getKey());
							}
						} 
						else {
							//1*: if dest is not a src address of the NAT (so it is a public address), while src is a src address of NAT (private address),
							//this is a shadowing predicates {private address, public address}
							if(ap.hasIPSrcEqualOrIncludedIn(natIPSrcAddressList))
								if(!shadowingMap.containsKey(ap.firstIPDstToString())) {
									List<Integer> list = new ArrayList<>();
									list.add(apEntry.getKey());
									shadowingMap.put(ap.firstIPDstToString(), list);
								} else {
									shadowingMap.get(ap.firstIPDstToString()).add(apEntry.getKey());
								}
						}
					} else if(ap.hasIPSrcNotIncludedIn(natIPSrcAddressList) && !ap.hasIPSrcEqual(natIPAddress)) {
						if(ap.hasIPDstEqual(natIPAddress)) {
							//3*: src not included in NAT src, dest = IP NAT -> reconversion predicate {public address, IP NAT}
							if(!reconversionMap.containsKey(ap.firstIPSrcToString())) {
								List<Integer> list = new ArrayList<>();
								list.add(apEntry.getKey());
								reconversionMap.put(ap.firstIPSrcToString(), list);
							} else {
								reconversionMap.get(ap.firstIPSrcToString()).add(apEntry.getKey());
							}
						} else if(ap.hasIPDstEqualOrIncludedIn(natIPSrcAddressList)) {
							//4*: src not included in NAT src, dest included in NAT src -> reconverted predicate {public address, private address}
							if(!reconvertedMap.containsKey(ap.firstIPSrcToString())) {
								List<Integer> list = new ArrayList<>();
								list.add(apEntry.getKey());
								reconvertedMap.put(ap.firstIPSrcToString(), list);
							} else {
								reconvertedMap.get(ap.firstIPSrcToString()).add(apEntry.getKey());
							}
						}
					}
				}
				//Fill the map: to each shadowing predicate assign the corresponding shadowed predicate, to each reconversion predicate assign the corresponding
				//list of reconverted predicates. NOTE: take also in consideration the ports and prototype of the predicate
				for(HashMap.Entry<String, List<Integer>> entry: shadowingMap.entrySet()) {
					for(Integer shing: entry.getValue()) {
						List<Integer> result = new ArrayList<>();
						for(Integer shed: shadowedMap.get(entry.getKey())) {
							if(aputils.APComparePrototypeList(
									networkAtomicPredicatesNew.get(shing).getProtoTypeList(), networkAtomicPredicatesNew.get(shed).getProtoTypeList())
									&& aputils.APComparePortList(networkAtomicPredicatesNew.get(shing).getpSrcList(), networkAtomicPredicatesNew.get(shed).getpSrcList())
									&& aputils.APComparePortList(networkAtomicPredicatesNew.get(shing).getpDstList(), networkAtomicPredicatesNew.get(shed).getpDstList())) 
								result.add(shed);
						}
						resultMap.put(shing, result);
					}
				}
				for(HashMap.Entry<String, List<Integer>> entry: reconversionMap.entrySet()) {
					for(Integer rcvion: entry.getValue()) {
						List<Integer> result = new ArrayList<>();
						for(Integer rcved: reconvertedMap.get(entry.getKey())) {
							if(aputils.APComparePrototypeList(
									networkAtomicPredicatesNew.get(rcvion).getProtoTypeList(), networkAtomicPredicatesNew.get(rcved).getProtoTypeList())
									&& aputils.APComparePortList(networkAtomicPredicatesNew.get(rcvion).getpSrcList(), networkAtomicPredicatesNew.get(rcved).getpSrcList())
									&& aputils.APComparePortList(networkAtomicPredicatesNew.get(rcvion).getpDstList(), networkAtomicPredicatesNew.get(rcved).getpDstList()))
								result.add(rcved);
						}
						resultMap.put(rcvion, result);
					}
				}
			} else if(node.getFunctionalType() == FunctionalTypes.FIREWALL) {
				List<Predicate> allowedPredicates = allocationNodes.get(node.getName()).getForwardBehaviourPredicateList();
				List<Integer> resultList = new ArrayList<>();
				for(HashMap.Entry<Integer, Predicate> apEntry: networkAtomicPredicatesNew.entrySet()) {
					//check if the atomic predicate match at least one allowed rule
					for(Predicate allowed: allowedPredicates) {
						Predicate intersectionPredicate = aputils.computeIntersection(apEntry.getValue(), allowed);
						if(intersectionPredicate != null && aputils.APCompare(intersectionPredicate, apEntry.getValue())) {
							resultList.add(apEntry.getKey());
							break;
						}
					}
				}
				allocationNodes.get(node.getName()).setForwardBehaviourList(resultList);
			}
		}
	}
	
	/* Starting from source and destination of each requirement, compute related atomic predicates. Then add to the computed set
	 * also atomic predicates representing input packet classes for each transformer (here we are considering only NAT and firewall)*/
	private HashMap<Integer, Predicate> generateAtomicPredicateNew(){
		List<Predicate> predicates = new ArrayList<>();
		List<Predicate> atomicPredicates = new ArrayList<>();
		List<String> srcList = new ArrayList<>();
		List<String> dstList = new ArrayList<>();
		List<String> srcPList = new ArrayList<>();
		List<String> dstPList = new ArrayList<>();
		List<L4ProtocolTypes> dstProtoList = new ArrayList<>();

		//Generate predicates representing source and predicates representing destination of each requirement
		for(SecurityRequirement sr : securityRequirements.values()) {
			Property property = sr.getOriginalProperty();
			String IPSrc = property.getSrc();
			String IPDst = property.getDst();
			String pSrc = property.getSrcPort() != null &&  !property.getSrcPort().equals("null") ? property.getSrcPort() : "*";
			String pDst = property.getDstPort() != null &&  !property.getDstPort().equals("null") ? property.getDstPort() : "*";
			L4ProtocolTypes proto = property.getLv4Proto() != null ? property.getLv4Proto() : L4ProtocolTypes.ANY;
			srcList.add("*"); dstList.add("*"); srcPList.add("*"); dstPList.add("*"); dstProtoList.add(L4ProtocolTypes.ANY);
			
			//if we have already inserted this source into the list, we can skip it
			if(!srcList.contains(IPSrc) || !srcPList.contains(pSrc)) {
				if(!srcList.contains(IPSrc))
					srcList.add(IPSrc);
				else IPSrc = "*";
				if(!srcPList.contains(pSrc)) 
					srcPList.add(pSrc);
				else pSrc = "*";
				
				Predicate srcPredicate = new Predicate(IPSrc, false, "*", false, pSrc, false, "*", false, L4ProtocolTypes.ANY);
				predicates.add(srcPredicate);
			}
			
			//if we have already inserted this destination into the list, we can skip it
			if(!dstList.contains(IPDst) || !dstPList.contains(pDst) || !dstProtoList.contains(proto)) {
				if(!dstList.contains(IPDst)) dstList.add(IPDst);
				else IPDst = "*";
				if(!dstPList.contains(pDst)) dstPList.add(pDst);
				else pDst = "*";
				if(!dstProtoList.contains(proto)) dstProtoList.add(proto);
				else proto = L4ProtocolTypes.ANY;
				
				Predicate dstPredicate = new Predicate("*", false, IPDst, false, "*", false, pDst, false, proto);
				predicates.add(dstPredicate);
			}
		}

		//Generate predicates representing input packet class for each transformers
		for(Node node: transformersNode.values()) {
			if(node.getFunctionalType() == FunctionalTypes.NAT) {
				//Compute list of shadowed (only those related to requirements sources), considering NAT source addresses list
				List<String> shadowedAddressesList = new ArrayList<>();
				for(String shadowedAddress: node.getConfiguration().getNat().getSource()) {
					for(String ip: srcList) {
						if(shadowedAddress.equals(ip) || aputils.isIncludedIPString(shadowedAddress, ip)) {
							shadowedAddressesList.add(shadowedAddress);
							break;
						}
					}
				}
				//Generate and add shadowing predicates
				for(String shadowed: shadowedAddressesList) {
					if(!srcList.contains(shadowed)) {
						Predicate shpred = new Predicate(shadowed, false, "*", false, "*", false, "*", false, L4ProtocolTypes.ANY);
						predicates.add(shpred);
					}
				}
				//Reconversion predicate
				if(!dstList.contains(node.getName())) {
					Predicate rcpred = new Predicate("*", false, node.getName(), false, "*", false, "*", false, L4ProtocolTypes.ANY);
					predicates.add(rcpred);
				}
				//Add predicate after applying trasformation: this is enough, all the others have already been added
				predicates.add(new Predicate(node.getName(), false, "*", false, "*", false, "*", false, L4ProtocolTypes.ANY));	
			} 
				//If the node is a firewall, compute its allowed rules list
				//Algorithm 1 Yang_Lam 2015
				else if(node.getFunctionalType() == FunctionalTypes.FIREWALL) {
				
				List<Predicate> allowedList = new ArrayList<>();
				List<Predicate> deniedList = new ArrayList<>();
				
				for(Elements rule: node.getConfiguration().getFirewall().getElements()) {
					if(rule.getAction().equals(ActionTypes.DENY)) {
						//deny <--- deny V rule-i
						deniedList.add(new Predicate(rule.getSource(), false, rule.getDestination(), false, 
								rule.getSrcPort(), false, rule.getDstPort(), false, rule.getProtocol()));
					} else {
						//allowed <--- allowed V (rule-i AND !denied)
						Predicate toAdd = new Predicate(rule.getSource(), false, rule.getDestination(), false, 
								rule.getSrcPort(), false, rule.getDstPort(), false, rule.getProtocol());
						List<Predicate> allowedToAdd = aputils.computeAllowedForRule(toAdd, deniedList);
						allowedList.addAll(allowedToAdd);
					}
				}
				//Check default action: if DENY do nothing
				if(node.getConfiguration().getFirewall().getDefaultAction().equals(ActionTypes.ALLOW)) {
					Predicate toAdd = new Predicate("*", false, "*", false, "*", false, "*", false, L4ProtocolTypes.ANY);
					List<Predicate> allowedToAdd = aputils.computeAllowedForRule(toAdd, deniedList);
					allowedList.addAll(allowedToAdd);
				}
				//the algorithm returns the allowed predicates list (if we want also the denied predicates list, we can compute allowed list negation)
				allocationNodes.get(node.getName()).setForwardBehaviourPredicateList(allowedList);
			}
		}

		//DEBUG: interesting predicates for requirements source and destination
		System.out.println("INTERESTING PREDICATES");
		for(Predicate p: predicates)
			p.print();
		//END DEBUG

		//Now we have the list of predicates on which we have to compute the set of atomic predicates, so compute atomic predicates
		atomicPredicates = aputils.computeAtomicPredicates(atomicPredicates, predicates);
		
		//Give to each atomic predicate an identifier
		int index = 0;
		for(Predicate p: atomicPredicates) {
			networkAtomicPredicatesNew.put(index, p);
			index++;
		}
		
		//DEBUG: print atomic predicates
		System.out.println("ATOMIC PREDICATES");
		for(HashMap.Entry<Integer, Predicate> entry: networkAtomicPredicatesNew.entrySet()) {
			System.out.print(entry.getKey() + " ");
			entry.getValue().print();
		}
		//END DEBUG
	
		return networkAtomicPredicatesNew;
	}
	
	/**
	 * This method allocates the functions on allocation nodes that are empty.
	 * At the moment only packet-filtering capability is allocated, in the future the decision will depend on the type of requirement.
	 */
	private void allocateFunctions() {
		for(Flow sr : trafficFlowsMap.values()) {
			List<AllocationNode> nodes = sr.getPath().getNodes();
			int lengthList = nodes.size();
			AllocationNode source = nodes.get(0);
			AllocationNode last = nodes.get(lengthList-1);
			for(int i = 1; i < lengthList-1; i++) {
				allocationManager.chooseFunctions(nodes.get(i), source, last);
			}
		}
		
	}


	/**
	 * This method creates the hard constraints in the z3 model for reachability and isolation requirements.
	 */
	private void formalizeRequirements() {
		
		for(SecurityRequirement sr : securityRequirements.values()) {
			switch (sr.getOriginalProperty().getName()) {
			case ISOLATION_PROPERTY:
				check.createRequirementConstraints(sr, Prop.ISOLATION);
				break;
			case REACHABILITY_PROPERTY:
				check.createRequirementConstraints(sr, Prop.REACHABILITY);
				break;
			default:
				throw new BadGraphError("Error in the property definition", EType.INVALID_PROPERTY_DEFINITION);
			}
				
		}
		
	}


	/**
	 * This method distributes into each Allocation Node the traffic flows and computes the characteristics of each ingress traffic
	 */
	private void distributeTrafficFlows() {
		for(Flow flow : trafficFlowsMap.values()) {
			
			boolean forwardUpdate = false;
			boolean backwardUpdate = false;
			
			List<AllocationNode> nodesList = flow.getPath().getNodes();
			
			for(AllocationNode node : nodesList) {
				node.addFlow(flow);
				if((node.getTypeNF().equals(FunctionalTypes.NAT) && node.getNode().getConfiguration().getNat().getSource().contains(flow.getOriginalTraffic().getIPSrc())) || (node.getTypeNF().equals(FunctionalTypes.LOADBALANCER) && node.getNode().getConfiguration().getLoadbalancer().getPool().contains(flow.getOriginalTraffic().getIPSrc()))){
					forwardUpdate = true;
				}
				else if((node.getTypeNF().equals(FunctionalTypes.NAT) && node.getNode().getConfiguration().getNat().getSource().contains(flow.getOriginalTraffic().getIPDst()) ) 
						|| (node.getTypeNF().equals(FunctionalTypes.LOADBALANCER) && node.getNode().getConfiguration().getLoadbalancer().getPool().contains(flow.getOriginalTraffic().getIPDst()))) {
					backwardUpdate = true;
				}
			}
			
			if(forwardUpdate || backwardUpdate) {
				for(int i = 0; i < nodesList.size(); i++) {
					Traffic t = Traffic.copyTraffic(flow.getOriginalTraffic());
					AllocationNode current = nodesList.get(i);
					flow.addModifiedTraffic(current.getNode().getName(), t);
				}
				
				if(forwardUpdate) {
					Traffic t = Traffic.copyTraffic(flow.getOriginalTraffic());
					int listLength = nodesList.size();
					String currentSrc = t.getIPSrc();
					//loop for modifications of IP addresses from source to destination 
					for(int i = 0; i < listLength; i++) {
						AllocationNode currentNode = nodesList.get(i);
						Traffic crossed = flow.getCrossedTraffic(currentNode.getNode().getName());
						crossed.setIPSrc(currentSrc);
						if((currentNode.getTypeNF().equals(FunctionalTypes.NAT) && currentNode.getNode().getConfiguration().getNat().getSource().contains(crossed.getIPSrc())) ||(currentNode.getTypeNF().equals(FunctionalTypes.LOADBALANCER) && currentNode.getNode().getConfiguration().getLoadbalancer().getPool().contains(crossed.getIPSrc())) ){
							currentSrc = currentNode.getNode().getName();
						}
					}
				}
				
				
				
				if(backwardUpdate) {
					Traffic t = Traffic.copyTraffic(flow.getOriginalTraffic());
					int listLength = nodesList.size();
					String currentDst = t.getIPDst();
					//loop for modifications of IP addresses from destination to source
					
					for(int i = listLength-1; i >= 0; i--) {
						AllocationNode currentNode = nodesList.get(i);
						Traffic crossed = flow.getCrossedTraffic(currentNode.getNode().getName());
						if((currentNode.getTypeNF().equals(FunctionalTypes.NAT) && currentNode.getNode().getConfiguration().getNat().getSource().contains(crossed.getIPDst())) ||(currentNode.getTypeNF().equals(FunctionalTypes.LOADBALANCER) && currentNode.getNode().getConfiguration().getLoadbalancer().getPool().contains(crossed.getIPDst())) ){
							currentDst = currentNode.getNode().getName();
						}
						crossed.setIPDst(currentDst);
					}
				}
			}
		}
	}


	/**
	 * For each requirement, this method identifies all the possible the paths of nodes that must be crossed by the traffic flows that are related to the requirement.
	 * @return the map of all the traffic flows
	 */
	private HashMap<Integer, Flow> generateFlowPaths(){
		
		HashMap<Integer, Flow> flowsMap = new HashMap<>();
		int id = 0;
		
		for(SecurityRequirement sr : securityRequirements.values()) {
			
			Property property = sr.getOriginalProperty();
			
			//first, this method finds if a forwarding path has been defined by the user for the requirement
			//in that case, the research is not performed for that specific requirement
			
			Path definedPath = null;
			if(paths != null) {
				for(Path p : paths) {
					String first = p.getPathNode().get(0).getName();
					String last = p.getPathNode().get(p.getPathNode().size()-1).getName();
					if(first.equals(property.getSrc()) && last.equals(property.getDst())) {
						definedPath = p;
					}	
				}
			}
			
			
			boolean found = false;
			List<List<AllocationNode>> allPaths = new ArrayList<>();
			List<AllocationNode> localPath = new ArrayList<>();
			//if no forwarding path has been defined by the user, the framework searches for ALL the possible existing path.
			//for each path, a corresponding flow is defined. The traffic characterization will be made in a different moment.
			if(definedPath == null) {
				Set<String> visited = new HashSet<>();
				AllocationNode source = allocationNodes.get(property.getSrc());
				AllocationNode destination = allocationNodes.get(property.getDst());
				recursivePathGeneration(allPaths, localPath, source, destination, source, visited, 0);
				found = allPaths.isEmpty()? false : true;
				visited.clear();
			}else {
				//otherwise, the nodes of the path are simply put in the list
				found = true;
				for(PathNode pn : definedPath.getPathNode()) {
					AllocationNode an = allocationNodes.get(pn.getName());
					localPath.add(an);
				}
				allPaths.add(localPath);
			}
			
			if(found) {
				for(List<AllocationNode> singlePath : allPaths) {
					FlowPath fp = new FlowPath(singlePath);
					Flow flow = new Flow(sr, fp, id);
					flowsMap.put(id, flow);
					sr.getFlowsMap().put(id, flow);
					id++;
				}
				
			} else {
				throw new BadGraphError("There is no path between " + property.getSrc() + " and " + property.getDst(),
						EType.INVALID_SERVICE_GRAPH);
			}
		
		}
		
		return flowsMap;	
	}

	/**
	 * This method is recursively called to generate the path of nodes for each requirement.
	 * @param allPaths it is the list of all the paths that have been computed for the requirement
	 * @param currentPath it is the current path that the method is building 
	 * @param source it is the source of the path
	 * @param destination it is the destination of the path
	 * @param current it is the current node in the recursive visit
	 * @param visited it is a list of nodes that have been already visited
	 * @param level it is the recursion level of the visit
	 * @return true if a path has been identified, false otherwise
	 */
	private void recursivePathGeneration(List<List<AllocationNode>> allPaths, List<AllocationNode> currentPath, AllocationNode source,
			AllocationNode destination, AllocationNode current, Set<String> visited, int level) {
		
		currentPath.add(level, current);
		visited.add(current.getNode().getName());
		List<Neighbour> listNeighbours = current.getNode().getNeighbour();
		if(destination.getNode().getName().equals(current.getNode().getName())) {
			//I save the completed path and search for others
			List<AllocationNode> pathToStore = new ArrayList<>();
			for(int i = 0; i < currentPath.size(); i++) {
				if((currentPath.get(i).getNode().getFunctionalType() == FunctionalTypes.NAT 
						|| currentPath.get(i).getNode().getFunctionalType() == FunctionalTypes.FIREWALL)
						&& !transformersNode.containsKey(currentPath.get(i).getNode().getName()))
					transformersNode.put(currentPath.get(i).getNode().getName(), currentPath.get(i).getNode());
				pathToStore.add(i, currentPath.get(i));
			}
			allPaths.add(pathToStore);
			visited.remove(current.getNode().getName());
			currentPath.remove(level);
			return;
		}
		if(level != 0) {
			if(current.getNode().getFunctionalType() == FunctionalTypes.WEBCLIENT || current.getNode().getFunctionalType() == FunctionalTypes.WEBSERVER) {
				//traffic is not forwarded anymore
				visited.remove(current.getNode().getName());
				currentPath.remove(level);
				return;
			}
		}
		
		

		for(Neighbour n : listNeighbours) {
			if(!visited.contains(n.getName())) {
				AllocationNode neighbourNode = allocationNodes.get(n.getName());
				level++;
				recursivePathGeneration(allPaths, currentPath, source, destination, neighbourNode, visited, level);
				level--;
			}
					
		}
		
		visited.remove(current.getNode().getName());
		currentPath.remove(level);
		return;
	}


	
	/**
	 * This method generates the NetContext object for the initialization of z3 model.
	 * @param ctx2 it is the z3 Context object
	 * @param nodes2 is is the list of nodes of the Allocation Graph
	 * @param prop it is the list of properties to be satisfied
	 * @param allocationNodes2 it is the list of allocation nodes
	 * @return the NetContext object
	 */
	private NetContext nctxGenerate(Context ctx2, List<Node> nodes2, List<Property> prop,
			HashMap<String, AllocationNode> allocationNodes2) {
		for (Node n : nodes) {
			if (n.getName().contains("@"))
				throw new BadGraphError("Invalid node name " + n.getName() + ", it can't contain @",
						EType.INVALID_SERVICE_GRAPH);
		}
		String[] nodesname = {};
		nodesname = nodes.stream().map((n) -> n.getName()).collect(Collectors.toCollection(ArrayList<String>::new))
				.toArray(nodesname);
		String[] nodesip = nodesname;
		String[] src_portRange = {};
		src_portRange = properties.stream().map(p -> p.getSrcPort()).filter(p -> p != null)
				.collect(Collectors.toCollection(ArrayList<String>::new)).toArray(src_portRange);
		String[] dst_portRange = {};
		dst_portRange = properties.stream().map(p -> p.getDstPort()).filter(p -> p != null)
				.collect(Collectors.toCollection(ArrayList<String>::new)).toArray(dst_portRange);
		return new NetContext(ctx, allocationNodes, nodesname, nodesip, src_portRange, dst_portRange);
	}

	/**
	 * Checks if the service graph satisfies all the imposed conditions
	 * 
	 * @return
	 */
	public VerificationResult checkNFFGProperty() {
		VerificationResult ret = this.check.propertyCheck();
		ret.time = this.check.getTimeChecker();
		return ret;
	}

	/**
	 * Get Net Context
	 * 
	 * @return the net context
	 */
	public NetContext getNctx() {
		return nctx;
	}


	/**
	 * @return all the allocation nodes
	 */
	public Map<String, AllocationNode> getAllocationNodes() {
		return allocationNodes;
	}

	
	/**
	 * @return all the requirements
	 */
	public Map<Integer, Flow> getTrafficFlowsMap(){
		return trafficFlowsMap;
	}
}
