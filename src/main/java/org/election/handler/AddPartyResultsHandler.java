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

public class AddPartyResultsHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("POST".equals(exchange.getRequestMethod())) {
            try {
                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

                if (requestBody.trim().startsWith("[")) {
                    JSONArray resultsJson = new JSONArray(requestBody);
                    try (Connection conn = DBConnection.getConnection()) {
                        conn.setAutoCommit(false);

                        String sql = "INSERT INTO partyresults (idParty, idDistrict, idElection,votesGained) " +
                                "VALUES (?, ?, ?, ?) " +
                                "ON DUPLICATE KEY UPDATE " +
                                "votesGained = VALUES(votesGained); ";

                        PreparedStatement pstmt = conn.prepareStatement(sql);
                        JSONArray responseArray = new JSONArray();

                        for (int i = 0; i < resultsJson.length(); i++) {
                            JSONObject json = resultsJson.getJSONObject(i);
                            pstmt.setInt(1, json.getInt("idParty"));
                            pstmt.setInt(2, json.getInt("idDistrict"));
                            pstmt.setInt(3, json.getInt("idElection"));
                            pstmt.setInt(4, json.getInt("votesgained"));
                            pstmt.addBatch();

                            JSONObject response = new JSONObject();
                            response.put("partyId", json.getInt("idParty"));
                            response.put("districtId", json.getInt("idDistrict"));
                            response.put("electionId", json.getInt("idElection"));
                            response.put("status", "success");
                            responseArray.put(response);

                            if (i % 100 == 0) {
                                pstmt.executeBatch();
                            }
                        }

                        pstmt.executeBatch();
                        conn.commit();
                        sendResponse(exchange, 201, responseArray.toString());
                    }
                } else {
                    JSONObject json = new JSONObject(requestBody);
                    try (Connection conn = DBConnection.getConnection()) {
                        String sql = "INSERT INTO partyresults (idParty, idDistrict, idElection,votesGained) " +
                                "VALUES (?, ?, ?) " +
                                "ON DUPLICATE KEY UPDATE " +
                                "votesGained = VALUES(votesGained), ";

                        PreparedStatement pstmt = conn.prepareStatement(sql);
                        pstmt.setInt(1, json.getInt("idParty"));
                        pstmt.setInt(2, json.getInt("idDistrict"));
                        pstmt.setInt(3, json.getInt("idElection"));
                        pstmt.setInt(4, json.getInt("votesGained"));

                        int affectedRows = pstmt.executeUpdate();

                        if (affectedRows > 0) {
                            JSONObject response = new JSONObject();
                            response.put("partyId", json.getInt("idParty"));
                            response.put("districtId", json.getInt("idDistrict"));
                            response.put("electionId", json.getInt("idElection"));
                            response.put("status", "success");
                            sendResponse(exchange, 201, response.toString());
                        } else {
                            sendResponse(exchange, 400, "{\"status\":\"error\",\"message\":\"Failed to add party results\"}");
                        }
                    }
                }
            } catch (Exception e) {
                sendResponse(exchange, 500, "{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
            }
        } else {
            sendResponse(exchange, 405, "{\"status\":\"error\",\"message\":\"Method not allowed\"}");
        }
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/form");
        exchange.sendResponseHeaders(statusCode, response.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }
}