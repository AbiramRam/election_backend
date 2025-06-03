package org.election.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.election.config.DBConnection;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class CreateElectionHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("POST".equals(exchange.getRequestMethod())) {
            try {
                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                JSONArray electionsJson = new JSONArray(requestBody);

                try (Connection conn = DBConnection.getConnection()) {
                    conn.setAutoCommit(false);
                    String sql = "INSERT INTO election (electionYear, noOfProvinces, totalSeats) VALUES (?, ?, ?)";

                    PreparedStatement pstmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS);
                    JSONArray responseArray = new JSONArray();

                    for (int i = 0; i < electionsJson.length(); i++) {
                        JSONObject json = electionsJson.getJSONObject(i);
                        pstmt.setInt(1, json.getInt("electionYear"));
                        pstmt.setInt(2, json.getInt("noOfProvinces"));
                        pstmt.setInt(3, json.getInt("totalSeats"));
                        pstmt.addBatch();

                        if (i % 100 == 0) { // Execute batch every 100 records
                            pstmt.executeBatch();
                        }
                    }

                    pstmt.executeBatch(); // Execute remaining records
                    ResultSet rs = pstmt.getGeneratedKeys();

                    int index = 0;
                    while (rs.next()) {
                        JSONObject response = new JSONObject();
                        response.put("electionId", rs.getInt(1));
                        response.put("status", "success");
                        response.put("electionYear", electionsJson.getJSONObject(index).getInt("electionYear"));
                        responseArray.put(response);
                        index++;
                    }

                    conn.commit();
                    sendResponse(exchange, 201, responseArray.toString());
                }
            } catch (Exception e) {
                sendResponse(exchange, 500, "{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
            }
        } else {
            sendResponse(exchange, 405, "{\"status\":\"error\",\"message\":\"Method not allowed\"}");
        }
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, response.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }
}