package org.election.model;

public class PartyResults {
    private int id;
    private int idDistrict;
    private int idParty;
    private int firstRound;
    private int secondRound;
    private int totalSeats;
    private int idElection;

    public PartyResults() {}

    public PartyResults(int id, int idDistrict, int idParty, int firstRound,
                        int secondRound, int totalSeats, int idElection) {
        this.id = id;
        this.idDistrict = idDistrict;
        this.idParty = idParty;
        this.firstRound = firstRound;
        this.secondRound = secondRound;
        this.totalSeats = totalSeats;
        this.idElection = idElection;
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getIdDistrict() { return idDistrict; }
    public void setIdDistrict(int idDistrict) { this.idDistrict = idDistrict; }
    public int getIdParty() { return idParty; }
    public void setIdParty(int idParty) { this.idParty = idParty; }
    public int getFirstRound() { return firstRound; }
    public void setFirstRound(int firstRound) { this.firstRound = firstRound; }
    public int getSecondRound() { return secondRound; }
    public void setSecondRound(int secondRound) { this.secondRound = secondRound; }
    public int getTotalSeats() { return totalSeats; }
    public void setTotalSeats(int totalSeats) { this.totalSeats = totalSeats; }
    public int getIdElection() { return idElection; }
    public void setIdElection(int idElection) { this.idElection = idElection; }
}