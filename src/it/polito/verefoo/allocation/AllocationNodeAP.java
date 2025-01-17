package it.polito.verefoo.allocation;

import java.util.*;

import com.microsoft.z3.DatatypeExpr;
import com.microsoft.z3.Optimize;

import it.polito.verefoo.functions.GenericFunction;
import it.polito.verefoo.graph.FlowPathAP;
import it.polito.verefoo.graph.FlowPathMF;
import it.polito.verefoo.graph.Predicate;
import it.polito.verefoo.jaxb.*;


/*
 * This class is an extension of the JAXB-Annotated Node class, this class is specific for Atomic Predicates algorithm.
 * It has additional features, like maps used to build forwarding rules and information about deployed Network Functions.
 * This class is for atomic predicate algorithm
 */

public class AllocationNodeAP extends AllocationNode {

	
	/* Atomic predicates */
	HashMap<Integer, List<Integer>> transformationsMap = new HashMap<>();
	List<Predicate> forwardBehaviourPredicateList = new ArrayList<>();
	List<Integer> forwardBehaviourList = new ArrayList<>();
	List<Integer> droppedList = new ArrayList<>();
	private Map<Integer, FlowPathAP> crossingFlows = new HashMap<>();
	//<flowPathId, <atomicFlowId, atomicPredicate>>
	private Map<Integer, Map<Integer, Integer>> mapFlowIdAtomicPredicatesInInput = new HashMap<>();
	
	/**
	 * Public constructor for the AllocationNodeAP class
	 * @param node It is the JAXB Node object.
	 */
	public AllocationNodeAP(Node node) {
		this.node = node;
		placedNF = null;
		typeNF = null;
		ipAddress = node.getName();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AllocationNodeAP other = (AllocationNodeAP) obj;
		if (ipAddress == null) {
			if (other.ipAddress != null)
				return false;
		} else if (!ipAddress.equals(other.ipAddress))
			return false;
		return true;
	}
	
	/**
	 * Method to add crossing flows specific to Atomic Predicate
	 * @param FlowPathAP object
	 */
	public void addCrossingFlow(FlowPathAP sr) {
		crossingFlows.put(sr.getIdFlow(), sr);
	}
	
	/**
	 * Getter method that returns flows that cross this node
	 * @param crossing flows of this node
	 */
	public Map<Integer, FlowPathAP> getCrossingFlows() {
		return crossingFlows;
	}

	/**
	 * Setter method for the map of requirements
	 * @param requirements the map of requirements
	 */
	public void setFlows(Map<Integer, FlowPathAP> requirements) {
		this.crossingFlows = requirements;
	}
	
	/**
	 * Getter method that returns the transformation Map
	 * @return transformation map of this node
	 */
	public HashMap<Integer, List<Integer>> getTransformationMap(){
		return transformationsMap;
	}
	
	/**
	 * Setter method for the forward behavior predicate list of this node
	 * @param list of predicates.
	 */
	public void setForwardBehaviourPredicateList(List<Predicate> list) {
		this.forwardBehaviourPredicateList = list;
	}
	
	/**
	 * Getter method that returns the list of forward behavior predicates
	 * @return forward behavior predicate list
	 */
	public List<Predicate> getForwardBehaviourPredicateList(){
		return forwardBehaviourPredicateList;
	}
	
	/**
	 * Setter method for the forward behavior list of this node
	 * @param list of Integers presenting forward behavior list
	 */
	public void setForwardBehaviourList(List<Integer> list) {
		forwardBehaviourList = list;
	}

	/**
	 * Getter method that returns the list of forward behavior
	 * @return forward behavior Integer list
	 */
	public List<Integer> getForwardBehaviourList(){
		return forwardBehaviourList;
	}

	/**
	 * Getter method that returns the Dropped list
	 * @return Dropped behavior Integer list
	 */
	public List<Integer> getDroppedList() {
		return droppedList;
	}

	/**
	 * Setter method for the Dropped behavior list
	 * @param list of Integers presenting Dropped behavior list
	 */
	public void setDroppedList(List<Integer> droppedList) {
		this.droppedList = droppedList;
	}

	public void addAtomicPredicateInInput(int flowPathId, int atomicFlowId, int ap) {
		if(mapFlowIdAtomicPredicatesInInput.containsKey(flowPathId)) {
			Map<Integer, Integer> atomicPredicateMap = mapFlowIdAtomicPredicatesInInput.get(flowPathId);
			atomicPredicateMap.put(atomicFlowId, ap);
		} else {
			Map<Integer, Integer> newMap = new HashMap<>();
			newMap.put(atomicFlowId, ap);	
			mapFlowIdAtomicPredicatesInInput.put(flowPathId, newMap);
		}
	}
	
	public Map<Integer, Integer> getAtomicPredicatesInInputForFlow(int flowId){
		if(mapFlowIdAtomicPredicatesInInput.containsKey(flowId))
			return mapFlowIdAtomicPredicatesInInput.get(flowId);
		return null;
	}
	
	/**
	 * Setter method for the forwarding behavior list
	 * @param integer atomic predicate
	 */	
	public void addForwardingPredicate(int ap) {
		forwardBehaviourList.add(ap);
	}
	
	/**
	 * Setter method for the dropped behavior
	 * @param integer atomic predicate
	 */
	public void addDroppedPredicate(int ap) {
		droppedList.add(ap);
	}

	public Map<Integer, Map<Integer, Integer>> getMapFlowIdAtomicPredicatesInInput() {
		return mapFlowIdAtomicPredicatesInInput;
	}

	public void setMapFlowIdAtomicPredicatesInInput(Map<Integer, Map<Integer, Integer>> mapFlowIdAtomicPredicatesInInput) {
		this.mapFlowIdAtomicPredicatesInInput = mapFlowIdAtomicPredicatesInInput;
	}
	
	
}
