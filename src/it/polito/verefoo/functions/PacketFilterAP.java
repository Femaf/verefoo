package it.polito.verefoo.functions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.microsoft.z3.ArithExpr;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.DatatypeExpr;
import com.microsoft.z3.Expr;
import com.microsoft.z3.FuncDecl;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.Optimize;

import it.polito.verefoo.allocation.AllocationNodeAP;
import it.polito.verefoo.allocation.AllocationNodeMF;
import it.polito.verefoo.extra.BadGraphError;
import it.polito.verefoo.extra.WildcardManager;
import it.polito.verefoo.graph.FlowPathAP;
import it.polito.verefoo.graph.FlowPathMF;
import it.polito.verefoo.jaxb.ActionTypes;
import it.polito.verefoo.jaxb.EType;
import it.polito.verefoo.jaxb.FunctionalTypes;
import it.polito.verefoo.jaxb.Node;
import it.polito.verefoo.jaxb.PName;
import it.polito.verefoo.solver.NetContextAP;
import it.polito.verefoo.solver.NetContextMF;
import it.polito.verefoo.utils.Tuple;

/** Represents a Packet Filter with the associated Access Control List
 * for Atomic Predicate Algorithm
 */
public class PacketFilterAP extends GenericFunction{

	FuncDecl filtering_function;
	boolean autoConfigured;
	boolean reconfigured;
	BoolExpr behaviour;
	FuncDecl rule_func;
	// blacklisting and defaultAction must match
	boolean blacklisting;
	boolean defaultActionSet;
	DatatypeExpr pf;
	private BoolExpr whitelist;
	

	/**
	 * Public constructor for the Packet Filter AP
	 * @param source It is the Allocation Node on which the packet filter is put
	 * @param ctx It is the Z3 Context in which the model is generated
	 * @param nctx It is the NetContext object to which constraints are sent
	 * @param wildcardManager 
	 */
	public PacketFilterAP(AllocationNodeAP source, Context ctx, NetContextAP nctx, WildcardManager wildcardManager) {
		this.sourceAP = source;
		this.ctx = ctx;
		this.nctxAP = nctx;
		
		pf = source.getZ3Name();
		constraints = new ArrayList<BoolExpr>();
		isEndHost = false;
		reconfigured = false;

   		// true for blacklisting, false for whitelisting
   		// this is the default, but it can be changed
   		blacklisting = false;
   		defaultActionSet = false;
   		whitelist = ctx.mkBoolConst(pf+"_whitelist");
   		
   		// function can be used or not, autoplace follows it
   		used = ctx.mkBoolConst(pf+"_used");
		autoplace = true; 
		
		//function can be autoConfigured is z3 must establish the firewall filtering policy
		autoConfigured = true;
		
	
	}

	/**
	 * This method allows to generate the filtering rules for a manually configured packet_filter
	 */
	public void manualConfiguration(){
		
		if(!autoplace) constraints.add(ctx.mkEq(used, ctx.mkTrue()));
		
		Node n = sourceAP.getNode();
		if(n.getFunctionalType().equals(FunctionalTypes.FIREWALL)){
			for(Integer traffic : sourceAP.getForwardBehaviourList()) {
				constraints.add(ctx.mkEq((BoolExpr)nctxAP.deny.apply(sourceAP.getZ3Name(), ctx.mkInt(traffic)), ctx.mkFalse()));
			}
			for(Integer traffic : sourceAP.getDroppedList()) {
				constraints.add(ctx.mkEq((BoolExpr)nctxAP.deny.apply(sourceAP.getZ3Name(), ctx.mkInt(traffic)), ctx.mkTrue()));
			}
		}
			
		

	}
    
    
	
	/**
	 * This method allows to create SOFT and HARD constraints for an auto_configured packet filter
	 *@param nRules It is the number of MAXIMUM rules the packet_filter should try to configure
	 */
    public void automaticConfiguration() {
    	
    	//allocation
    	if(autoplace) {
    		if(reconfigured) {
      			//Prefer a previously configured FW with respect to a new allocation point
      	    	nctxAP.softConstrMaintainStatePlacement.add(new Tuple<BoolExpr, String>(ctx.mkNot(used), "fw_auto_conf"));
    		} else {
    			// packet filter should not be used if possible
    			nctxAP.softConstrAutoPlace.add(new Tuple<BoolExpr, String>(ctx.mkNot(used), "fw_auto_conf"));
    		}
  		}else {
  			used = ctx.mkTrue();
  			constraints.add(ctx.mkEq(used, ctx.mkTrue()));
  		}
    	
    	//configuration
    	if(defaultActionSet) {
    		if(blacklisting) {
    			constraints.add(ctx.mkEq(whitelist, ctx.mkFalse()));
    		} else {
    			constraints.add(ctx.mkEq(whitelist, ctx.mkTrue()));
    		}
    	}
    	for(Map<Integer, Integer> flowMap : sourceAP.getMapFlowIdAtomicPredicatesInInput().values()) {
    		for(Integer traffic : flowMap.values()) {
    			BoolExpr rule = (BoolExpr) ctx.mkConst(pf + "_rule_" + traffic, ctx.mkBoolSort());
    			constraints.add(
    					ctx.mkEq(
    							(BoolExpr) nctxAP.deny.apply(sourceAP.getZ3Name(), ctx.mkInt(traffic)),
    							ctx.mkAnd(
    									used,
    									ctx.mkOr(
    											ctx.mkAnd(whitelist, ctx.mkNot(rule)),
    											ctx.mkAnd(ctx.mkNot(whitelist), rule)
    											)
    									)
    							)
    					);
    			//For reconfigured FWs, maintaining its configuration should be preferred
    			if(!reconfigured) {
    				nctxAP.softConstrAutoConf.add(new Tuple<BoolExpr, String>(ctx.mkEq(rule, ctx.mkFalse()), "fw_auto_conf"));
    			} else {
    				if(this.sourceAP.getDroppedList().contains(traffic) || this.sourceAP.getForwardBehaviourList().contains(traffic))
    					nctxAP.softConstrMaintainStateConfiguration.add(new Tuple<BoolExpr, String>(ctx.mkEq(rule, ctx.mkFalse()), "fw_auto_conf"));
    				else {
    					nctxAP.softConstrAutoConf.add(new Tuple<BoolExpr, String>(ctx.mkEq(rule, ctx.mkFalse()), "fw_auto_conf"));
    				} 
    			}
    		}
    	}
    
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
	
    public void reConfigure() {
    	//Set automatic placement and configuration
    	autoplace = true;
    	autoConfigured = true;
    	defaultActionSet = false;
    	reconfigured = true;
    }
}
