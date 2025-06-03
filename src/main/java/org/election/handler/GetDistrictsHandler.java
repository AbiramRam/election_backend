package org.election.handler;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.election.config.DBConnection;
import org.json.JSONArray;
import org.json.JSONObject;

public class GetDistrictsHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("GET".equals(exchange.getRequestMethod())) {
            try (Connection conn = DBConnection.getConnection()) {
                String sql = "SELECT d.id, d.districtName, d.idProvince, p.provinceName " +
                        "FROM district d JOIN province p ON d.idProvince = p.id";
                PreparedStatement pstmt = conn.prepareStatement(sql);
                ResultSet rs = pstmt.executeQuery();

                JSONArray districts = new JSONArray();
                while (rs.next()) {
                    JSONObject district = new JSONObject();
                    district.put("id", rs.getInt("id"));
                    district.put("districtName", rs.getString("districtName"));
                    district.put("idProvince", rs.getInt("idProvince"));
                    district.put("provinceName", rs.getString("provinceName"));
                    districts.put(district);
                }

                JSONObject response = new JSONObject();
                response.put("status", "success");
                response.put("districts", districts);
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
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }
}