package jenaserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import org.apache.jena.tdb.TDBFactory;
import org.apache.jena.rdf.model.*;
import org.apache.jena.query.*;
import java.io.ByteArrayOutputStream;

import java.io.File;
import org.apache.commons.io.FileUtils;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import java.net.URLDecoder;

public class App implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

  // TDBファイル(Zip圧縮)
  private final String DatasetType = "tdb";
  private final String DatasetPath = "isilloddb1/";
  private final String DatasetFile = "isilloddb1.zip";

  // TDBファイル(非圧縮)
  // private final String DatasetType = "tdb";
  // private final String DatasetPath = "isilloddb1/";
  // private final String DatasetFile = "";

  // RDFファイル
  // private final String DatasetType = "TURTLE";
  // private final String DatasetPath = "";
  // private final String DatasetFile = "isillod.ttl";

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
      Model model = this.getModel(this.DatasetPath, this.DatasetFile, this.DatasetType);
      ResultSet results = this.execSparqlQuery(queryString, model);
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

  private ResultSet execSparqlQuery(String queryString, Model model) {
    Query query = QueryFactory.create(queryString);
    try (QueryExecution qexec = QueryExecutionFactory.create(query, model)) {
      ResultSet results = qexec.execSelect();
      results = ResultSetFactory.copyResults(results) ;
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

  private String copyTdbFiles(String path, String file) {
    String directory = "/tmp/static/"+path;
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
              var dst = Paths.get("/tmp/static/", e.getName());
              Files.createDirectories(dst.getParent());
              Files.write(dst, in.readAllBytes());
              System.out.printf("inflating: %s%n", dst);
            }
          }
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    } else {
      File srcDir = new File("/var/task/static/"+path);
      File destDir = new File(directory);
      if (!destDir.exists()) {
        try {
          FileUtils.copyDirectory(srcDir, destDir);
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    return directory;
  }

  private Model getModel(String path, String file, String type) {
    Model model;
    if (type=="tdb") {
      String directory = this.copyTdbFiles(path, file);
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