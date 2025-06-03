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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CalculateSeatsHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("POST".equals(exchange.getRequestMethod())) {
            Connection conn = null;
            try {
                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                JSONObject json = new JSONObject(requestBody);
                int districtId = json.getInt("districtId");
                int electionId = json.getInt("electionId");

                conn = DBConnection.getConnection();
                conn.setAutoCommit(false);

                // 1. Get district details
                String districtSql = "SELECT noOfSeats, validVotes FROM districtresults " +
                        "WHERE idDistrict = ? AND idElection = ?";
                PreparedStatement districtStmt = conn.prepareStatement(districtSql);
                districtStmt.setInt(1, districtId);
                districtStmt.setInt(2, electionId);
                ResultSet districtRs = districtStmt.executeQuery();

                if (!districtRs.next()) {
                    sendResponse(exchange, 404, "{\"status\":\"error\",\"message\":\"District not found\"}");
                    return;
                }

                int totalSeats = districtRs.getInt("noOfSeats");
                int districtTotalVotes = districtRs.getInt("validVotes");

                // 2. Get all party results and calculate total party votes
                String partySql = "SELECT id, idParty, votesGained FROM partyresults " +
                        "WHERE idDistrict = ? AND idElection = ?";
                PreparedStatement partyStmt = conn.prepareStatement(partySql);
                partyStmt.setInt(1, districtId);
                partyStmt.setInt(2, electionId);
                ResultSet partyRs = partyStmt.executeQuery();

                int totalPartyVotes = 0;
                List<PartyResult> allPartyResults = new ArrayList<>();

                while (partyRs.next()) {
                    int resultId = partyRs.getInt("id");
                    int partyId = partyRs.getInt("idParty");
                    int votesGained = partyRs.getInt("votesGained");
                    totalPartyVotes += votesGained;
                    allPartyResults.add(new PartyResult(resultId, partyId, votesGained));
                }

                // 3. Validate that district total votes matches sum of party votes
                if (districtTotalVotes != totalPartyVotes) {
                    sendResponse(exchange, 400,
                            String.format("{\"status\":\"error\",\"message\":\"Vote count mismatch. District has %d votes but parties sum to %d\"}",
                                    districtTotalVotes, totalPartyVotes));
                    return;
                }

                // 4. Calculate threshold (5% of total votes)
                int threshold = (int) (districtTotalVotes * 0.05);

                // 5. Filter qualified parties (those meeting threshold)
                List<PartyResult> qualifiedParties = new ArrayList<>();
                int maxVotes = 0;
                int bonusPartyId = -1;

                for (PartyResult party : allPartyResults) {
                    if (party.totalVotes > maxVotes) {
                        maxVotes = party.totalVotes;
                        bonusPartyId = party.partyId;
                    }

                    if (party.totalVotes >= threshold) {
                        qualifiedParties.add(party);
                    }
                }

                // Check if there are any qualified parties
                if (qualifiedParties.isEmpty()) {
                    sendResponse(exchange, 400, "{\"status\":\"error\",\"message\":\"No parties met the 5% threshold\"}");
                    return;
                }

                // 6. Calculate total votes from qualified parties
                int qualifiedTotalVotes = qualifiedParties.stream().mapToInt(p -> p.totalVotes).sum();

                // 7. Calculate votes per seat (excluding one bonus seat)
                double votesPerSeat = (double) qualifiedTotalVotes / (totalSeats - 1);

                // 8. Allocate seats to qualified parties
                Map<Integer, Integer> seatAllocations = new HashMap<>();
                Map<Integer, Integer> firstRound = new HashMap<>();
                Map<Integer, Integer> secondRound = new HashMap<>();

                int allocatedSeats = 0;

                // First pass: allocate integer seats
                for (PartyResult party : qualifiedParties) {
                    int seats = (int) (party.totalVotes / votesPerSeat);
                    seatAllocations.put(party.partyId, seats);
                    firstRound.put(party.partyId, seats);
                    allocatedSeats += seats;
                }

                // Second pass: allocate remaining seats by largest remainder
                if (allocatedSeats < totalSeats - 1) {
                    List<PartyRemainder> remainders = new ArrayList<>();
                    for (PartyResult party : qualifiedParties) {
                        double remainder = (party.totalVotes / votesPerSeat) - seatAllocations.get(party.partyId);
                        remainders.add(new PartyRemainder(party.partyId, remainder));
                    }

                    // Sort by largest remainder
                    remainders.sort((a, b) -> Double.compare(b.remainder, a.remainder));

                    int seatsToAllocate = (totalSeats - 1) - allocatedSeats;
                    for (int i = 0; i < seatsToAllocate && i < remainders.size(); i++) {
                        int partyId = remainders.get(i).partyId;
                        seatAllocations.put(partyId, seatAllocations.get(partyId) + 1);
                        secondRound.put(partyId, 1);
                    }
                }

                // 9. Add bonus seat to the party with maximum votes
                if (bonusPartyId != -1) {
                    seatAllocations.put(bonusPartyId, seatAllocations.getOrDefault(bonusPartyId, 0) + 1);
                }

                // 10. Update database with seat allocations
                String updateSql = "UPDATE partyresults SET firstRound=?, secondRound=?, totalSeats = ? " +
                        "WHERE idParty = ? AND idDistrict = ? AND idElection = ?";
                PreparedStatement updateStmt = conn.prepareStatement(updateSql);

                // Requery to get all party results with IDs
                partyRs = partyStmt.executeQuery();
                while (partyRs.next()) {
                    int partyId = partyRs.getInt("idParty");
                    int seats = seatAllocations.getOrDefault(partyId, 0);
                    int seat1 = firstRound.getOrDefault(partyId, 0);
                    int seat2 = secondRound.getOrDefault(partyId, 0);

                    updateStmt.setInt(1, seat1);
                    updateStmt.setInt(2, seat2);
                    updateStmt.setInt(3, seats);
                    updateStmt.setInt(4, partyId);
                    updateStmt.setInt(5, districtId);
                    updateStmt.setInt(6, electionId);
                    updateStmt.addBatch();
                }

                // 11. Update bonus party in district results
                String bonusSql = "UPDATE districtresults SET bonusPartyId = ? " +
                        "WHERE idDistrict = ? AND idElection = ?";
                PreparedStatement bonusStmt = conn.prepareStatement(bonusSql);
                bonusStmt.setInt(1, bonusPartyId);
                bonusStmt.setInt(2, districtId);
                bonusStmt.setInt(3, electionId);
                bonusStmt.executeUpdate();

                // 12. Update election results with total votes and leading party
                updateElectionResults(conn, electionId);

                // 13. Execute all updates
                updateStmt.executeBatch();
                conn.commit();

                // 14. Prepare response
                JSONObject response = new JSONObject();
                response.put("status", "success");
                response.put("bonusPartyId", bonusPartyId);

                JSONArray allocations = new JSONArray();
                for (PartyResult pr : allPartyResults) {
                    JSONObject allocJson = new JSONObject();
                    allocJson.put("partyId", pr.partyId);
                    allocJson.put("totalSeats", seatAllocations.getOrDefault(pr.partyId, 0));
                    allocations.put(allocJson);
                }
                response.put("seatAllocation", allocations);

                sendResponse(exchange, 200, response.toString());
            } catch (Exception e) {
                try {
                    if (conn != null) conn.rollback();
                } catch (Exception ex) {
                    // Ignore
                }
                sendResponse(exchange, 500, "{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
            } finally {
                try {
                    if (conn != null) conn.close();
                } catch (Exception e) {
                    // Ignore
                }
            }
        } else {
            sendResponse(exchange, 405, "{\"status\":\"error\",\"message\":\"Method not allowed\"}");
        }
    }

    private void updateElectionResults(Connection conn, int electionId) throws Exception {
        // Calculate total valid votes from all districts
        String totalVotesSql = "SELECT SUM(validVotes) as total FROM districtresults " +
                "WHERE idElection = ?";
        PreparedStatement totalVotesStmt = conn.prepareStatement(totalVotesSql);
        totalVotesStmt.setInt(1, electionId);
        ResultSet totalVotesRs = totalVotesStmt.executeQuery();

        int totalElectionVotes = 0;
        if (totalVotesRs.next()) {
            totalElectionVotes = totalVotesRs.getInt("total");
        }

        // Determine leading party (party with most seats across all districts)
        String leadingPartySql = "SELECT idParty, SUM(totalSeats) as totalSeats " +
                "FROM partyresults " +
                "WHERE idElection = ? " +
                "GROUP BY idParty " +
                "ORDER BY totalSeats DESC " +
                "LIMIT 1";
        PreparedStatement leadingPartyStmt = conn.prepareStatement(leadingPartySql);
        leadingPartyStmt.setInt(1, electionId);
        ResultSet leadingPartyRs = leadingPartyStmt.executeQuery();

        int leadingPartyId = -1;
        if (leadingPartyRs.next()) {
            leadingPartyId = leadingPartyRs.getInt("idParty");
        }

        // Update election results
        String updateElectionSql = "UPDATE electionresults SET totalVotes = ?, leadingPartyId = ? " +
                "WHERE idElection = ?";
        PreparedStatement updateElectionStmt = conn.prepareStatement(updateElectionSql);
        updateElectionStmt.setInt(1, totalElectionVotes);
        updateElectionStmt.setInt(2, leadingPartyId);
        updateElectionStmt.setInt(3, electionId);
        updateElectionStmt.executeUpdate();
    }

    private static class PartyResult {
        int resultId;
        int partyId;
        int totalVotes;

        PartyResult(int resultId, int partyId, int totalVotes) {
            this.resultId = resultId;
            this.partyId = partyId;
            this.totalVotes = totalVotes;
        }
    }

    private static class PartyRemainder {
        int partyId;
        double remainder;

        PartyRemainder(int partyId, double remainder) {
            this.partyId = partyId;
            this.remainder = remainder;
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