package com.allossimulator.model.enums;

public enum ProcessState {
    CREATED("Created", "Process has been created but not started"),
    READY("Ready", "Process is ready to run"),
    RUNNING("Running", "Process is currently executing"),
    BLOCKED("Blocked", "Process is blocked waiting for resources"),
    SUSPENDED("Suspended", "Process execution is suspended"),
    TERMINATED("Terminated", "Process has completed execution"),
    KILLED("Killed", "Process was forcefully terminated"),
    ZOMBIE("Zombie", "Process has terminated but not reaped by parent");
    
    private final String displayName;
    private final String description;
    
    ProcessState(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public boolean isActive() {
        return this == READY || this == RUNNING || this == BLOCKED || this == SUSPENDED;
    }
    
    public boolean canTransitionTo(ProcessState newState) {
        switch (this) {
            case CREATED:
                return newState == READY;
            case READY:
                return newState == RUNNING || newState == SUSPENDED || newState == TERMINATED;
            case RUNNING:
                return newState == READY || newState == BLOCKED || newState == SUSPENDED || 
                       newState == TERMINATED || newState == KILLED;
            case BLOCKED:
                return newState == READY || newState == SUSPENDED || newState == TERMINATED;
            case SUSPENDED:
                return newState == READY || newState == TERMINATED;
            case TERMINATED:
            case KILLED:
                return newState == ZOMBIE;
            case ZOMBIE:
                return false;
            default:
                return false;
        }
    }
}