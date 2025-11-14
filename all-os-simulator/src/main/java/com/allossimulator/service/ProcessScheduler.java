package com.allossimulator.service;

import com.allossimulator.model.process.Process;
import com.allossimulator.model.process.ProcessState;
import com.allossimulator.model.enums.Priority;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class ProcessScheduler {
    
    // Scheduling algorithm types
    public enum SchedulingAlgorithm {
        ROUND_ROBIN,
        PRIORITY,
        SHORTEST_JOB_FIRST,
        MULTILEVEL_FEEDBACK,
        COMPLETELY_FAIR,
        REAL_TIME
    }
    
    private SchedulingAlgorithm algorithm = SchedulingAlgorithm.COMPLETELY_FAIR;
    private final int timeQuantum = 100; // milliseconds
    
    // Process queues by priority
    private final Map<Priority, Queue<Process>> priorityQueues;
    private final Queue<Process> readyQueue;
    private final Map<Integer, Process> runningProcesses;
    private final Set<Process> blockedProcesses;
    
    // CFS (Completely Fair Scheduler) structures
    private final TreeMap<Long, Process> cfsRedBlackTree;
    private final Map<Process, Long> virtualRuntime;
    
    // Real-time process queues
    private final PriorityQueue<Process> realtimeQueue;
    
    // Statistics
    private final Map<Integer, SchedulerStatistics> processStats;
    private final AtomicInteger contextSwitches = new AtomicInteger(0);
    
    private final ScheduledExecutorService schedulerExecutor;
    private final ReentrantLock schedulerLock = new ReentrantLock();
    
    private Process currentProcess;
    private long currentQuantumStart;
    
    public ProcessScheduler() {
        priorityQueues = new ConcurrentHashMap<>();
        for (Priority priority : Priority.values()) {
            priorityQueues.put(priority, new ConcurrentLinkedQueue<>());
        }
        
        readyQueue = new ConcurrentLinkedQueue<>();
        runningProcesses = new ConcurrentHashMap<>();
        blockedProcesses = ConcurrentHashMap.newKeySet();
        
        cfsRedBlackTree = new TreeMap<>();
        virtualRuntime = new ConcurrentHashMap<>();
        
        realtimeQueue = new PriorityQueue<>(Comparator.comparingInt(Process::getNiceValue));
        
        processStats = new ConcurrentHashMap<>();
        
        schedulerExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ProcessScheduler");
            t.setPriority(Thread.MAX_PRIORITY);
            return t;
        });
        
        startScheduler();
    }
    
    private void startScheduler() {
        schedulerExecutor.scheduleAtFixedRate(this::schedule, 0, 10, TimeUnit.MILLISECONDS);
    }
    
    public void addProcess(Process process) {
        schedulerLock.lock();
        try {
            process.setState(ProcessState.READY);
            
            // Initialize statistics
            processStats.put(process.getPid(), new SchedulerStatistics(process.getPid()));
            
            // Add to appropriate queue based on algorithm
            switch (algorithm) {
                case COMPLETELY_FAIR:
                    addToCFS(process);
                    break;
                case PRIORITY:
                    priorityQueues.get(process.getPriority()).offer(process);
                    break;
                case REAL_TIME:
                    if (process.getPriority() == Priority.REALTIME) {
                        realtimeQueue.offer(process);
                    } else {
                        readyQueue.offer(process);
                    }
                    break;
                default:
                    readyQueue.offer(process);
            }
            
            log.debug("Added process {} (PID {}) to scheduler", process.getName(), process.getPid());
            
        } finally {
            schedulerLock.unlock();
        }
    }
    
    public void removeProcess(Process process) {
        schedulerLock.lock();
        try {
            // Remove from all queues
            readyQueue.remove(process);
            priorityQueues.values().forEach(queue -> queue.remove(process));
            runningProcesses.remove(process.getPid());
            blockedProcesses.remove(process);
            
            // Remove from CFS
            if (virtualRuntime.containsKey(process)) {
                long vruntime = virtualRuntime.remove(process);
                cfsRedBlackTree.remove(vruntime);
            }
            
            realtimeQueue.remove(process);
            
            // Log statistics
            SchedulerStatistics stats = processStats.remove(process.getPid());
            if (stats != null) {
                log.info("Process {} (PID {}) removed - Total CPU time: {}ms, Context switches: {}",
                        process.getName(), process.getPid(), 
                        stats.totalCpuTime, stats.contextSwitchCount);
            }
            
        } finally {
            schedulerLock.unlock();
        }
    }
    
    private void schedule() {
        schedulerLock.lock();
        try {
            // Check if current process quantum expired
            if (currentProcess != null && 
                System.currentTimeMillis() - currentQuantumStart > timeQuantum) {
                preemptCurrentProcess();
            }
            
            // Select next process based on algorithm
            Process nextProcess = selectNextProcess();
            
            if (nextProcess != null && nextProcess != currentProcess) {
                performContextSwitch(currentProcess, nextProcess);
            }
            
            // Update statistics
            updateStatistics();
            
        } finally {
            schedulerLock.unlock();
        }
    }
    
    private Process selectNextProcess() {
        Process selected = null;
        
        switch (algorithm) {
            case COMPLETELY_FAIR:
                selected = selectCFS();
                break;
                
            case ROUND_ROBIN:
                selected = readyQueue.poll();
                if (selected != null) {
                    readyQueue.offer(selected); // Re-add to end of queue
                }
                break;
                
            case PRIORITY:
                for (Priority priority : Priority.values()) {
                    Queue<Process> queue = priorityQueues.get(priority);
                    if (!queue.isEmpty()) {
                        selected = queue.poll();
                        queue.offer(selected); // Re-add for round-robin within priority
                        break;
                    }
                }
                break;
                
            case SHORTEST_JOB_FIRST:
                selected = selectShortestJob();
                break;
                
            case MULTILEVEL_FEEDBACK:
                selected = selectMultilevelFeedback();
                break;
                
            case REAL_TIME:
                selected = realtimeQueue.peek();
                if (selected == null) {
                    selected = readyQueue.poll();
                    if (selected != null) {
                        readyQueue.offer(selected);
                    }
                }
                break;
        }
        
        return selected;
    }
    
    private Process selectCFS() {
        if (cfsRedBlackTree.isEmpty()) {
            return null;
        }
        
        // Select process with minimum virtual runtime
        Map.Entry<Long, Process> entry = cfsRedBlackTree.firstEntry();
        if (entry != null) {
            Process process = entry.getValue();
            
            // Update virtual runtime
            long vruntime = virtualRuntime.get(process);
            long newVruntime = vruntime + calculateVruntimeIncrement(process);
            
            // Remove and re-add with new vruntime
            cfsRedBlackTree.remove(vruntime);
            virtualRuntime.put(process, newVruntime);
            cfsRedBlackTree.put(newVruntime, process);
            
            return process;
        }
        
        return null;
    }
    
    private long calculateVruntimeIncrement(Process process) {
        // Calculate based on nice value and priority
        int niceFactor = 20 - process.getNiceValue(); // Nice ranges from -20 to 19
        long baseIncrement = timeQuantum;
        
        // Adjust based on priority
        switch (process.getPriority()) {
            case REALTIME:
                baseIncrement /= 4;
                break;
            case HIGH:
                baseIncrement /= 2;
                break;
            case LOW:
                baseIncrement *= 2;
                break;
            case IDLE:
                baseIncrement *= 4;
                break;
        }
        
        return baseIncrement * niceFactor / 20;
    }
    
    private void addToCFS(Process process) {
        // Initialize virtual runtime
        long vruntime = cfsRedBlackTree.isEmpty() ? 0 : 
                       Collections.min(virtualRuntime.values());
        
        virtualRuntime.put(process, vruntime);
        cfsRedBlackTree.put(vruntime, process);
    }
    
    private Process selectShortestJob() {
        // Estimate remaining time based on history
        Process shortest = null;
        long shortestTime = Long.MAX_VALUE;
        
        for (Process process : readyQueue) {
            SchedulerStatistics stats = processStats.get(process.getPid());
            if (stats != null) {
                long estimatedTime = stats.averageBurstTime;
                if (estimatedTime < shortestTime) {
                    shortest = process;
                    shortestTime = estimatedTime;
                }
            }
        }
        
        return shortest != null ? shortest : readyQueue.peek();
    }
    
    private Process selectMultilevelFeedback() {
        // Implement aging to prevent starvation
        for (Process process : readyQueue) {
            SchedulerStatistics stats = processStats.get(process.getPid());
            if (stats != null && stats.waitingTime > 1000) { // 1 second
                // Promote to higher priority
                Priority current = process.getPriority();
                if (current != Priority.REALTIME && current != Priority.HIGH) {
                    process.setPriority(Priority.values()[current.ordinal() - 1]);
                    stats.waitingTime = 0;
                }
            }
        }
        
        // Select from highest priority non-empty queue
        for (Priority priority : Priority.values()) {
            Queue<Process> queue = priorityQueues.get(priority);
            if (!queue.isEmpty()) {
                return queue.poll();
            }
        }
        
        return null;
    }
    
    private void performContextSwitch(Process oldProcess, Process newProcess) {
        log.debug("Context switch: {} -> {}", 
                 oldProcess != null ? oldProcess.getPid() : "null",
                 newProcess.getPid());
        
        // Save old process state
        if (oldProcess != null) {
            oldProcess.setState(ProcessState.READY);
            runningProcesses.remove(oldProcess.getPid());
            
            SchedulerStatistics stats = processStats.get(oldProcess.getPid());
            if (stats != null) {
                stats.contextSwitchCount++;
                long burstTime = System.currentTimeMillis() - currentQuantumStart;
                stats.updateBurstTime(burstTime);
            }
        }
        
        // Load new process state
        newProcess.setState(ProcessState.RUNNING);
        runningProcesses.put(newProcess.getPid(), newProcess);
        currentProcess = newProcess;
        currentQuantumStart = System.currentTimeMillis();
        
        // Update global statistics
        contextSwitches.incrementAndGet();
        
        // Update process statistics
        SchedulerStatistics stats = processStats.get(newProcess.getPid());
        if (stats != null) {
            stats.lastScheduledTime = currentQuantumStart;
        }
    }
    
    private void preemptCurrentProcess() {
        if (currentProcess != null) {
            log.debug("Preempting process {} (PID {})", 
                     currentProcess.getName(), currentProcess.getPid());
            
            // Move to ready queue
            currentProcess.setState(ProcessState.READY);
            
            // Re-add to scheduler
            addProcess(currentProcess);
            
            currentProcess = null;
        }
    }
    
    public void blockProcess(Process process) {
        schedulerLock.lock();
        try {
            process.setState(ProcessState.BLOCKED);
            blockedProcesses.add(process);
            
            // Remove from ready queues
            readyQueue.remove(process);
            priorityQueues.values().forEach(queue -> queue.remove(process));
            
            if (currentProcess == process) {
                currentProcess = null;
            }
            
            log.debug("Process {} (PID {}) blocked", process.getName(), process.getPid());
            
        } finally {
            schedulerLock.unlock();
        }
    }
    
    public void unblockProcess(Process process) {
        schedulerLock.lock();
        try {
            if (blockedProcesses.remove(process)) {
                process.setState(ProcessState.READY);
                addProcess(process);
                
                log.debug("Process {} (PID {}) unblocked", process.getName(), process.getPid());
            }
            
        } finally {
            schedulerLock.unlock();
        }
    }
    
    private void updateStatistics() {
        long currentTime = System.currentTimeMillis();
        
        // Update waiting time for ready processes
        for (Process process : readyQueue) {
            if (process.getState() == ProcessState.READY) {
                SchedulerStatistics stats = processStats.get(process.getPid());
                if (stats != null) {
                    stats.waitingTime += 10; // Scheduler runs every 10ms
                }
            }
        }
        
        // Update CPU time for running process
        if (currentProcess != null) {
            SchedulerStatistics stats = processStats.get(currentProcess.getPid());
            if (stats != null) {
                stats.totalCpuTime += 10;
            }
            currentProcess.incrementCpuTime();
        }
    }
    
    public Map<Integer, SchedulerStatistics> getStatistics() {
        return new HashMap<>(processStats);
    }
    
    public void setAlgorithm(SchedulingAlgorithm algorithm) {
        this.algorithm = algorithm;
        log.info("Scheduling algorithm changed to: {}", algorithm);
    }
    
    public static class SchedulerStatistics {
        private final int pid;
        private long totalCpuTime = 0;
        private long waitingTime = 0;
        private long turnaroundTime = 0;
        private int contextSwitchCount = 0;
        private long lastScheduledTime = 0;
        private long averageBurstTime = 0;
        private int burstCount = 0;
        
        public SchedulerStatistics(int pid) {
            this.pid = pid;
        }
        
        public void updateBurstTime(long burstTime) {
            averageBurstTime = (averageBurstTime * burstCount + burstTime) / (burstCount + 1);
            burstCount++;
        }
    }
    
    public void shutdown() {
        schedulerExecutor.shutdown();
        try {
            if (!schedulerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                schedulerExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            schedulerExecutor.shutdownNow();
        }
    }
}