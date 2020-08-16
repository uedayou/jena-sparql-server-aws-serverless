package jenaserver;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import java.util.Map;

public interface ISparqlServer {
  APIGatewayProxyResponseEvent getResponse(Map<String, String> params, Map<String, String> reqHeaders);
}