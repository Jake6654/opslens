package com.opslens.model;


import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;

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
    @Column(columnDefinition = "TEXT")
    private String message;

    private String project;
    private String environment;

    //
    public LogItem() {

    }

    public LogItem(String environment, String project, String service, String level, String timestamp, String message) {
        this.environment = environment;
        this.project = project;
        this.service = service;
        this.level = level;
        this.timestamp = timestamp;
        this.message = message;
    }

    public Long getId() {
        return id;
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

    public String getProject() {
        return project;
    }

    public String getEnvironment() {
        return environment;
    }

}








