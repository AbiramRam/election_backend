package org.election.model;

public class District {
    private int id;
    private String districtName;
    private int idProvince;

    public District() {}

    public District(int id, String districtName, int idProvince) {
        this.id = id;
        this.districtName = districtName;
        this.idProvince = idProvince;
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getDistrictName() { return districtName; }
    public void setDistrictName(String districtName) { this.districtName = districtName; }
    public int getIdProvince() { return idProvince; }
    public void setIdProvince(int idProvince) { this.idProvince = idProvince; }
}