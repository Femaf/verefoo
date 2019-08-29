package it.polito.verigraph.functions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.DatatypeExpr;
import com.microsoft.z3.Expr;
import com.microsoft.z3.FuncDecl;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.Optimize;
import com.microsoft.z3.Sort;

import it.polito.verefoo.allocation.AllocationNode;
import it.polito.verefoo.extra.BadGraphError;
import it.polito.verefoo.extra.WildcardManager;
import it.polito.verefoo.graph.SecurityRequirement;
import it.polito.verefoo.jaxb.ActionTypes;
import it.polito.verefoo.jaxb.EType;
import it.polito.verefoo.jaxb.FunctionalTypes;
import it.polito.verefoo.jaxb.Node;
import it.polito.verefoo.jaxb.PName;
import it.polito.verefoo.jaxb.Property;
import it.polito.verigraph.extra.PacketFilterRule;
import it.polito.verigraph.extra.Quadruple;
import it.polito.verigraph.extra.Tuple;
import it.polito.verigraph.solver.NetContext;
import it.polito.verigraph.solver.Checker.Prop;

/** Represents a Packet Filter with the associated Access Control List
 *
 */
public class PacketFilter extends GenericFunction{

	FuncDecl filtering_function;
	ArrayList<PacketFilterRule> rules; 
	boolean autoConfigured;
	BoolExpr behaviour;
	FuncDecl rule_func;
	// blacklisting and defaultAction must match
	private BoolExpr blacklisting_z3;
	boolean blacklisting;
	boolean defaultActionSet;
	private WildcardManager wildcardManager;
	
	DatatypeExpr pf;
	Expr defaultAction;
	int nRules;
	Map<Integer, BoolExpr> notConfiguredConditions = new HashMap<>();
	Map<Integer, Expr> srcConditions = new HashMap<>();
	Map<Integer, Expr> dstConditions = new HashMap<>();
	Map<Integer, Expr> portSConditions = new HashMap<>();
	Map<Integer, Expr> portDConditions = new HashMap<>();
	Map<Integer, Expr> l4Conditions = new HashMap<>();
	

	/**
	 * Public constructor for the Packet Filter
	 * @param source It is the Allocation Node on which the packet filter is put
	 * @param ctx It is the Z3 Context in which the model is generated
	 * @param nctx It is the NetContext object to which constraints are sent
	 * @param wildcardManager 
	 */
	public PacketFilter(AllocationNode source, Context ctx, NetContext nctx, WildcardManager wildcardManager) {
		this.source = source;
		this.ctx = ctx;
		this.nctx = nctx;
		this.wildcardManager = wildcardManager;
		
		pf = source.getZ3Name();
		constraints = new ArrayList<BoolExpr>();
   		rules = new ArrayList<>();
		isEndHost = false;

   		// true for blacklisting, false for whitelisting
   		// this is the default, but it can be changed
   		blacklisting_z3 = ctx.mkFalse();
   		blacklisting = false;
   		defaultActionSet = false;
   		
   		// function can be used or not, autoplace follows it
   		used = ctx.mkBoolConst(pf+"_used");
		autoplace = true; 
		
		//function can be autoConfigured is z3 must establish the firewall filtering policy
		autoConfigured = true;
		
		// main z3 fitlering function
		filtering_function = ctx.mkFuncDecl(pf + "_filt_func", new Sort[] { nctx.packetType}, ctx.mkBoolSort());
	}

	/**
	 * This method allows to generate the filtering rules for a manually configured packet_filter
	 */
	public void manualConfiguration(){
		Node n = source.getNode();
		if(n.getFunctionalType().equals(FunctionalTypes.FIREWALL)){
				n.getConfiguration().getFirewall().getElements().forEach((e)->{
						ArrayList<PacketFilterRule> rules_manual = new ArrayList<>();
						String src_port = e.getSrcPort()!=null? e.getSrcPort():"*";
						String dst_port = e.getDstPort()!=null? e.getDstPort():"*";
						boolean directional = e.isDirectional()!=null? e.isDirectional():true;
						int protocol = e.getProtocol()!=null? e.getProtocol().ordinal():0;
						boolean action;
						if(e.getAction() != null){
							action = (e.getAction().equals(ActionTypes.ALLOW)) ? true : false;
						}else{
							// if not specified the action of the rule is the opposite of the 
							// default behaviour otherwise the rule would not be necessary
							if(n.getConfiguration().getFirewall().getDefaultAction() == null){
								action = false;
							}else
							action = n.getConfiguration().getFirewall().getDefaultAction().equals(ActionTypes.ALLOW) ? false : true;
						}
						try{

							if(nctx.addressMap.get(e.getSource())!=null&&nctx.addressMap.get(e.getDestination())!=null){
									PacketFilterRule rule=new PacketFilterRule(nctx, ctx, action, nctx.addressMap.get(e.getSource()),nctx.addressMap.get(e.getDestination()),
																				src_port,dst_port, protocol, directional);
									rules_manual.add(rule);
							}else{ // #TODO check if it enters here
									PacketFilterRule rule=new PacketFilterRule(nctx, ctx, action, e.getSource(),e.getDestination(),
																				src_port,dst_port, protocol, directional);
									rules_manual.add(rule);
							}
						}catch(NumberFormatException ex){
							throw new BadGraphError(n.getName()+" has invalid configuration: "+ex.getMessage(), EType.INVALID_NODE_CONFIGURATION);
						}
						
						if(!autoConfigured){	// if not an auto_configuration packet_filter
							this.rules.addAll(rules_manual);
						}	
				});
		}
		
		
		if(!autoplace) constraints.add(ctx.mkEq(used, ctx.mkTrue()));
		
		nRules = rules.size();
		for(PacketFilterRule rule : rules) {
			int i = 0;
			Expr src = ctx.mkConst(pf + "_manual_src_"+i, nctx.addressType);
  			Expr dst = ctx.mkConst(pf + "_manual_dst_"+i, nctx.addressType);
  			Expr proto = ctx.mkConst(pf + "_manual_proto_"+i, ctx.mkIntSort());
  			Expr srcp = ctx.mkConst(pf + "_manual_srcp_"+i, nctx.portType);
  			Expr dstp = ctx.mkConst(pf + "_manual_dstp_"+i, nctx.portType);
 			
 			
 			constraints.add(ctx.mkEq(src, rule.getSource()));
 			constraints.add(ctx.mkEq(dst, rule.getDestination()));
 			constraints.add(ctx.mkEq((IntExpr)nctx.portFunctionsMap.get("start").apply(srcp),(IntExpr)rule.getStart_src_port()));
 			constraints.add(ctx.mkEq((IntExpr)nctx.portFunctionsMap.get("end").apply(srcp),(IntExpr)rule.getEnd_src_port()));
 			constraints.add(ctx.mkEq((IntExpr)nctx.portFunctionsMap.get("start").apply(dstp),(IntExpr)rule.getStart_dst_port()));
 			constraints.add(ctx.mkEq((IntExpr)nctx.portFunctionsMap.get("end").apply(dstp),(IntExpr)rule.getEnd_dst_port()));
 			constraints.add(ctx.mkEq(proto,rule.getProtocol()));
 			
 			srcConditions.put(i,  src);
 			dstConditions.put(i, dst);
 			portSConditions.put(i,  srcp);
 			portDConditions.put(i, dstp);
 			l4Conditions.put(i, proto) ;
 			i++;
		}
		
		
		/**
	     * This section allow the creation of Z3 variables for defaultAction and rules.
	     * behaviour variables is the combination of the auto-configured rules.
	    */
  		defaultAction = ctx.mkConst(pf + "_manual_default_action", ctx.mkBoolSort());
  		Expr ruleAction = ctx.mkConst(pf + "_manual_action", ctx.mkBoolSort());
  		
  		if(this.blacklisting_z3.equals(ctx.mkTrue())){
  			constraints.add(ctx.mkEq(defaultAction, ctx.mkTrue()));
  			constraints.add(ctx.mkEq(ruleAction, ctx.mkFalse()));
  		}else{
  			constraints.add(ctx.mkEq(defaultAction, ctx.mkFalse()));
  			constraints.add(ctx.mkEq(ruleAction, ctx.mkTrue()));
  		}
  	
	
  		/**
  		 * This sections generates all the constraints to satisfy reachability and isolation requirements.
  		 */
  		
  		for(SecurityRequirement sr : source.getRequirements().values()) {
  			generateManualSatifiabilityConstraint(sr);
  		}
		
	}
    
    
	
	/**
	 * This method allows to create SOFT and HARD constraints for an auto_configured packet filter
	 *@param nRules It is the number of MAXIMUM rules the packet_filter should try to configure
	 */
    public void automaticConfiguration() {
    	
        List<BoolExpr> implications1 = new ArrayList<BoolExpr>();
  		List<BoolExpr> implications2 = new ArrayList<BoolExpr>();
  		
  		if(!defaultActionSet) {
  			long nReachability = source.getRequirements().values().stream().filter(r -> r.getProperty().getName().equals(PName.REACHABILITY_PROPERTY)).count();
  			long nIsolation = source.getRequirements().values().stream().filter(r -> r.getProperty().getName().equals(PName.ISOLATION_PROPERTY)).count();
  			if(nIsolation >= nReachability) setDefaultAction(false);
  			else setDefaultAction(true);
  			//leave uncommented if you want "whitelisting" approach
  	  		setDefaultAction(false);
  		}
  		
  		
  		
  		nRules = minizimePlaceholderRules();
  		
  		if(autoplace) {
  			// packet filter should not be used if possible
  			nctx.softConstrAutoPlace.add(new Tuple<BoolExpr, String>(ctx.mkNot(used), "fw_auto_conf"));
  		}else {
  			used = ctx.mkTrue();
  			constraints.add(ctx.mkEq(used, ctx.mkTrue()));
  		}
  		
  		for(int i = 0; i < nRules; i++){
  			Expr src = ctx.mkConst(pf + "_auto_src_"+i, nctx.addressType);
  			Expr dst = ctx.mkConst(pf + "_auto_dst_"+i, nctx.addressType);
  			Expr proto = ctx.mkConst(pf + "_auto_proto_"+i, ctx.mkIntSort());
  			Expr srcp = ctx.mkConst(pf + "_auto_srcp_"+i, nctx.portType);
  			Expr dstp = ctx.mkConst(pf + "_auto_dstp_"+i, nctx.portType);
  			IntExpr srcAuto1 = ctx.mkIntConst(pf + "_auto_src_ip_1_"+i);
  			IntExpr srcAuto2 = ctx.mkIntConst(pf + "_auto_src_ip_2_"+i);
  			IntExpr srcAuto3 = ctx.mkIntConst(pf + "_auto_src_ip_3_"+i);
  			IntExpr srcAuto4 = ctx.mkIntConst(pf + "_auto_src_ip_4_"+i);
  			IntExpr dstAuto1 = ctx.mkIntConst(pf + "_auto_dst_ip_1_"+i);
  			IntExpr dstAuto2 = ctx.mkIntConst(pf + "_auto_dst_ip_2_"+i);
  			IntExpr dstAuto3 = ctx.mkIntConst(pf + "_auto_dst_ip_3_"+i);
  			IntExpr dstAuto4 = ctx.mkIntConst(pf + "_auto_dst_ip_4_"+i);
  			
  			/**
  	    	 * This section allows the creation of the following hard constraints:
  	    	 * 1) if in a rule the source isn't NULL, then also the destination shouldn't be NULL
  	    	 * 2) if in a rule the source is NULL, then also the destination should be NULL
  	    	 * This is done only with autoplacement feature.
  	    	 * Observation: maybe we can introduce the REVERSE rules!
  	    	 */
  			if(autoplace) {
  				implications1.add(ctx.mkAnd(ctx.mkNot(ctx.mkEq( src, this.nctx.addressMap.get("null"))),
  						ctx.mkNot(ctx.mkEq( dst, this.nctx.addressMap.get("null")))));
  	  			implications2.add(ctx.mkAnd(ctx.mkEq( src, this.nctx.addressMap.get("null")),
  						ctx.mkEq( dst, this.nctx.addressMap.get("null"))));
  			}
  			/**
  	    	 * This section allows the creation of the following hard constraints:
  	    	 * Each component of source or destination ipAddress of the rule should be equal to the defined Z3 variable in this method.
  	    	 * It's used for a mapping useful only for Z3 model, not first-order logic behaviour of packet_filter.
  	    	 */
  			constraints.add(ctx.mkAnd(
						ctx.mkEq(nctx.ipFunctionsMap.get("ipAddr_1").apply(src), srcAuto1),
						ctx.mkEq(nctx.ipFunctionsMap.get("ipAddr_2").apply(src), srcAuto2),
						ctx.mkEq(nctx.ipFunctionsMap.get("ipAddr_3").apply(src), srcAuto3),
						ctx.mkEq(nctx.ipFunctionsMap.get("ipAddr_4").apply(src), srcAuto4),
						ctx.mkEq(nctx.ipFunctionsMap.get("ipAddr_1").apply(dst), dstAuto1),
						ctx.mkEq(nctx.ipFunctionsMap.get("ipAddr_2").apply(dst), dstAuto2),
						ctx.mkEq(nctx.ipFunctionsMap.get("ipAddr_3").apply(dst), dstAuto3),
						ctx.mkEq(nctx.ipFunctionsMap.get("ipAddr_4").apply(dst), dstAuto4)
						)
				); 
  			
  			/**
  	    	 * This section allows the creation of the following soft constraints:
  	    	 * If possible, the source/destination IP address components should be equal to the wildcard.
  	    	 * This way the rule is able to manage more than one property at the same time.
  	    	 */
  			nctx.softConstrWildcard.add(new Tuple<BoolExpr, String>(ctx.mkEq((IntExpr)nctx.ipFunctionsMap.get("ipAddr_1").apply(nctx.addressMap.get("wildcard")),srcAuto1), "fw_auto_conf"));
  			nctx.softConstrWildcard.add(new Tuple<BoolExpr, String>(ctx.mkEq((IntExpr)nctx.ipFunctionsMap.get("ipAddr_2").apply(nctx.addressMap.get("wildcard")),srcAuto2), "fw_auto_conf"));
  			nctx.softConstrWildcard.add(new Tuple<BoolExpr, String>(ctx.mkEq((IntExpr)nctx.ipFunctionsMap.get("ipAddr_3").apply(nctx.addressMap.get("wildcard")),srcAuto3), "fw_auto_conf"));
  			nctx.softConstrWildcard.add(new Tuple<BoolExpr, String>(ctx.mkEq((IntExpr)nctx.ipFunctionsMap.get("ipAddr_4").apply(nctx.addressMap.get("wildcard")),srcAuto4), "fw_auto_conf"));
  			nctx.softConstrWildcard.add(new Tuple<BoolExpr, String>(ctx.mkEq((IntExpr)nctx.ipFunctionsMap.get("ipAddr_1").apply(nctx.addressMap.get("wildcard")),dstAuto1), "fw_auto_conf"));
  			nctx.softConstrWildcard.add(new Tuple<BoolExpr, String>(ctx.mkEq((IntExpr)nctx.ipFunctionsMap.get("ipAddr_2").apply(nctx.addressMap.get("wildcard")),dstAuto2), "fw_auto_conf"));
  			nctx.softConstrWildcard.add(new Tuple<BoolExpr, String>(ctx.mkEq((IntExpr)nctx.ipFunctionsMap.get("ipAddr_3").apply(nctx.addressMap.get("wildcard")),dstAuto3), "fw_auto_conf"));
  			nctx.softConstrWildcard.add(new Tuple<BoolExpr, String>(ctx.mkEq((IntExpr)nctx.ipFunctionsMap.get("ipAddr_4").apply(nctx.addressMap.get("wildcard")),dstAuto4), "fw_auto_conf"));
  			
   			/**
  	    	 * This section allows the creation of the following soft constraints:
  	    	 * If possible, the source/destination IP address components should be NULL at the same time.
  	    	 * This way the rule isn't generated.
  	    	 */
  			
  			BoolExpr notConfiguredRule = ctx.mkAnd(
						ctx.mkEq(nctx.ipFunctionsMap.get("ipAddr_1").apply(nctx.addressMap.get("null")), srcAuto1),
						ctx.mkEq(nctx.ipFunctionsMap.get("ipAddr_2").apply(nctx.addressMap.get("null")), srcAuto2),
						ctx.mkEq(nctx.ipFunctionsMap.get("ipAddr_3").apply(nctx.addressMap.get("null")), srcAuto3),
						ctx.mkEq(nctx.ipFunctionsMap.get("ipAddr_4").apply(nctx.addressMap.get("null")), srcAuto4),
						ctx.mkEq(nctx.ipFunctionsMap.get("ipAddr_1").apply(nctx.addressMap.get("null")), dstAuto1),
						ctx.mkEq(nctx.ipFunctionsMap.get("ipAddr_2").apply(nctx.addressMap.get("null")), dstAuto2),
						ctx.mkEq(nctx.ipFunctionsMap.get("ipAddr_3").apply(nctx.addressMap.get("null")), dstAuto3),
						ctx.mkEq(nctx.ipFunctionsMap.get("ipAddr_4").apply(nctx.addressMap.get("null")), dstAuto4)
						);
  			nctx.softConstrAutoConf.add(new Tuple<BoolExpr, String>(notConfiguredRule, "fw_auto_conf"));
  			notConfiguredConditions.put(i, notConfiguredRule);
  			
  			/**
  	    	 * This section allows the creation of the following soft constraints:
  	    	 * 1) use wildcards for protocol field if possible
  	    	 * 2) use wildcards for source port if possible
  	    	 * 3) use wildcards for destination port if possible
  	    	 */
 			nctx.softConstrProtoWildcard.add(new Tuple<BoolExpr, String>(ctx.mkEq( proto, ctx.mkInt(0)),"fw_auto_conf"));
 			nctx.softConstrPorts.add(new Tuple<BoolExpr, String>(ctx.mkEq(srcp, nctx.portMap.get("null")),"fw_auto_conf"));
 			nctx.softConstrPorts.add(new Tuple<BoolExpr, String>(ctx.mkEq(dstp, nctx.portMap.get("null")),"fw_auto_conf"));
  			
 			/*
 			 * For each rule which must be potentially configured by z3,
 			 * all the fields are matched with the packet p_0,
 			 * through formulas involving wildcards and defined in NetContext class.
 			 */
 			srcConditions.put(i,  src);
 			dstConditions.put(i, dst);
 			portSConditions.put(i,  srcp);
 			portDConditions.put(i, dstp);
 			l4Conditions.put(i, proto) ;
 			
  		}
  		
  		
  		/**
	     * This section allow the creation of Z3 variables for defaultAction and rules.
	     * behaviour variables is the combination of the auto-configured rules.
	    */
  		defaultAction = ctx.mkConst(pf + "_auto_default_action", ctx.mkBoolSort());
  		Expr ruleAction = ctx.mkConst(pf + "_auto_action", ctx.mkBoolSort());
  		
  		if(this.blacklisting_z3.equals(ctx.mkTrue())){
  			constraints.add(ctx.mkEq(defaultAction, ctx.mkTrue()));
  			constraints.add(ctx.mkEq(ruleAction, ctx.mkFalse()));
  		}else{
  			constraints.add(ctx.mkEq(defaultAction, ctx.mkFalse()));
  			constraints.add(ctx.mkEq(ruleAction, ctx.mkTrue()));
  		}
  		
  		/**
	     * This section allows the insertion of previously created hard constraints in a list.
	     */
  		if(autoplace) {
  			BoolExpr[] implications_tmp = new BoolExpr[implications1.size()];
  			constraints.add(ctx.mkImplies(ctx.mkOr(implications1.toArray(implications_tmp)),used));

  	 		implications_tmp = new BoolExpr[implications2.size()];
  			constraints.add(ctx.mkImplies(ctx.mkNot(used), ctx.mkAnd(implications2.toArray(implications_tmp))));
  		}
  		
  		
  		/**
  		 * This sections generates all the constraints to satisfy reachability and isolation requirements.
  		 */
  		
  		for(SecurityRequirement sr : source.getRequirements().values()) {
  			generateSatifiabilityConstraint(sr);
  		}
  	

    }

	/**
	 * This method exploits pruning strategies to minimize the number of placeholder rules in a firewall to be automatically configured
	 * @return the maximum number of placeholder rules that are needed after the pruning
	 */
	private int minizimePlaceholderRules() {
		
		List<SecurityRequirement> allProperties = source.getRequirements().values().stream().collect(Collectors.toList());
		List<SecurityRequirement> interestedProperties = allProperties.stream().filter(p -> {
			boolean pruning = (p.getProperty().getName().value().equals("IsolationProperty") && blacklisting)
					|| (p.getProperty().getName().value().equals("ReachabilityProperty") && !blacklisting);
			// if the pruning must be disabled
			// return true;
			return pruning;
		}).collect(Collectors.toList());
		
		int minimizedRules = interestedProperties.size();
		
		if (!allProperties.isEmpty()) {
			List<String> destinations = allProperties.stream().map(p -> p.getProperty().getDst()).distinct()
					.collect(Collectors.toList());
			for (String destination : destinations) {
				Set<String> interestedSRC = interestedProperties.stream()
						.filter(p -> p.getProperty().getDst().equals(destination)).map(p -> p.getProperty().getSrc()).distinct()
						.collect(Collectors.toSet());
				Set<String> notInterestedSRC = allProperties.stream().filter(p -> p.getProperty().getDst().equals(destination))
						.map(p -> p.getProperty().getSrc()).distinct().collect(Collectors.toSet());
				notInterestedSRC.removeAll(interestedSRC);
				if (!interestedSRC.isEmpty()
						&& wildcardManager.areAggregable(interestedSRC, notInterestedSRC)) {
					minimizedRules -= interestedSRC.size();
					minimizedRules += 1;
				}
			}
		}
	
		return minimizedRules;
	}



	/**
	 * This method generates the hard constraint to satisfy a requirement for an automatically configured firewall.
	 * @param sr it is the security requirement
	 */
	private void generateSatifiabilityConstraint(SecurityRequirement sr) {
		
		BoolExpr firstA = used;
		BoolExpr secondA = sr.getProperty().getName().equals(PName.ISOLATION_PROPERTY) ? 
				ctx.mkEq((BoolExpr)nctx.deny.apply(source.getZ3Name(), ctx.mkInt(sr.getIdRequirement())), ctx.mkTrue()):
				ctx.mkEq((BoolExpr)nctx.deny.apply(source.getZ3Name(), ctx.mkInt(sr.getIdRequirement())), ctx.mkFalse());
		BoolExpr firstC = blacklisting? ctx.mkEq(defaultAction, ctx.mkTrue()) : ctx.mkEq(defaultAction, ctx.mkFalse());
		
		List<BoolExpr> listSecondC = new ArrayList<>();
		for(int i = 0; i < nRules; i++) {
			BoolExpr component1 = ctx.mkNot(notConfiguredConditions.get(i));
			BoolExpr component2 = ctx.mkAnd(
						nctx.equalIpAddressToPFRule(sr.getProperty().getSrc(), srcConditions.get(i)),
						nctx.equalIpAddressToPFRule(sr.getProperty().getDst(), dstConditions.get(i)),
	 					nctx.equalPortRangeToPFRule(sr.getProperty().getSrcPort(), portSConditions.get(i)),
	 					nctx.equalPortRangeToPFRule(sr.getProperty().getDstPort(), portDConditions.get(i)),
	 					nctx.equalLv4ProtoToFwLv4Proto(sr.getProperty().getLv4Proto().ordinal(), l4Conditions.get(i))
	 					);
			BoolExpr secondCPart = ((blacklisting && sr.getProperty().getName().equals(PName.ISOLATION_PROPERTY)) || (!blacklisting && sr.getProperty().getName().equals(PName.REACHABILITY_PROPERTY))) ?
					ctx.mkAnd(component1, component2) : ctx.mkNot(ctx.mkAnd(component1, component2));
			listSecondC.add(secondCPart);
		}
		
		BoolExpr[] tmp = new BoolExpr[listSecondC.size()];
		BoolExpr secondC = ((blacklisting && sr.getProperty().getName().equals(PName.ISOLATION_PROPERTY)) || (!blacklisting && sr.getProperty().getName().equals(PName.REACHABILITY_PROPERTY))) ?
				ctx.mkOr(listSecondC.toArray(tmp)) : ctx.mkAnd(listSecondC.toArray(tmp));
		
		constraints.add(ctx.mkImplies(ctx.mkAnd(firstA, secondA), ctx.mkAnd(firstC, secondC)));
		constraints.add(ctx.mkImplies(ctx.mkAnd(firstC, secondC, firstA), secondA));
	}
	
	
	/**
	 * This method generates the hard constraint to satisfy a requirement for an manually configured firewall.
	 * @param sr it is the security requirement
	 */
	private void generateManualSatifiabilityConstraint(SecurityRequirement sr) {
		
		BoolExpr firstA = used;
		BoolExpr secondA = sr.getProperty().getName().equals(PName.ISOLATION_PROPERTY) ? 
				ctx.mkEq((BoolExpr)nctx.deny.apply(source.getZ3Name(), ctx.mkInt(sr.getIdRequirement())), ctx.mkTrue()):
				ctx.mkEq((BoolExpr)nctx.deny.apply(source.getZ3Name(), ctx.mkInt(sr.getIdRequirement())), ctx.mkFalse());
		BoolExpr firstC = blacklisting? ctx.mkEq(defaultAction, ctx.mkTrue()) : ctx.mkEq(defaultAction, ctx.mkFalse());
		
		List<BoolExpr> listSecondC = new ArrayList<>();
		for(int i = 0; i < nRules; i++) {
			BoolExpr component2 = ctx.mkAnd(
						nctx.equalIpAddressToPFRule(sr.getProperty().getSrc(), srcConditions.get(i)),
						nctx.equalIpAddressToPFRule(sr.getProperty().getDst(), dstConditions.get(i)),
	 					nctx.equalPortRangeToPFRule(sr.getProperty().getSrcPort(), portSConditions.get(i)),
	 					nctx.equalPortRangeToPFRule(sr.getProperty().getDstPort(), portDConditions.get(i)),
	 					nctx.equalLv4ProtoToFwLv4Proto(sr.getProperty().getLv4Proto().ordinal(), l4Conditions.get(i))
	 					);
			BoolExpr secondCPart = ((blacklisting && sr.getProperty().getName().equals(PName.ISOLATION_PROPERTY)) || (!blacklisting && sr.getProperty().getName().equals(PName.REACHABILITY_PROPERTY))) ?
					component2 : ctx.mkNot(component2);
			listSecondC.add(secondCPart);
		}
		
		BoolExpr[] tmp = new BoolExpr[listSecondC.size()];
		BoolExpr secondC = ((blacklisting && sr.getProperty().getName().equals(PName.ISOLATION_PROPERTY)) || (!blacklisting && sr.getProperty().getName().equals(PName.REACHABILITY_PROPERTY))) ?
				ctx.mkOr(listSecondC.toArray(tmp)) : ctx.mkAnd(listSecondC.toArray(tmp));
		
		constraints.add(ctx.mkImplies(ctx.mkAnd(firstA, secondA), ctx.mkAnd(firstC, secondC)));
		constraints.add(ctx.mkImplies(ctx.mkAnd(firstC, secondC, firstA), secondA));
	}

	/**
	 * This method allows to know if autoconfiguration feature is used
	 *@return the value of autoconfigured boolean variable
	 */
	public boolean isAutoconfigured() {
		return autoConfigured;
	}
    
   	/**
	 * This method allows to know if autoplacement feature is used
	 *@return the value of autoplace boolean variable
	 */
	public boolean isAutoplace() {
		return autoplace;
	}


	/**
	 * This method allows to know if blacklisting is used
	 *@return the value of blacklisting boolean variable
	 */
 	public boolean isBlacklisting() {
		return blacklisting;
	}

 	/**
	 * This method allows to set autoconfigured variable
	 *@param autoconfigured Value to set
	 */
	public void setAutoconfigured(boolean autoconfigured) {
		this.autoConfigured = autoconfigured;
	}
	
	/**
	 * This method allows to set autoplace variable
	 *@param autoplace Value to set
	 */
	public void setAutoplace(boolean autoplace) {
		this.autoplace = autoplace;
	}

	/**
	 * This method allows to set blacklisting variable
	 *@param blacklisting Value to set
	 */
	public void setBlacklisting(boolean blacklisting) {
		this.blacklisting = blacklisting;
		defaultActionSet = true;
	}
	
	/**
	 * This method allows to change the default behaviour of the packet_filter: blacklisting (true) or whitelisting (false)
	 * @param action The boolean is true for blacklisting, false for whitelisting.
	 */
	public void setDefaultAction(boolean action){
		blacklisting_z3 = action? ctx.mkTrue(): ctx.mkFalse();
		this.setBlacklisting(action);
	}

	
	/**
	 * This method allows to wrap the method which adds the constraints inside Z3 solver
	 * @param solver Istance of Z3 solver
	 */
	@Override
	public void addContraints(Optimize solver) {
		BoolExpr[] constr = new BoolExpr[constraints.size()];
	    solver.Add(constraints.toArray(constr));
	    //additionalConstraints(solver);
	}
}
