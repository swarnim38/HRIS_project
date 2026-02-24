package model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class Candidate {
    private String candidateId;
    private String name;
    private String roleApplied;
    private LocalDate applicationDate;
    private LocalDate offerDate; 
    private String status; 

    public Candidate(String candidateId, String name, String roleApplied, 
                     LocalDate applicationDate, LocalDate offerDate, String status) {
        this.candidateId = candidateId;
        this.name = name;
        this.roleApplied = roleApplied;
        this.applicationDate = applicationDate;
        this.offerDate = offerDate;
        this.status = status;
    }

    public long getCycleTimeDays() {
        if (this.offerDate == null) {
            return -1;
        }
        return ChronoUnit.DAYS.between(this.applicationDate, this.offerDate);
    }

    public String toCSVString() {
        String offerStr = (offerDate != null) ? offerDate.toString() : "PENDING";
        return String.format("%s,%s,%s,%s,%s,%s", 
                candidateId, name, roleApplied, applicationDate, offerStr, status);
    }
}