package it.polito.verigraph.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import javax.xml.bind.JAXBException;

import org.neo4j.graphdb.Transaction;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

import it.polito.neo4j.jaxb.GraphToNeo4j;
import it.polito.neo4j.jaxb.Graphs;
import it.polito.neo4j.jaxb.ObjectFactory;
import it.polito.neo4j.manager.Neo4jDBManager;
import it.polito.neo4j.manager.Neo4jLibrary;
import it.polito.neo4j.service.Service;
import it.polito.neo4j.exceptions.DuplicateNodeException;
import it.polito.neo4j.exceptions.MyInvalidObjectException;
import it.polito.neo4j.exceptions.MyInvalidIdException;
import it.polito.neo4j.exceptions.MyNotFoundException;
import it.polito.verigraph.exception.DataNotFoundException;
import it.polito.verigraph.exception.ForbiddenException;
import it.polito.verigraph.model.Graph;
import it.polito.verigraph.model.Neighbour;
import it.polito.verigraph.model.Node;

public class GraphService {
	
	
	private Neo4jDBManager manager= new Neo4jDBManager();

	public GraphService() {
		
	}

	public List<Graph> getAllGraphs() throws JsonProcessingException, MyNotFoundException {
		List<Graph> result;
		result=manager.getGraphs();
		for(Graph g : result){
			validateGraph(g);
		}
		return result;
	}

	public Graph getGraph(long id) throws JsonParseException, JsonMappingException, JAXBException, IOException {
		if (id < 0) {
			throw new ForbiddenException("Illegal graph id: " + id);
		}
		Graph localGraph=manager.getGraph(id);
		validateGraph(localGraph);
		return localGraph;
	}

	public Graph updateGraph(Graph graph) throws JAXBException, JsonParseException, JsonMappingException, IOException {
		if (graph.getId() < 0) {
			throw new ForbiddenException("Illegal graph id: " + graph.getId());
		}
			
		validateGraph(graph);		
		
		
		Graph localGraph=new Graph();
		localGraph=manager.updateGraph(graph);
		System.out.println("update graph ok");
		
		
		validateGraph(localGraph);
		return localGraph;
	}

	
	public void removeGraph(long id) {
		
		if (id < 0) {
			throw new ForbiddenException("Illegal graph id: " + id);
		}
		
		manager.deleteGraph(id);		
		
	}

	public Graph addGraph(Graph graph) throws JAXBException, JsonParseException, JsonMappingException, IOException {
		validateGraph(graph);		
		
		Graph g=manager.addGraph(graph);
		
		validateGraph(g);
		
		return g;
	}

	public static void validateGraph(Graph graph) throws JsonProcessingException {
		for (Node node : graph.getNodes().values()) {
			NodeService.validateNode(graph, node);
		}
	}
}
