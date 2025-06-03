package org.election.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.election.config.DBConnection;
import org.json.JSONArray;
import org.json.JSONObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

public class AddDistrictHandler implements HttpHandler {
    private static final Logger logger = LogManager.getLogger(CalculateSeatsHandler.class);
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("POST".equals(exchange.getRequestMethod())) {
            try {
                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

                // Check if this is an update operation
                String query = exchange.getRequestURI().getQuery();
                Map<String, String> params = parseQuery(query);
                boolean isUpdate = params.containsKey("action") && "update".equals(params.get("action"));

                if (isUpdate) {
                    handleDistrictUpdate(exchange, requestBody);
                } else if (requestBody.trim().startsWith("[")) {
                    handleBulkDistrictCreate(exchange, requestBody);
                } else {
                    handleSingleDistrictCreate(exchange, requestBody);
                }
            } catch (Exception e) {
                sendResponse(exchange, 500, "{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
            }
        } else if ("GET".equals(exchange.getRequestMethod())) {
            handleDistrictGet(exchange);
        } else {
            sendResponse(exchange, 405, "{\"status\":\"error\",\"message\":\"Method not allowed\"}");
        }
    }

    private void handleBulkDistrictCreate(HttpExchange exchange, String requestBody) throws Exception {
        JSONArray districtsJson = new JSONArray(requestBody);
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);

            // First, validate all provinces can accept new districts
            Map<Integer, Integer> provinceDistrictCounts = new HashMap<>();
            for (int i = 0; i < districtsJson.length(); i++) {
                JSONObject json = districtsJson.getJSONObject(i);
                int provinceId = json.getInt("idProvince");
                provinceDistrictCounts.put(provinceId, provinceDistrictCounts.getOrDefault(provinceId, 0) + 1);
            }

            // Check each province's capacity
            for (Map.Entry<Integer, Integer> entry : provinceDistrictCounts.entrySet()) {
                int provinceId = entry.getKey();
                int districtsToAdd = entry.getValue();

                // Get current district count and max allowed for the province
                String checkSql = "SELECT COUNT(d.id) as currentDistricts, p.noOfDistricts as maxDistricts " +
                        "FROM district d RIGHT JOIN province p ON d.idProvince = p.id " +
                        "WHERE p.id = ? GROUP BY p.id, p.noOfDistricts";
                PreparedStatement checkStmt = conn.prepareStatement(checkSql);
                checkStmt.setInt(1, provinceId);
                ResultSet rs = checkStmt.executeQuery();

                if (rs.next()) {
                    int currentDistricts = rs.getInt("currentDistricts");
                    int maxDistricts = rs.getInt("maxDistricts");

                    if (currentDistricts + districtsToAdd > maxDistricts) {
                        conn.rollback();
                        sendResponse(exchange, 400,
                                String.format("{\"status\":\"error\",\"message\":\"Province %s can only have %d districts (currently has %d, trying to add %d)\"}",
                                        provinceId, maxDistricts, currentDistricts, districtsToAdd));
                        return;
                    }
                } else {
                    conn.rollback();
                    sendResponse(exchange, 404,
                            String.format("{\"status\":\"error\",\"message\":\"Province %d not found\"}", provinceId));
                    return;
                }
            }

            // All checks passed, proceed with insertion
            String sql = "INSERT INTO district (districtName, idProvince) VALUES (?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS);
            JSONArray responseArray = new JSONArray();

            for (int i = 0; i < districtsJson.length(); i++) {
                JSONObject json = districtsJson.getJSONObject(i);
                pstmt.setString(1, json.getString("districtName"));
                pstmt.setInt(2, json.getInt("idProvince"));
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
                response.put("districtId", rs.getInt(1));
                response.put("status", "success");
                response.put("districtName", districtsJson.getJSONObject(index).getString("districtName"));
                responseArray.put(response);
                index++;
            }

            logger.info("Start");

            conn.commit();
            sendResponse(exchange, 201, responseArray.toString());
        }
    }

    private void handleSingleDistrictCreate(HttpExchange exchange, String requestBody) throws Exception {
        JSONObject json = new JSONObject(requestBody);
        try (Connection conn = DBConnection.getConnection()) {
            int provinceId = json.getInt("idProvince");

            // Check province capacity first
            String checkSql = "SELECT COUNT(d.id) as currentDistricts, p.noOfDistricts as maxDistricts " +
                    "FROM district d RIGHT JOIN province p ON d.idProvince = p.id " +
                    "WHERE p.id = ? GROUP BY p.id, p.noOfDistricts";
            PreparedStatement checkStmt = conn.prepareStatement(checkSql);
            checkStmt.setInt(1, provinceId);
            ResultSet rs = checkStmt.executeQuery();

            if (rs.next()) {
                int currentDistricts = rs.getInt("currentDistricts");
                int maxDistricts = rs.getInt("maxDistricts");

                if (currentDistricts >= maxDistricts) {
                    sendResponse(exchange, 400,
                            String.format("{\"status\":\"error\",\"message\":\"Province %d has reached its maximum district limit (%d)\"}",
                                    provinceId, maxDistricts));
                    return;
                }
            } else {
                sendResponse(exchange, 404,
                        String.format("{\"status\":\"error\",\"message\":\"Province %d not found\"}", provinceId));
                return;
            }

            // Proceed with insertion
            String sql = "INSERT INTO district (districtName, idProvince) VALUES (?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS);
            pstmt.setString(1, json.getString("districtName"));
            pstmt.setInt(2, provinceId);

            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                rs = pstmt.getGeneratedKeys();
                if (rs.next()) {
                    JSONObject response = new JSONObject();
                    response.put("districtId", rs.getInt(1));
                    response.put("status", "success");
                    sendResponse(exchange, 201, response.toString());
                }
            } else {
                sendResponse(exchange, 400, "{\"status\":\"error\",\"message\":\"Failed to add district\"}");
            }
        }
    }

    private void handleDistrictUpdate(HttpExchange exchange, String requestBody) throws Exception {
        JSONObject json = new JSONObject(requestBody);

        if (!json.has("id") || !json.has("districtName") || !json.has("idProvince")) {
            sendResponse(exchange, 400, "{\"status\":\"error\",\"message\":\"Missing required fields (id, districtName, idProvince)\"}");
            return;
        }

        int districtId = json.getInt("id");
        String districtName = json.getString("districtName");
        int newProvinceId = json.getInt("idProvince");

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);

            // 1. Check if district exists
            String checkDistrictSql = "SELECT idProvince FROM district WHERE id = ?";
            PreparedStatement checkDistrictStmt = conn.prepareStatement(checkDistrictSql);
            checkDistrictStmt.setInt(1, districtId);
            ResultSet rs = checkDistrictStmt.executeQuery();

            if (!rs.next()) {
                sendResponse(exchange, 404, "{\"status\":\"error\",\"message\":\"District not found\"}");
                return;
            }

            int currentProvinceId = rs.getInt("idProvince");

            // 2. If province is being changed, check capacity of new province
            if (currentProvinceId != newProvinceId) {
                String checkProvinceSql = "SELECT COUNT(d.id) as currentDistricts, p.noOfDistricts as maxDistricts " +
                        "FROM district d RIGHT JOIN province p ON d.idProvince = p.id " +
                        "WHERE p.id = ? GROUP BY p.id, p.noOfDistricts";
                PreparedStatement checkProvinceStmt = conn.prepareStatement(checkProvinceSql);
                checkProvinceStmt.setInt(1, newProvinceId);
                rs = checkProvinceStmt.executeQuery();

                if (rs.next()) {
                    int currentDistricts = rs.getInt("currentDistricts");
                    int maxDistricts = rs.getInt("maxDistricts");

                    if (currentDistricts >= maxDistricts) {
                        sendResponse(exchange, 400,
                                String.format("{\"status\":\"error\",\"message\":\"Province %d has reached its maximum district limit (%d)\"}",
                                        newProvinceId, maxDistricts));
                        return;
                    }
                } else {
                    sendResponse(exchange, 404,
                            String.format("{\"status\":\"error\",\"message\":\"Province %d not found\"}", newProvinceId));
                    return;
                }
            }

            // 3. Update district
            String updateSql = "UPDATE district SET districtName = ?, idProvince = ? WHERE id = ?";
            PreparedStatement updateStmt = conn.prepareStatement(updateSql);
            updateStmt.setString(1, districtName);
            updateStmt.setInt(2, newProvinceId);
            updateStmt.setInt(3, districtId);

            int affectedRows = updateStmt.executeUpdate();

            if (affectedRows > 0) {
                conn.commit();
                JSONObject response = new JSONObject();
                response.put("status", "success");
                response.put("message", "District updated successfully");
                sendResponse(exchange, 200, response.toString());
            } else {
                conn.rollback();
                sendResponse(exchange, 400, "{\"status\":\"error\",\"message\":\"Failed to update district\"}");
            }
        }
    }

    private void handleDistrictGet(HttpExchange exchange) throws IOException {
        // Get districts with optional province filter
        String query = exchange.getRequestURI().getQuery();
        Map<String, String> params = parseQuery(query);

        try (Connection conn = DBConnection.getConnection()) {
            String sql;
            PreparedStatement pstmt;

            if (params.containsKey("provinceId")) {
                sql = "SELECT d.id, d.districtName, d.idProvince, p.provinceName, p.noOfDistricts " +
                        "FROM district d JOIN province p ON d.idProvince = p.id " +
                        "WHERE d.idProvince = ?";
                pstmt = conn.prepareStatement(sql);
                pstmt.setInt(1, Integer.parseInt(params.get("provinceId")));
            } else {
                sql = "SELECT d.id, d.districtName, d.idProvince, p.provinceName, p.noOfDistricts " +
                        "FROM district d JOIN province p ON d.idProvince = p.id";
                pstmt = conn.prepareStatement(sql);
            }

            ResultSet rs = pstmt.executeQuery();
            JSONArray districts = new JSONArray();

            while (rs.next()) {
                JSONObject district = new JSONObject();
                district.put("id", rs.getInt("id"));
                district.put("districtName", rs.getString("districtName"));
                district.put("idProvince", rs.getInt("idProvince"));
                district.put("provinceName", rs.getString("provinceName"));
                district.put("maxDistricts", rs.getInt("noOfDistricts"));
                districts.put(district);
            }

            JSONObject response = new JSONObject();
            response.put("status", "success");
            response.put("districts", districts);
            sendResponse(exchange, 200, response.toString());
        } catch (Exception e) {
            sendResponse(exchange, 500, "{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] pair = param.split("=");
                if (pair.length == 2) {
                    params.put(pair[0], pair[1]);
                }
            }
        }
        return params;
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, response.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }
}