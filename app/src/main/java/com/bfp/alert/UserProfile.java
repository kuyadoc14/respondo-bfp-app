package com.bfp.alert;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class UserProfile implements Serializable {
    private String fullName = "";
    private String age = "";
    private String birthdate = "";
    private String address = "";
    private String phone = "";
    private String emergencyContact = "";
    private String emergencyContactName = "";
    private String allergies = "";
    private String bloodType = "";
    private String medicalConditions = "";
    private boolean privacyConsentAccepted = false;
    private String privacyConsentDate = "";

    public UserProfile() {}

    public String getFullName() {
        return fullName != null ? fullName : "";
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getAge() {
        return age != null ? age : "";
    }

    public void setAge(String age) {
        this.age = age;
    }

    public String getBirthdate() {
        return birthdate != null ? birthdate : "";
    }

    public void setBirthdate(String birthdate) {
        this.birthdate = birthdate;
    }

    public String getAddress() {
        return address != null ? address : "";
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhone() {
        return phone != null ? phone : "";
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmergencyContact() {
        return emergencyContact != null ? emergencyContact : "";
    }

    public void setEmergencyContact(String emergencyContact) {
        this.emergencyContact = emergencyContact;
    }

    public String getEmergencyContactName() {
        return emergencyContactName != null ? emergencyContactName : "";
    }

    public void setEmergencyContactName(String emergencyContactName) {
        this.emergencyContactName = emergencyContactName;
    }

    public String getAllergies() {
        return allergies != null ? allergies : "";
    }

    public void setAllergies(String allergies) {
        this.allergies = allergies;
    }

    public String getBloodType() {
        return bloodType != null ? bloodType : "";
    }

    public void setBloodType(String bloodType) {
        this.bloodType = bloodType;
    }

    public String getMedicalConditions() {
        return medicalConditions != null ? medicalConditions : "";
    }

    public void setMedicalConditions(String medicalConditions) {
        this.medicalConditions = medicalConditions;
    }

    public boolean isPrivacyConsentAccepted() {
        return privacyConsentAccepted;
    }

    public void setPrivacyConsentAccepted(boolean privacyConsentAccepted) {
        this.privacyConsentAccepted = privacyConsentAccepted;
    }

    public String getPrivacyConsentDate() {
        return privacyConsentDate != null ? privacyConsentDate : "";
    }

    public void setPrivacyConsentDate(String privacyConsentDate) {
        this.privacyConsentDate = privacyConsentDate;
    }

    public boolean isEmpty() {
        return getFullName().trim().isEmpty() &&
               getPhone().trim().isEmpty() &&
               getAddress().trim().isEmpty() &&
               getEmergencyContact().trim().isEmpty();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("fullName", getFullName());
        map.put("age", getAge());
        map.put("birthdate", getBirthdate());
        map.put("address", getAddress());
        map.put("phone", getPhone());
        map.put("emergencyContact", getEmergencyContact());
        map.put("emergencyContactName", getEmergencyContactName());
        map.put("allergies", getAllergies());
        map.put("bloodType", getBloodType());
        map.put("medicalConditions", getMedicalConditions());
        map.put("privacyConsentAccepted", isPrivacyConsentAccepted());
        map.put("privacyConsentDate", getPrivacyConsentDate());
        return map;
    }

    public static UserProfile fromMap(Map<String, Object> map) {
        UserProfile p = new UserProfile();
        if (map == null) return p;
        if (map.get("fullName") != null) p.setFullName(String.valueOf(map.get("fullName")));
        if (map.get("age") != null) p.setAge(String.valueOf(map.get("age")));
        if (map.get("birthdate") != null) p.setBirthdate(String.valueOf(map.get("birthdate")));
        if (map.get("address") != null) p.setAddress(String.valueOf(map.get("address")));
        if (map.get("phone") != null) p.setPhone(String.valueOf(map.get("phone")));
        if (map.get("emergencyContact") != null) p.setEmergencyContact(String.valueOf(map.get("emergencyContact")));
        if (map.get("emergencyContactName") != null) p.setEmergencyContactName(String.valueOf(map.get("emergencyContactName")));
        if (map.get("allergies") != null) p.setAllergies(String.valueOf(map.get("allergies")));
        if (map.get("bloodType") != null) p.setBloodType(String.valueOf(map.get("bloodType")));
        if (map.get("medicalConditions") != null) p.setMedicalConditions(String.valueOf(map.get("medicalConditions")));
        if (map.get("privacyConsentAccepted") != null) {
            p.setPrivacyConsentAccepted(Boolean.TRUE.equals(map.get("privacyConsentAccepted")));
        }
        if (map.get("privacyConsentDate") != null) {
            p.setPrivacyConsentDate(String.valueOf(map.get("privacyConsentDate")));
        }
        return p;
    }
}
