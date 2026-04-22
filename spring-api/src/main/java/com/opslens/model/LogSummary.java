package com.opslens.model;

/**
 *  LogSummary represents aggregated data about many logs
 */
public class LogSummary {


    private int totalLogs;
    // Why long? Lamda's terminal operation count() returns long
    private long errorCount;
    private long warnCount;
    private long infoCount;


    public LogSummary(int totalLogs, long errorCount, long warnCount, long infoCount) {
        this.totalLogs = totalLogs;
        this.errorCount = errorCount;
        this.warnCount = warnCount;
        this.infoCount = infoCount;
    }

    public int getTotalLogs() {
        return totalLogs;
    }

    public long getWarnCount() {
        return warnCount;
    }

    public long getErrorCount() {
        return errorCount;
    }

    public long getInfoCount() {
        return infoCount;
    }
}
