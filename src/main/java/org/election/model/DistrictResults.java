package org.election.model;

public class DistrictResults {
    private int id;
    private int idDistrict;
    private int idElection;
    private int noOfSeats;
    private int totalVotes;
    private int rejectedVotes;
    private int validVotes;
    private int bonusPartyId;

    public DistrictResults() {}

    public DistrictResults(int id, int idDistrict, int idElection, int noOfSeats, int totalVotes,
                           int rejectedVotes, int validVotes, int bonusPartyId) {
        this.id = id;
        this.idDistrict = idDistrict;
        this.idElection = idElection;
        this.noOfSeats = noOfSeats;
        this.totalVotes = totalVotes;
        this.rejectedVotes = rejectedVotes;
        this.validVotes = validVotes;
        this.bonusPartyId = bonusPartyId;
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getIdDistrict() { return idDistrict; }
    public void setIdDistrict(int idDistrict) { this.idDistrict = idDistrict; }
    public int getIdElection() { return idElection; }
    public void setIdElection(int idElection) { this.idElection = idElection; }
    public int getNoOfSeats() { return noOfSeats; }
    public void setNoOfSeats(int noOfSeats) { this.noOfSeats = noOfSeats; }
    public int getTotalVotes() { return totalVotes; }
    public void setTotalVotes(int totalVotes) { this.totalVotes = totalVotes; }
    public int getRejectedVotes() { return rejectedVotes; }
    public void setRejectedVotes(int rejectedVotes) { this.rejectedVotes = rejectedVotes; }
    public int getValidVotes() { return validVotes; }
    public void setValidVotes(int validVotes) { this.validVotes = validVotes; }
    public int getBonusPartyId() { return bonusPartyId; }
    public void setBonusPartyId(int bonusPartyId) { this.bonusPartyId = bonusPartyId; }
}