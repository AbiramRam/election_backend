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
public class AddDistrictResultsHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("POST".equals(exchange.getRequestMethod())) {
            try {
                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

                // Support both single and bulk operations
                if (requestBody.trim().startsWith("[")) {
                    JSONArray resultsJson = new JSONArray(requestBody);
                    try (Connection conn = DBConnection.getConnection()) {
                        conn.setAutoCommit(false);

                        String sql = "INSERT INTO districtresults (idDistrict, idElection, totalVotes, rejectedVotes, validVotes, noOfSeats) " +
                                "VALUES (?, ?, ?, ?, ?, ?) " +
                                "ON DUPLICATE KEY UPDATE " +
                                "totalVotes = VALUES(totalVotes), " +
                                "rejectedVotes = VALUES(rejectedVotes), " +
                                "validVotes = VALUES(validVotes), " +
                                "noOfSeats = VALUES(noOfSeats)";

                        PreparedStatement pstmt = conn.prepareStatement(sql);
                        JSONArray responseArray = new JSONArray();

                        for (int i = 0; i < resultsJson.length(); i++) {
                            JSONObject json = resultsJson.getJSONObject(i);
                            int totalVotes = json.getInt("totalVotes");
                            int rejectedVotes = json.getInt("rejectedVotes");
                            int validVotes = totalVotes - rejectedVotes;

                            pstmt.setInt(1, json.getInt("idDistrict"));
                            pstmt.setInt(2, json.getInt("idElection"));
                            pstmt.setInt(3, totalVotes);
                            pstmt.setInt(4, rejectedVotes);
                            pstmt.setInt(5, validVotes);
                            pstmt.setInt(6, json.getInt("noOfSeats"));
                            pstmt.addBatch();

                            JSONObject response = new JSONObject();
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
                    // Single result addition
                    JSONObject json = new JSONObject(requestBody);
                    try (Connection conn = DBConnection.getConnection()) {
                        int totalVotes = json.getInt("totalVotes");
                        int rejectedVotes = json.getInt("rejectedVotes");
                        int validVotes = totalVotes - rejectedVotes;

                        String sql = "INSERT INTO districtresults (idDistrict, idElection, totalVotes, rejectedVotes, validVotes, noOfSeats) " +
                                "VALUES (?, ?, ?, ?, ?, ?) " +
                                "ON DUPLICATE KEY UPDATE " +
                                "totalVotes = VALUES(totalVotes), " +
                                "rejectedVotes = VALUES(rejectedVotes), " +
                                "validVotes = VALUES(validVotes), " +
                                "noOfSeats = VALUES(noOfSeats)";

                        PreparedStatement pstmt = conn.prepareStatement(sql);
                        pstmt.setInt(1, json.getInt("idDistrict"));
                        pstmt.setInt(2, json.getInt("idElection"));
                        pstmt.setInt(3, totalVotes);
                        pstmt.setInt(4, rejectedVotes);
                        pstmt.setInt(5, validVotes);
                        pstmt.setInt(6, json.getInt("noOfSeats"));

                        int affectedRows = pstmt.executeUpdate();

                        if (affectedRows > 0) {
                            JSONObject response = new JSONObject();
                            response.put("districtId", json.getInt("idDistrict"));
                            response.put("electionId", json.getInt("idElection"));
                            response.put("status", "success");
                            sendResponse(exchange, 201, response.toString());
                        } else {
                            sendResponse(exchange, 400, "{\"status\":\"error\",\"message\":\"Failed to add district results\"}");
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
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, response.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }
}