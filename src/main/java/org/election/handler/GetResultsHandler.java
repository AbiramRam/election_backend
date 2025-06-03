package org.election.handler;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.election.config.DBConnection;
import org.json.JSONObject;
import org.json.JSONArray;

public class GetResultsHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("GET".equals(exchange.getRequestMethod())) {
            try {
                // Get query parameters
                String query = exchange.getRequestURI().getQuery();
                String[] params = query.split("&");
                int electionId = -1;
                int districtId = -1;
                int partyId = -1;

                for (String param : params) {
                    String[] keyValue = param.split("=");
                    if (keyValue.length == 2) {
                        switch (keyValue[0]) {
                            case "electionId":
                                electionId = Integer.parseInt(keyValue[1]);
                                break;
                            case "districtId":
                                districtId = Integer.parseInt(keyValue[1]);
                                break;
                            case "partyId":
                                partyId = Integer.parseInt(keyValue[1]);
                                break;
                        }
                    }
                }

                try (Connection conn = DBConnection.getConnection()) {
                    JSONObject response = new JSONObject();

                    if (electionId > 0 && districtId > 0 && partyId > 0) {
                        // Get specific party results in a district for an election
                        String sql = "SELECT * FROM partyresults WHERE idElection = ? AND idDistrict = ? AND idParty = ?";
                        PreparedStatement pstmt = conn.prepareStatement(sql);
                        pstmt.setInt(1, electionId);
                        pstmt.setInt(2, districtId);
                        pstmt.setInt(3, partyId);

                        ResultSet rs = pstmt.executeQuery();
                        if (rs.next()) {
                            JSONObject result = new JSONObject();
                            result.put("firstRound", rs.getInt("firstRound"));
                            result.put("secondRound", rs.getInt("secondRound"));
                            result.put("totalSeats", rs.getInt("totalSeats"));
                            response.put("result", result);
                        }
                    } else if (electionId > 0 && districtId > 0) {
                        // Get all party results for a district in an election
                        String sql = "SELECT pr.*, p.* FROM partyresults pr " +
                                "JOIN party p ON pr.idParty = p.id " +
                                "WHERE pr.idElection = ? AND pr.idDistrict = ?";
                        PreparedStatement pstmt = conn.prepareStatement(sql);
                        pstmt.setInt(1, electionId);
                        pstmt.setInt(2, districtId);

                        ResultSet rs = pstmt.executeQuery();
                        JSONArray results = new JSONArray();
                        while (rs.next()) {
                            JSONObject result = new JSONObject();
                            result.put("partyLogo", rs.getBlob("partyLogo"));
                            result.put("partyId", rs.getInt("idParty"));
                            result.put("partyName", rs.getString("partyName"));
                            result.put("firstRound", rs.getInt("firstRound"));
                            result.put("secondRound", rs.getInt("secondRound"));
                            result.put("totalSeats", rs.getInt("totalSeats"));
                            results.put(result);
                        }
                        response.put("results", results);
                    } else if (electionId > 0) {
                        // Get all results for an election
                        String sql = "SELECT dr.*, d.districtName, p.provinceName FROM districtresults dr " +
                                "JOIN district d ON dr.idDistrict = d.id " +
                                "JOIN province p ON d.idProvince = p.id " +
                                "WHERE dr.idElection = ?";
                        PreparedStatement pstmt = conn.prepareStatement(sql);
                        pstmt.setInt(1, electionId);

                        ResultSet rs = pstmt.executeQuery();
                        JSONArray districtResults = new JSONArray();
                        while (rs.next()) {
                            JSONObject district = new JSONObject();
                            district.put("districtId", rs.getInt("idDistrict"));
                            district.put("districtName", rs.getString("districtName"));
                            district.put("provinceName", rs.getString("provinceName"));
                            district.put("totalVotes", rs.getInt("totalVotes"));
                            district.put("rejectedVotes", rs.getInt("rejectedVotes"));
                            district.put("validVotes", rs.getInt("validVotes"));
                            district.put("noOfSeats", rs.getInt("noOfSeats"));
                            district.put("bonusPartyId", rs.getInt("bonusPartyId"));
                            districtResults.put(district);
                        }
                        response.put("districtResults", districtResults);
                    } else {
                        sendResponse(exchange, 400, "{\"status\":\"error\",\"message\":\"Invalid parameters\"}");
                        return;
                    }

                    response.put("status", "success");
                    sendResponse(exchange, 200, response.toString());
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
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }
}