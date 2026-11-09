package org.example.drivesimui.model;

/**
 * Marker interface for objects that can be serialized to CSV and JSON.
 */
public interface DataExport {
    String toCSV();
    String toJSON();
}
