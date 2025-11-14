package com.allossimulator.model.enums;

public enum Priority {
    IDLE("Idle", "Lowest priority for background tasks", 0),
    LOW("Low", "Low priority", 1),
    NORMAL("Normal", "Normal priority", 2),
    HIGH("High", "High priority", 3),
    REALTIME("Real-time", "Highest priority for real-time tasks", 4);
    
    private final String displayName;
    private final String description;
    private final int value;
    
    Priority(String displayName, String description, int value) {
        this.displayName = displayName;
        this.description = description;
        this.value = value;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public int getValue() {
        return value;
    }
}