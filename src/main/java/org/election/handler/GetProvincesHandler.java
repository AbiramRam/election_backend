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

public class GetProvincesHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("GET".equals(exchange.getRequestMethod())) {
            try (Connection conn = DBConnection.getConnection()) {
                String sql = "SELECT * FROM province";
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
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }
}