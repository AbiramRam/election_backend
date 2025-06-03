package org.election.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.election.config.DBConnection;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.Base64;

public class AddPartyHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendErrorResponse(exchange, 405, "Method not allowed");
            return;
        }

        try {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

            // Validate JSON structure first
            if (requestBody == null || requestBody.trim().isEmpty()) {
                sendErrorResponse(exchange, 400, "Empty request body");
                return;
            }

            JSONObject json;
            try {
                json = new JSONObject(requestBody);
            } catch (Exception e) {
                sendErrorResponse(exchange, 400, "Invalid JSON format");
                return;
            }

            // Validate required fields
            if (!json.has("partyName") || !json.has("partyLogo") || !json.has("idElection")) {
                sendErrorResponse(exchange, 400, "Missing required fields (partyName, partyLogo, idElection)");
                return;
            }

            // Process image
            String logoData = json.getString("partyLogo");
            byte[] logoBytes;
            try {
                if (!logoData.startsWith("data:image")) {
                    throw new IllegalArgumentException("Invalid image format");
                }
                String base64Data = logoData.split(",")[1];
                logoBytes = Base64.getDecoder().decode(base64Data);
            } catch (Exception e) {
                sendErrorResponse(exchange, 400, "Invalid image data: " + e.getMessage());
                return;
            }

            // Database operation
            try (Connection conn = DBConnection.getConnection()) {
                String sql = "INSERT INTO party (partyName, partyLogo, idElection) VALUES (?, ?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    pstmt.setString(1, json.getString("partyName"));
                    pstmt.setBytes(2, logoBytes);
                    pstmt.setInt(3, json.getInt("idElection"));

                    int affectedRows = pstmt.executeUpdate();
                    if (affectedRows == 0) {
                        sendErrorResponse(exchange, 500, "Failed to create party");
                        return;
                    }

                    try (ResultSet rs = pstmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            JSONObject response = new JSONObject();
                            response.put("status", "success");
                            response.put("partyId", rs.getInt(1));
                            sendResponse(exchange, 201, response.toString());
                        }
                    }
                }
            }
        } catch (Exception e) {
            sendErrorResponse(exchange, 500, "Server error: " + e.getMessage());
        }
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, response.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }

    private void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        JSONObject response = new JSONObject();
        response.put("status", "error");
        response.put("message", message);
        sendResponse(exchange, statusCode, response.toString());
    }
}