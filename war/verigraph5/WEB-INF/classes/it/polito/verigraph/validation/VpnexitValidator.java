package it.polito.verigraph.validation;

import com.fasterxml.jackson.databind.JsonNode;

import it.polito.verigraph.model.Configuration;
import it.polito.verigraph.model.Graph;
import it.polito.verigraph.model.Node;
import it.polito.verigraph.validation.exception.ValidationException;

public class VpnexitValidator implements ValidationInterface {

	@Override
	public void validate(Graph graph, Node node, Configuration configuration) throws ValidationException {
		JsonNode configurationNode = configuration.getConfiguration();

		if (!configurationNode.isArray())
			throw new ValidationException("configuration must be an array");

		for (JsonNode object : configurationNode) {
			JsonNode vpnexit = object.get("vpnaccess");
			if (!vpnexit.isTextual())
				throw new ValidationException("value corresponding to the key 'vpnaccess' must be a string");
			validateObject(graph, node, vpnexit.asText());
		}

	}

	private void validateObject(Graph g, Node node, String field) throws ValidationException {
		boolean isValid = false;
		for (Node n : g.getNodes().values()) {
			if ((n.getFunctional_type().equals("vpnaccess")) && (n.getName().equals(field)))
				isValid = true;
		}

		if (!isValid)
			throw new ValidationException("'"	+ field + "' is not a valid value for the configuration of a type '"
											+ node.getFunctional_type()
											+ "'. Please use the name of an existing node of type 'vpnaccess'.");

	}

}
