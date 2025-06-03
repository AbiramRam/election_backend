package org.election.model;

public class Province {
    private int id;
    private String provinceName;
    private int noOfDistricts;

    public Province() {}

    public Province(int id, String provinceName, int noOfDistricts) {
        this.id = id;
        this.provinceName = provinceName;
        this.noOfDistricts = noOfDistricts;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getProvinceName() { return provinceName; }
    public void setProvinceName(String provinceName) { this.provinceName = provinceName; }
    public int getNoOfDistricts() { return noOfDistricts; }
    public void setNoOfDistricts(int noOfDistricts) { this.noOfDistricts = noOfDistricts; }
}