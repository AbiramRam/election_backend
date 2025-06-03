package org.election.model;

public class Election {
    private int id;
    private int electionYear;
    private int noOfProvinces;
    private int totalSeats;
    private int totalVotes;
    private int leadingPartyId;

    public Election() {}

    public Election(int id, int electionYear, int noOfProvinces, int totalSeats, int totalVotes, int leadingPartyId) {
        this.id = id;
        this.electionYear = electionYear;
        this.noOfProvinces = noOfProvinces;
        this.totalSeats = totalSeats;
        this.totalVotes = totalVotes;
        this.leadingPartyId = leadingPartyId;
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getElectionYear() { return electionYear; }
    public void setElectionYear(int electionYear) { this.electionYear = electionYear; }
    public int getNoOfProvinces() { return noOfProvinces; }
    public void setNoOfProvinces(int noOfProvinces) { this.noOfProvinces = noOfProvinces; }
    public int getTotalSeats() { return totalSeats; }
    public void setTotalSeats(int totalSeats) { this.totalSeats = totalSeats; }
    public int getTotalVotes() { return totalVotes; }
    public void setTotalVotes(int totalVotes) { this.totalVotes = totalVotes; }
    public int getLeadingPartyId() { return leadingPartyId; }
    public void setLeadingPartyId(int leadingPartyId) { this.leadingPartyId = leadingPartyId; }
}