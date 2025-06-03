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

public class DistrictDetailsHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("POST".equals(exchange.getRequestMethod())) {
            try {
                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                JSONArray updatesJson = new JSONArray(requestBody);

                try (Connection conn = DBConnection.getConnection()) {
                    conn.setAutoCommit(false);

                    String sql = "UPDATE districtresults SET totalVotes = ?, noOfSeats = ?, rejectedVotes = ?, validVotes = ? " +
                            "WHERE idDistrict = ? AND idElection = ?";

                    PreparedStatement pstmt = conn.prepareStatement(sql);
                    JSONArray responseArray = new JSONArray();

                    for (int i = 0; i < updatesJson.length(); i++) {
                        JSONObject json = updatesJson.getJSONObject(i);
                        int totalVotes = json.getInt("totalVotes");
                        int noOfSeats = json.getInt("noOfSeats");
                        int rejectedVotes = json.getInt("rejectedVotes");
                        int validVotes = totalVotes - rejectedVotes;

                        pstmt.setInt(1, totalVotes);
                        pstmt.setInt(2, noOfSeats);
                        pstmt.setInt(3, rejectedVotes);
                        pstmt.setInt(4, validVotes);
                        pstmt.setInt(5, json.getInt("districtId"));
                        pstmt.setInt(6, json.getInt("electionId"));
                        pstmt.addBatch();

                        if (i % 100 == 0) {
                            pstmt.executeBatch();
                        }

                        JSONObject response = new JSONObject();
                        response.put("districtId", json.getInt("districtId"));
                        response.put("validVotes", validVotes);
                        response.put("status", "success");
                        responseArray.put(response);
                    }

                    pstmt.executeBatch();
                    conn.commit();
                    sendResponse(exchange, 200, responseArray.toString());
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