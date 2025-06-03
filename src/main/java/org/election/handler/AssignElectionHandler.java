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

public class AssignElectionHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("POST".equals(exchange.getRequestMethod())) {
            try {
                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                JSONObject json = new JSONObject(requestBody);
                int electionId = json.getInt("electionId");

                try (Connection conn = DBConnection.getConnection()) {
                    conn.setAutoCommit(false);

                    // First, validate the election exists
                    if (!electionExists(conn, electionId)) {
                        sendResponse(exchange, 404, "{\"status\":\"error\",\"message\":\"Election not found\"}");
                        return;
                    }

                    // 1. Validate and assign provinces to election
                    if (json.has("provinceIds")) {
                        JSONArray provinceIds = json.getJSONArray("provinceIds");

                        // Check if provinces exist and get their counts
                        for (int i = 0; i < provinceIds.length(); i++) {
                            int provinceId = provinceIds.getInt(i);
                            if (!provinceExists(conn, provinceId)) {
                                conn.rollback();
                                sendResponse(exchange, 404,
                                        String.format("{\"status\":\"error\",\"message\":\"Province %d not found\"}", provinceId));
                                return;
                            }
                        }

                        // Check if adding these provinces would exceed election limits
                        String checkProvinceCountSql = "SELECT COUNT(*) as currentCount, e.noOfProvinces as maxCount " +
                                "FROM provinceresults pr " +
                                "JOIN election e ON pr.idElection = e.id " +
                                "WHERE pr.idElection = ? " +
                                "GROUP BY e.noOfProvinces";
                        PreparedStatement checkProvinceStmt = conn.prepareStatement(checkProvinceCountSql);
                        checkProvinceStmt.setInt(1, electionId);
                        ResultSet rs = checkProvinceStmt.executeQuery();

                        int currentProvinceCount = 0;
                        int maxProvinceCount = Integer.MAX_VALUE;

                        if (rs.next()) {
                            currentProvinceCount = rs.getInt("currentCount");
                            maxProvinceCount = rs.getInt("maxCount");
                        } else {
                            // If no provinces assigned yet, get max from election table
                            String getMaxSql = "SELECT noOfProvinces as maxCount FROM election WHERE id = ?";
                            PreparedStatement maxStmt = conn.prepareStatement(getMaxSql);
                            maxStmt.setInt(1, electionId);
                            rs = maxStmt.executeQuery();
                            if (rs.next()) {
                                maxProvinceCount = rs.getInt("maxCount");
                            }
                        }

                        System.out.println("Started");

                        if (currentProvinceCount + provinceIds.length() > maxProvinceCount) {
                            conn.rollback();
                            sendResponse(exchange, 400,
                                    String.format("{\"status\":\"error\",\"message\":\"Cannot assign %d provinces. Election only allows %d provinces (currently has %d)\"}",
                                            provinceIds.length(), maxProvinceCount, currentProvinceCount));
                            return;
                        }

                        // All checks passed, proceed with assignment
                        String provinceSql = "INSERT INTO provinceresults (idElection, idProvince, noOfDistricts) " +
                                "SELECT ?, p.id, p.noOfDistricts FROM province p " +
                                "WHERE p.id = ? AND NOT EXISTS (" +
                                "SELECT 1 FROM provinceresults pr " +
                                "WHERE pr.idElection = ? AND pr.idProvince = p.id)";

                        PreparedStatement provinceStmt = conn.prepareStatement(provinceSql);

                        for (int i = 0; i < provinceIds.length(); i++) {
                            int provinceId = provinceIds.getInt(i);
                            provinceStmt.setInt(1, electionId);
                            provinceStmt.setInt(2, provinceId);
                            provinceStmt.setInt(3, electionId);
                            provinceStmt.addBatch();

                            if (i % 100 == 0) {
                                provinceStmt.executeBatch();
                            }
                        }
                        provinceStmt.executeBatch();
                    }

                    // 2. Validate and assign districts to election
                    if (json.has("districtIds")) {
                        JSONArray districtIds = json.getJSONArray("districtIds");
                        JSONArray noOfSeats = json.getJSONArray("noOfSeats");

                        if (districtIds.length() != noOfSeats.length()) {
                            conn.rollback();
                            sendResponse(exchange, 400,
                                    "{\"status\":\"error\",\"message\":\"District IDs and number of seats arrays must be of equal length\"}");
                            return;
                        }

                        // Check if districts exist and belong to assigned provinces
                        for (int i = 0; i < districtIds.length(); i++) {
                            int districtId = districtIds.getInt(i);
                            if (!districtExists(conn, districtId)) {
                                conn.rollback();
                                sendResponse(exchange, 404,
                                        String.format("{\"status\":\"error\",\"message\":\"District %d not found\"}", districtId));
                                return;
                            }

                            // Check if district belongs to a province assigned to this election
                            if (!isDistrictInAssignedProvince(conn, electionId, districtId)) {
                                conn.rollback();
                                sendResponse(exchange, 400,
                                        String.format("{\"status\":\"error\",\"message\":\"District %d's province is not assigned to this election\"}", districtId));
                                return;
                            }
                        }

                        // Check if adding these districts would exceed province limits
                        for (int i = 0; i < districtIds.length(); i++) {
                            int districtId = districtIds.getInt(i);
                            int seatCount = noOfSeats.getInt(i);

                            // Get province for this district
                            String provinceSql = "SELECT idProvince FROM district WHERE id = ?";
                            PreparedStatement provinceStmt = conn.prepareStatement(provinceSql);
                            provinceStmt.setInt(1, districtId);
                            ResultSet provinceRs = provinceStmt.executeQuery();

                            if (provinceRs.next()) {
                                int provinceId = provinceRs.getInt("idProvince");

                                // Check current seat count in province
                                String seatCheckSql = "SELECT SUM(dr.noOfSeats) as currentSeats, pr.noOfDistricts as maxSeats " +
                                        "FROM districtresults dr " +
                                        "JOIN district d ON dr.idDistrict = d.id " +
                                        "JOIN provinceresults pr ON d.idProvince = pr.idProvince AND pr.idElection = dr.idElection " +
                                        "WHERE dr.idElection = ? AND d.idProvince = ? " +
                                        "GROUP BY pr.noOfDistricts";
                                PreparedStatement seatStmt = conn.prepareStatement(seatCheckSql);
                                seatStmt.setInt(1, electionId);
                                seatStmt.setInt(2, provinceId);
                                ResultSet seatRs = seatStmt.executeQuery();

                                int currentSeats = 0;
                                int maxSeats = 0;

                                if (seatRs.next()) {
                                    currentSeats = seatRs.getInt("currentSeats");
                                    maxSeats = seatRs.getInt("maxSeats");
                                } else {
                                    // If no districts assigned yet, get max from province
                                    String maxSeatSql = "SELECT noOfDistricts as maxSeats FROM province WHERE id = ?";
                                    PreparedStatement maxStmt = conn.prepareStatement(maxSeatSql);
                                    maxStmt.setInt(1, provinceId);
                                    seatRs = maxStmt.executeQuery();
                                    if (seatRs.next()) {
                                        maxSeats = seatRs.getInt("maxSeats");
                                    }
                                }

                                if (currentSeats + seatCount > maxSeats) {
                                    conn.rollback();
                                    sendResponse(exchange, 400,
                                            String.format("{\"status\":\"error\",\"message\":\"Cannot assign %d seats to district %d. Province %d only allows %d seats (currently has %d)\"}",
                                                    seatCount, districtId, provinceId, maxSeats, currentSeats));
                                    return;
                                }
                            }
                        }

                        // All checks passed, proceed with assignment
                        String districtSql = "INSERT INTO districtresults (idElection, idDistrict, noOfSeats) " +
                                "SELECT ?, d.id, ? " +
                                "FROM district d " +
                                "WHERE d.id = ? AND NOT EXISTS (" +
                                "SELECT 1 FROM districtresults dr " +
                                "WHERE dr.idElection = ? AND dr.idDistrict = d.id)";

                        PreparedStatement districtStmt = conn.prepareStatement(districtSql);

                        for (int i = 0; i < districtIds.length(); i++) {
                            int districtId = districtIds.getInt(i);
                            int noOfSeat = noOfSeats.getInt(i);
                            districtStmt.setInt(1, electionId);
                            districtStmt.setInt(2, noOfSeat);
                            districtStmt.setInt(3, districtId);
                            districtStmt.setInt(4, electionId);
                            districtStmt.addBatch();

                            if (i % 100 == 0) {
                                districtStmt.executeBatch();
                            }
                        }
                        districtStmt.executeBatch();
                    }

                    conn.commit();
                    System.out.println("Assigned");
                    sendResponse(exchange, 200, "{\"status\":\"success\",\"message\":\"Election assigned successfully\"}");
                }
            } catch (Exception e) {
                try(Connection conn = DBConnection.getConnection()) {
                    if (conn != null) conn.rollback();
                } catch (Exception ex) {
                    // Ignore
                }
                sendResponse(exchange, 500, "{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
            }
        } else {
            sendResponse(exchange, 405, "{\"status\":\"error\",\"message\":\"Method not allowed\"}");
        }
    }

    private boolean electionExists(Connection conn, int electionId) throws Exception {
        String sql = "SELECT 1 FROM election WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, electionId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean provinceExists(Connection conn, int provinceId) throws Exception {
        String sql = "SELECT 1 FROM province WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, provinceId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean districtExists(Connection conn, int districtId) throws Exception {
        String sql = "SELECT 1 FROM district WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, districtId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean isDistrictInAssignedProvince(Connection conn, int electionId, int districtId) throws Exception {
        String sql = "SELECT 1 FROM district d " +
                "JOIN provinceresults pr ON d.idProvince = pr.idProvince " +
                "WHERE d.id = ? AND pr.idElection = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, districtId);
            stmt.setInt(2, electionId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
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