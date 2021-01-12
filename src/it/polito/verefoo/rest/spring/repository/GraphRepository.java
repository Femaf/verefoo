package it.polito.verefoo.rest.spring.repository;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

import it.polito.verefoo.jaxb.Graph;

@Repository
public interface GraphRepository extends Neo4jRepository<Graph, Long> {
    
}
