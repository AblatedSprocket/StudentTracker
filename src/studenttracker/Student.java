/* 
 * Copyright 2016 Andrew Burch.
 *
 * This software is not available for distribution under any license.
 */
package studenttracker;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author Andrew Burch
 */
public class Student {

    public Student(String name) {
        this.fullName = name;
        this.firstName = fullName.split(" ")[0];
        this.lastName = fullName.split(" ")[1];
        this.walkInDates = new StringBuilder("");
    }

    public void setStartInd() {
        System.out.println("indStart is being modified!");
        this.indStartDate = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("MM-dd-yyyy"));
    }

    public void setStartInd(String date) {
        System.out.println("indStart is being modified!");
        this.indStartDate = date;
    }

    public void setEndInd() {
        System.out.println("indEnd is being modified!");
        this.indEndDate = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("MM-dd-yyyy"));
    }

    public void setEndInd(String date) {
        System.out.println("indEnd is being modified!");
        this.indEndDate = date;
    }

    public void setStartGroup() {
        this.groupStartDate = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("MM-dd-yyyy"));
    }

    public void setStartGroup(String date) {
        this.groupStartDate = date;
    }

    public void setEndGroup() {
        this.groupEndDate = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("MM-dd-yyyy"));
    }

    public void setEndGroup(String date) {
        this.groupEndDate = date;
    }

    public void setStartCheckIn() {
        this.checkInStartDate = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("MM-dd-yyyy"));
    }

    public void setStartCheckIn(String date) {
        this.checkInStartDate = date;
    }

    public void setEndCheckIn() {
        this.checkInEndDate = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("MM-dd-yyyy"));
    }

    public void setEndCheckIn(String date) {
        this.checkInEndDate = date;
    }

    public void addWalkIn() {
        if (!this.walkInDates.toString().equals("")) {
            this.walkInDates.append(";");
        }
        this.walkInDates.append(LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("MM-dd-yyyy")));
    }

    public void addWalkIn(String date) {
        if (!this.walkInDates.toString().equals("")) {
            this.walkInDates.append(";");
        }
        this.walkInDates.append(date);
    }

    public void setWalkIns(String dates) {
        this.walkInDates = new StringBuilder(dates);
    }

    public void addForms(String form) {
        if (!this.forms.toString().equals("")) {
            this.forms.append(";");
        }
        this.forms.append(form);
    }

    public void removeForm(String form) {
        System.out.println("Form to remove: '" + form + "'");
        List<String> formsList = Arrays.asList(this.forms.toString().split(";"));
        List<String> formsArray = new ArrayList<>();
        formsArray.addAll(formsList);
        formsArray.remove(form);
        if (formsArray.isEmpty()) {
            this.forms = new StringBuilder("");
        }else {
            this.forms = new StringBuilder(formsArray.get(0));
            for (int i = 1; i < formsArray.size(); i++) {
                this.forms.append(";");
                this.forms.append(formsArray.get(i));
            }
        }
    }

    public void setForms(String forms) {
        this.forms = new StringBuilder(forms);
    }

    public void setNotes(String notes) {
        this.notes = new StringBuilder(notes);
    }

    public void toggleIEP() {
        this.hasIEP ^= true;
        System.out.println("Toggled IEP!");
    }

    public void setIEP(Boolean has) {
        this.hasIEP = has;
        System.out.println("Set IEP!");
    }

    public void toggle504() {
        this.has504 ^= true;
        System.out.println("Toggled 504!");
    }

    public void set504(Boolean has) {
        this.has504 = has;
        System.out.println("Set 504!");
    }

    public void toggleEval() {
        this.hasEval ^= true;
        System.out.println("Toggled eval!!");
    }

    public void setEval(Boolean has) {
        this.hasEval = has;
        System.out.println("Set eval!");
    }

    public String getFullName() {
        return this.fullName;
    }

    public String getFirstName() {
        return this.firstName;
    }

    public String getLastName() {
        return this.lastName;
    }

    public String getStartInd() {
        return this.indStartDate;
    }

    public String getEndInd() {
        return this.indEndDate;
    }

    public String getStartGroup() {
        return this.groupStartDate;
    }

    public String getEndGroup() {
        return this.groupEndDate;
    }

    public String getStartCheckIn() {
        return this.checkInStartDate;
    }

    public String getEndCheckIn() {
        return this.checkInEndDate;
    }

    public String getWalkIns() {
        return this.walkInDates.toString();

    }

    public String getForms() {
        return this.forms.toString();
    }

    public String getNotes() {
        return this.notes.toString();
    }

    public boolean getIEP() {
        return this.hasIEP;
    }

    public boolean get504() {
        return this.has504;
    }

    public boolean getEval() {
        return this.hasEval;
    }

    private final String firstName;
    private final String lastName;
    private final String fullName;
    private String indStartDate = null;
    private String indEndDate = null;
    private String groupStartDate = null;
    private String groupEndDate = null;
    private String checkInStartDate = null;
    private String checkInEndDate = null;
    private StringBuilder walkInDates = new StringBuilder("");
    private StringBuilder forms = new StringBuilder("");
    private StringBuilder notes = new StringBuilder("");
    private boolean hasIEP = false;
    private boolean has504 = false;
    private boolean hasEval = false;
}
