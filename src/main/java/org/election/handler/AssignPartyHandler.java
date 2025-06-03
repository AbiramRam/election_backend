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

public class AssignPartyHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("POST".equals(exchange.getRequestMethod())) {
            try {
                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                JSONArray assignmentsJson = new JSONArray(requestBody);

                try (Connection conn = DBConnection.getConnection()) {
                    conn.setAutoCommit(false);

                    String sql = "INSERT INTO partyresults (idParty, idDistrict, idElection) " +
                            "VALUES (?, ?, ?);";

                    PreparedStatement pstmt = conn.prepareStatement(sql);
                    JSONArray responseArray = new JSONArray();

                    for (int i = 0; i < assignmentsJson.length(); i++) {
                        JSONObject json = assignmentsJson.getJSONObject(i);
                        pstmt.setInt(1, json.getInt("partyId"));
                        pstmt.setInt(2, json.getInt("districtId"));
                        pstmt.setInt(3, json.getInt("electionId"));
                        pstmt.addBatch();

                        if (i % 100 == 0) {
                            pstmt.executeBatch();
                        }

                        JSONObject response = new JSONObject();
                        response.put("partyId", json.getInt("partyId"));
                        response.put("districtId", json.getInt("districtId"));
                        response.put("status", "success");
                        responseArray.put(response);
                    }

                    pstmt.executeBatch();
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