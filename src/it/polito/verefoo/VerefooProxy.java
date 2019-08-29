package it.polito.verefoo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


import com.microsoft.z3.Context;


import it.polito.verefoo.allocation.AllocationManager;
import it.polito.verefoo.allocation.AllocationNode;
import it.polito.verefoo.extra.BadGraphError;
import it.polito.verefoo.extra.WildcardManager;
import it.polito.verefoo.graph.RequirementPath;
import it.polito.verefoo.graph.SecurityRequirement;
import it.polito.verefoo.jaxb.*;
import it.polito.verefoo.jaxb.NodeConstraints.NodeMetrics;
import it.polito.verigraph.extra.VerificationResult;
import it.polito.verigraph.solver.*;
import it.polito.verigraph.solver.Checker.Prop;

/**
 * 
 * This is the main class that will interface with the Verefoo classes
 *
 */
public class VerefooProxy {
	private Context ctx;
	private NetContext nctx;
	private List<Property> properties;
	private WildcardManager wildcardManager;
	private HashMap<String, AllocationNode> allocationNodes;
	private HashMap<Integer, SecurityRequirement> requirementsMap;
	public Checker check;
	private List<Node> nodes;
	int clientServerCombinations = 0;
	private List<NodeMetrics> nodeMetrics;
	private AllocationManager allocationManager;
	
	private Logger logger = LogManager.getLogger("mylog");
	
	
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
		allocationNodes = new HashMap<>();
		HashMap<String, String> cfg = new HashMap<String, String>();
		cfg.put("model", "true");
		ctx = new Context(cfg);
		properties = prop;
		nodes = graph.getNode();
		nodes.forEach(n -> allocationNodes.put(n.getName(), new AllocationNode(n)));
		this.nodeMetrics = constraints.getNodeConstraints().getNodeMetrics();
		requirementsMap = generateRequirementPaths();
		wildcardManager = new WildcardManager(allocationNodes);

		nctx = nctxGenerate(ctx, nodes, prop, allocationNodes);
		nctx.setWildcardManager(wildcardManager);

		
		allocationManager = new AllocationManager(ctx, nctx, allocationNodes, nodeMetrics, prop, wildcardManager);

		allocationManager.instantiateFunctions();

		allocateFunctions();
		distributeRequirements();
		allocationManager.configureFunctions();
		check = new Checker(ctx, nctx, allocationNodes);
		formalizeRequirements();
		
	}
	
	
	/**
	 * This method allocates the functions on allocation nodes that are empty.
	 * At the moment only packet-filtering capability is allocated, in the future the decision will depend on the type of requirement.
	 */
	private void allocateFunctions() {
		for(SecurityRequirement sr : requirementsMap.values()) {
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
	 * This method creates the constraint in the z3 model for reachability and isolation requirements.
	 */
	private void formalizeRequirements() {
		
		for(SecurityRequirement sr : requirementsMap.values()) {
			switch (sr.getProperty().getName()) {
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
	 * This method distributes into each Allocation Node the requirements whose traffic flow pass through it.
	 */
	private void distributeRequirements() {
		
		for(SecurityRequirement sr : requirementsMap.values()) {
			for(AllocationNode node : sr.getPath().getNodes()) {
				node.addRequirement(sr);
			}
		}
		
	}


	/**
	 * For each requirement, this method identifies the path of nodes that must be crossed by the traffic flow.
	 * @return the map of all the security requirements
	 */
	private HashMap<Integer, SecurityRequirement> generateRequirementPaths(){
		
		HashMap<Integer, SecurityRequirement> SRMap = new HashMap<>();
		int id = 0;
		for(Property property : properties) {
			List<AllocationNode> nodes = new ArrayList<>();
			Set<String> visited = new HashSet<>();
			AllocationNode source = allocationNodes.get(property.getSrc());
			AllocationNode destination = allocationNodes.get(property.getDst());
			boolean found = recursivePathGeneration(nodes, source, destination, source, visited, 0);
			visited.clear();
			if(found) {
				RequirementPath rp = new RequirementPath(nodes);
				SecurityRequirement sr = new SecurityRequirement(property, rp, id);
				SRMap.put(id, sr);
				id++;
			} else {
				System.out.println("ERROR");
			}
		}
		
		return SRMap;
		
	}

	/**
	 * This method is recursively called to generate the path of nodes for each requirement.
	 * @param nodes it is the list of nodes that compose the correct path
	 * @param source it is the source of the path
	 * @param destination it is the destination of the path
	 * @param current it is the current node in the recursive visit
	 * @param visited it is a list of nodes that have been already visited
	 * @param level it is the recursion level of the visit
	 * @return true if a path has been identified, false otherwise
	 */
	private boolean recursivePathGeneration(List<AllocationNode> nodes, AllocationNode source,
			AllocationNode destination, AllocationNode current, Set<String> visited, int level) {
		
		nodes.add(level, current);
		visited.add(current.getNode().getName());
		List<Neighbour> listNeighbours = current.getNode().getNeighbour();
		if(destination.getNode().getName().equals(current.getNode().getName())) return true;
		
		
		for(Neighbour n : listNeighbours) {
			if(!visited.contains(n.getName())) {
				AllocationNode neighbourNode = allocationNodes.get(n.getName());
				level++;
				boolean result = recursivePathGeneration(nodes, source, destination, neighbourNode, visited, level);
				if(result) return true;
				level--;
			}
			
			
		}
		
		visited.remove(current.getNode().getName());
		nodes.remove(nodes.size()-1);
		return false;
	}


	
	/**
	 * This method generared the NetContext object for the inizialitation of z3 model.
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
	 * Returns true if the node is a client
	 */
	public boolean nodeIsClient(Node n) {
		return n.getFunctionalType().equals(FunctionalTypes.MAILCLIENT)
				|| n.getFunctionalType().equals(FunctionalTypes.WEBCLIENT)
				|| n.getFunctionalType().equals(FunctionalTypes.ENDHOST);

	}

	/**
	 * Returns true if the node is a server
	 */
	public boolean nodeIsServer(Node n) {
		return n.getFunctionalType().equals(FunctionalTypes.MAILSERVER)
				|| n.getFunctionalType().equals(FunctionalTypes.WEBSERVER);

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
	public Map<Integer, SecurityRequirement> getRequirementsMap(){
		return requirementsMap;
	}
}
