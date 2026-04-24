package com.opslens.model;


import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class LogItem {

    @Id
    // Generate the ID automatically when a new row is inserted
    // when you create a new obj, it assign a new id automatically
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String timestamp;
    private String level;
    private String service;
    private String message;

    //
    public LogItem() {

    }

    public LogItem(String timestamp, String level, String service, String message) {
        this.timestamp = timestamp;
        this.level = level;
        this.service = service;
        this.message = message;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getLevel() {
        return level;
    }

    public String getService() {
        return service;
    }

    public String getMessage() {
        return message;
    }
}
