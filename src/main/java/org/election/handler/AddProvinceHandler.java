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
import java.sql.SQLException;

import static org.election.config.DBConnection.getConnection;

public class AddProvinceHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {

        if ("POST".equals(exchange.getRequestMethod())) {
            try {
                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

                // Support both single and bulk operations
                if (requestBody.trim().startsWith("[")) {
                    JSONArray provincesJson = new JSONArray(requestBody);
                    try (Connection conn = getConnection()) {
                        conn.setAutoCommit(false);

                        String sqlPro = "SELECT 1 FROM province WHERE provinceName=?";
                        PreparedStatement pstmt = conn.prepareStatement(sqlPro);
                        JSONObject json = null;
                        pstmt.setString(1, json.getString("provinceName"));

                        try (ResultSet rs = pstmt.executeQuery()) {
                            if(rs.next()){
                                sendResponse(exchange, 405, "{\"status\":\"error\",\":\"Province already Exist\"}");
                                return;
                            }
                        }

                        String sql = "INSERT INTO province (provinceName, noOfDistricts) VALUES (?, ?)";

                        pstmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS);
                        JSONArray responseArray = new JSONArray();

                        for (int i = 0; i < provincesJson.length(); i++) {
                            json = provincesJson.getJSONObject(i);
                            pstmt.setString(1, json.getString("provinceName"));
                            pstmt.setInt(2, json.optInt("noOfDistricts", 0)); // Optional field
                            pstmt.addBatch();

                            if (i % 100 == 0) {
                                pstmt.executeBatch();
                            }
                        }

                        pstmt.executeBatch();
                        ResultSet rs = pstmt.getGeneratedKeys();

                        int index = 0;
                        while (rs.next()) {
                            JSONObject response = new JSONObject();
                            response.put("provinceId", rs.getInt(1));
                            response.put("status", "success");
                            response.put("provinceName", provincesJson.getJSONObject(index).getString("provinceName"));
                            responseArray.put(response);
                            index++;
                        }

                        conn.commit();
                        sendResponse(exchange, 201, responseArray.toString());
                    }
                } else {
                    // Single province creation
                    JSONObject json = new JSONObject(requestBody);
                    try (Connection conn = getConnection()) {
                        String sql = "INSERT INTO province (provinceName, noOfDistricts) VALUES (?, ?)";
                        PreparedStatement pstmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS);
                        pstmt.setString(1, json.getString("provinceName"));
                        pstmt.setInt(2, json.optInt("noOfDistricts", 0));

                        int affectedRows = pstmt.executeUpdate();

                        if (affectedRows > 0) {
                            ResultSet rs = pstmt.getGeneratedKeys();
                            if (rs.next()) {
                                JSONObject response = new JSONObject();
                                response.put("provinceId", rs.getInt(1));
                                response.put("status", "success");
                                sendResponse(exchange, 201, response.toString());
                            }
                        } else {
                            sendResponse(exchange, 400, "{\"status\":\"error\",\"message\":\"Failed to add province\"}");
                        }
                    }
                }
            } catch (Exception e) {
                sendResponse(exchange, 500, "{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
            }
        } else if ("GET".equals(exchange.getRequestMethod())) {
            // Get all provinces
            try (Connection conn = getConnection()) {
                String sql = "SELECT id, provinceName, noOfDistricts FROM province";
                PreparedStatement pstmt = conn.prepareStatement(sql);
                ResultSet rs = pstmt.executeQuery();

                JSONArray provinces = new JSONArray();
                while (rs.next()) {
                    JSONObject province = new JSONObject();
                    province.put("id", rs.getInt("id"));
                    province.put("provinceName", rs.getString("provinceName"));
                    province.put("noOfDistricts", rs.getInt("noOfDistricts"));
                    provinces.put(province);
                }

                JSONObject response = new JSONObject();
                response.put("status", "success");
                response.put("provinces", provinces);
                sendResponse(exchange, 200, response.toString());
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

    public boolean provinceExists(String provinceName) throws SQLException {
        String sql = "SELECT 1 FROM province WHERE provinceName=?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, provinceName);

            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        }
    }
}