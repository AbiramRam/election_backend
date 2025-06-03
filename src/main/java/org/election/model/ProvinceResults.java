package org.election.model;

public class ProvinceResults {
    private int id;
    private int idElection;
    private int idProvince;
    private int noOfDistricts;

    public ProvinceResults() {}

    public ProvinceResults(int id, int idElection, int idProvince, int noOfDistricts) {
        this.id = id;
        this.idElection = idElection;
        this.idProvince = idProvince;
        this.noOfDistricts = noOfDistricts;
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getIdElection() { return idElection; }
    public void setIdElection(int idElection) { this.idElection = idElection; }
    public int getIdProvince() { return idProvince; }
    public void setIdProvince(int idProvince) { this.idProvince = idProvince; }
    public int getNoOfDistricts() { return noOfDistricts; }
    public void setNoOfDistricts(int noOfDistricts) { this.noOfDistricts = noOfDistricts; }
}