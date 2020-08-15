package jenaserver;

import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import org.apache.jena.tdb.TDBFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.query.ResultSetFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.Query;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import java.net.URLDecoder;

public class App implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

  // TDBファイル(Zip圧縮)
  private static final String DatasetType = "tdb";
  private static final String DatasetPath = "isilloddb1/";
  private static final String DatasetFile = "isilloddb1.zip";

  // RDFファイル
  // private static final String DatasetType = "TURTLE";
  // private static final String DatasetPath = "";
  // private static final String DatasetFile = "isillod.ttl";

  private static final String DbPath = "/tmp/tdb/";

  private static Model model = App.getModel(App.DatasetPath, App.DatasetFile, App.DatasetType);

  public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
    Map<String, String> headers = new HashMap<>();
    headers.put("Access-Control-Allow-Headers", "Content-Type");
    headers.put("Access-Control-Allow-Origin", "*");
    headers.put("Access-Control-Allow-Methods", "OPTIONS,GET");

    APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();

    Map<String, String> params = input.getQueryStringParameters();
    Map<String, String> reqHeaders = input.getHeaders();
    String format = this.getFormat(params, reqHeaders);
    String queryString = this.getSparqlQuery(params);

    if (queryString != null) {
      ResultSet results = App.execSparqlQuery(queryString, this.model);
      String output = this.getOutputFromResultSet(results, format);

      String contentType = this.getContentType(format);
      headers.put("Content-Type", contentType);
      headers.put("X-Custom-Header", contentType);

      return response
        .withHeaders(headers)
        .withStatusCode(200)
        .withBody(output);
    } else {
      headers.put("Content-Type", "application/json");
      headers.put("X-Custom-Header", "application/json");
      return response
        .withHeaders(headers)
        .withStatusCode(400)
        .withBody("{\"status\":\"Error\"}");
    }
  }

  private static ResultSet execSparqlQuery(String queryString, Model model) {
    Query query = QueryFactory.create(queryString);
    try (QueryExecution qexec = QueryExecutionFactory.create(query, model)) {
      ResultSet results = qexec.execSelect();
      results = ResultSetFactory.copyResults(results);
      return results;
    }
  }

  private String getOutputFromResultSet(ResultSet results, String format) {
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    if (format.equals("xml")) {
      ResultSetFormatter.outputAsXML(stream, results);
    } else {
      ResultSetFormatter.outputAsJSON(stream, results);
    }
    String output = "";
    try {
      output = stream.toString("UTF-8");
    } catch (Exception e) {}
    return output;
  }

  private static String copyTdbFiles(String path, String file) {
    String directory = App.DbPath+path;
    if (file != null && file.length()>0) {
      try {
        var target = Paths.get(directory);
        if (!Files.exists(target)) {
          var zipfile = Paths.get("/var/task/static/"+file);
          try (var in = new ZipInputStream(Files.newInputStream(zipfile))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
              if (e.isDirectory()) {
                continue;
              }
              var dst = Paths.get(App.DbPath, e.getName());
              Files.createDirectories(dst.getParent());
              Files.write(dst, in.readAllBytes());
              System.out.printf("inflating: %s%n", dst);
            }
          }
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    return directory;
  }

  private static Model getModel(String path, String file, String type) {
    Model model;
    if (type=="tdb") {
      String directory = App.copyTdbFiles(path, file);
      Dataset dataset = TDBFactory.createDataset(directory);
      model = dataset.getDefaultModel();
    } else {
      model = ModelFactory.createDefaultModel();
      model.read("/var/task/static/"+path+file, type);
    }
    return model;
  }

  private String getFormat(Map<String, String> params, Map<String, String> headers) {
    String format = null;
    if (params != null ) format = params.get("format");
    if (format == null) {
      String accept = "";
      if (headers != null) accept = headers.getOrDefault("accept", "json");
      if (accept.contains("xml")) {
        format = "xml";
      }
    }
    return format != null ? format : "json";
  }

  private String getContentType(String format) {
    String contentType = "application/json";
    if (format.equals("xml")) contentType = "text/xml";
    return contentType;
  }

  private String getSparqlQuery(Map<String, String> params) {
    String queryString = null;
    if (params != null) {
      queryString = params.get("query");
      try {
        if (queryString != null)
          queryString = URLDecoder.decode(queryString, "UTF-8");
      } catch (IOException e) {
        queryString = null;
      }
    }
    return queryString;
  }
}