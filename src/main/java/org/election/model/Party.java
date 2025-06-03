package org.election.model;

public class Party {
    private int id;
    private String partyName;
    private byte[] partyLogo;

    public Party() {}

    public Party(int id, String partyName, byte[] partyLogo) {
        this.id = id;
        this.partyName = partyName;
        this.partyLogo = partyLogo;
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getPartyName() { return partyName; }
    public void setPartyName(String partyName) { this.partyName = partyName; }
    public byte[] getPartyLogo() { return partyLogo; }
    public void setPartyLogo(byte[] partyLogo) { this.partyLogo = partyLogo; }
}