package com.allossimulator.model.process;

import com.allossimulator.model.enums.Priority;
import com.allossimulator.model.enums.ProcessState;

public class Process {
    private final int pid;
    private final String name;
    private final String executablePath;
    private ProcessState state;
    private Priority priority;
    private int niceValue; // Unix nice value (-20 to 19)
    private long cpuTime; // CPU time in milliseconds
    private long memoryUsage; // Memory usage in bytes
    private long startTime;
    private long executionTime; // Total execution time
    private int parentPid;
    private String owner;
    
    public Process(int pid, String name, String executablePath) {
        this.pid = pid;
        this.name = name;
        this.executablePath = executablePath;
        this.state = ProcessState.CREATED;
        this.priority = Priority.NORMAL;
        this.niceValue = 0; // Default nice value
        this.cpuTime = 0;
        this.memoryUsage = 0;
        this.startTime = System.currentTimeMillis();
        this.executionTime = 0;
        this.parentPid = 0; // Default to no parent
        this.owner = "system";
    }
    
    // Getters
    public int getPid() {
        return pid;
    }
    
    public String getName() {
        return name;
    }
    
    public String getExecutablePath() {
        return executablePath;
    }
    
    public ProcessState getState() {
        return state;
    }
    
    public Priority getPriority() {
        return priority;
    }
    
    public int getNiceValue() {
        return niceValue;
    }
    
    public long getCpuTime() {
        return cpuTime;
    }
    
    public long getMemoryUsage() {
        return memoryUsage;
    }
    
    public long getStartTime() {
        return startTime;
    }
    
    public long getExecutionTime() {
        return executionTime;
    }
    
    public int getParentPid() {
        return parentPid;
    }
    
    public String getOwner() {
        return owner;
    }
    
    // Setters
    public void setState(ProcessState state) {
        this.state = state;
    }
    
    public void setPriority(Priority priority) {
        this.priority = priority;
    }
    
    public void setNiceValue(int niceValue) {
        // Ensure nice value is within Unix range (-20 to 19)
        this.niceValue = Math.max(-20, Math.min(19, niceValue));
    }
    
    public void incrementCpuTime() {
        this.cpuTime++;
    }
    
    public void setMemoryUsage(long memoryUsage) {
        this.memoryUsage = memoryUsage;
    }
    
    public void setParentPid(int parentPid) {
        this.parentPid = parentPid;
    }
    
    public void setOwner(String owner) {
        this.owner = owner;
    }
    
    // Business methods
    public void incrementCpuTime(long amount) {
        this.cpuTime += amount;
    }
    
    public void incrementExecutionTime(long amount) {
        this.executionTime += amount;
    }
    
    public boolean isActive() {
        return state.isActive();
    }
    
    public boolean isTerminated() {
        return state == ProcessState.TERMINATED || 
               state == ProcessState.KILLED || 
               state == ProcessState.ZOMBIE;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        Process process = (Process) obj;
        return pid == process.pid;
    }
    
    @Override
    public int hashCode() {
        return pid;
    }
    
    @Override
    public String toString() {
        return String.format("Process{pid=%d, name='%s', state=%s, priority=%s}", 
                           pid, name, state, priority);
    }
}