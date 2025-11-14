package com.allossimulator.model.os;

import com.allossimulator.core.kernel.Kernel;
import com.allossimulator.model.process.Process;
import com.allossimulator.model.memory.Memory;
import com.allossimulator.model.filesystem.FileSystem;
import com.allossimulator.model.device.Device;
import com.allossimulator.model.user.User;
import com.allossimulator.model.settings.SystemSettings;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Data
@Entity
@Table(name = "operating_systems")
public abstract class OperatingSystem {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false)
    private String version;
    
    @Enumerated(EnumType.STRING)
    private OSType type;
    
    @Enumerated(EnumType.STRING)
    private OSState state = OSState.SHUTDOWN;
    
    @Transient
    protected Kernel kernel;
    
    @Transient
    protected Memory memory;
    
    @Transient
    protected FileSystem fileSystem;
    
    @Transient
    protected Map<Integer, Process> processes = new ConcurrentHashMap<>();
    
    @Transient
    protected Map<String, Device> devices = new ConcurrentHashMap<>();
    
    @Transient
    protected List<User> users = new ArrayList<>();
    
    @Transient
    protected SystemSettings settings;
    
    @Column(name = "boot_time")
    private LocalDateTime bootTime;
    
    @Column(name = "uptime")
    private Long uptime = 0L;
    
    @Transient
    private AtomicInteger nextPid = new AtomicInteger(1);
    
    @Transient
    private AtomicLong systemClock = new AtomicLong(0);
    
    @Transient
    protected Timer systemTimer;
    
    // Abstract methods that each OS must implement
    public abstract void initializeKernel();
    public abstract void loadSystemServices();
    public abstract void configureNetworking();
    public abstract void mountFileSystems();
    public abstract String getShellCommand();
    public abstract Map<String, String> getEnvironmentVariables();
    
    // Common OS operations
    public void boot() {
        log.info("Booting {} {}", name, version);
        setState(OSState.BOOTING);
        
        try {
            // Initialize boot time
            bootTime = LocalDateTime.now();
            
            // Initialize kernel
            kernel = new Kernel();
            kernel.initialize();
            initializeKernel();
            
            // Initialize memory
            memory = new Memory(getDefaultMemorySize());
            memory.initialize();
            
            // Initialize file system
            fileSystem = createFileSystem();
            mountFileSystems();
            
            // Load system services
            loadSystemServices();
            
            // Configure networking
            configureNetworking();
            
            // Initialize devices
            initializeDevices();
            
            // Create init process (PID 1)
            createInitProcess();
            
            // Start system timer
            startSystemTimer();
            
            setState(OSState.RUNNING);
            log.info("{} {} booted successfully", name, version);
            
        } catch (Exception e) {
            log.error("Boot failed for {} {}", name, version, e);
            setState(OSState.ERROR);
            throw new OSBootException("Failed to boot OS", e);
        }
    }
    
    public void shutdown() {
        log.info("Shutting down {} {}", name, version);
        setState(OSState.SHUTTING_DOWN);
        
        try {
            // Stop all user processes
            stopAllProcesses();
            
            // Unmount file systems
            unmountFileSystems();
            
            // Stop network services
            stopNetworkServices();
            
            // Stop system timer
            if (systemTimer != null) {
                systemTimer.cancel();
            }
            
            // Shutdown kernel
            if (kernel != null) {
                kernel.shutdown();
            }
            
            setState(OSState.SHUTDOWN);
            log.info("{} {} shutdown complete", name, version);
            
        } catch (Exception e) {
            log.error("Shutdown error for {} {}", name, version, e);
            setState(OSState.ERROR);
        }
    }
    
    public Process createProcess(String name, String command, String[] args, Map<String, String> env) {
        Process process = new Process();
        process.setPid(nextPid.getAndIncrement());
        process.setName(name);
        process.setCommand(command);
        process.setArguments(args);
        process.setEnvironment(env != null ? env : getEnvironmentVariables());
        process.setState(ProcessState.CREATED);
        process.setCreationTime(LocalDateTime.now());
        process.setParentPid(getCurrentProcess() != null ? getCurrentProcess().getPid() : 1);
        
        // Allocate memory for process
        int memorySize = calculateProcessMemory(process);
        MemoryBlock memBlock = memory.allocate(memorySize);
        process.setMemoryBlock(memBlock);
        
        // Add to process table
        processes.put(process.getPid(), process);
        
        // Schedule with kernel
        kernel.getScheduler().addProcess(process);
        
        log.debug("Created process: {} (PID: {})", name, process.getPid());
        return process;
    }
    
    public void terminateProcess(int pid) {
        Process process = processes.get(pid);
        if (process != null) {
            process.setState(ProcessState.TERMINATED);
            
            // Free memory
            if (process.getMemoryBlock() != null) {
                memory.free(process.getMemoryBlock());
            }
            
            // Remove from scheduler
            kernel.getScheduler().removeProcess(process);
            
            // Remove from process table
            processes.remove(pid);
            
            log.debug("Terminated process PID: {}", pid);
        }
    }
    
    protected void createInitProcess() {
        Process init = createProcess("init", "/sbin/init", new String[]{}, getEnvironmentVariables());
        init.setPid(1); // Force PID 1 for init
        init.setParentPid(0);
        init.setState(ProcessState.RUNNING);
    }
    
    protected void initializeDevices() {
        // Add CPU
        devices.put("cpu0", new CPU("cpu0", "Intel Core i7", 4, 3600));
        
        // Add memory device
        devices.put("mem", new MemoryDevice("mem", memory.getTotalSize()));
        
        // Add storage devices
        devices.put("sda", new StorageDevice("sda", "Primary HDD", 1024L * 1024 * 1024 * 500)); // 500GB
        
        // Add network devices
        devices.put("eth0", new NetworkAdapter("eth0", "Ethernet", "00:11:22:33:44:55"));
        
        log.info("Initialized {} devices", devices.size());
    }
    
    protected void startSystemTimer() {
        systemTimer = new Timer("SystemTimer-" + name, true);
        systemTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                systemClock.incrementAndGet();
                uptime = systemClock.get();
                
                // Update process times
                processes.values().stream()
                    .filter(p -> p.getState() == ProcessState.RUNNING)
                    .forEach(p -> p.incrementCpuTime());
                
                // Run kernel tick
                kernel.tick();
            }
        }, 0, 10); // 10ms tick rate
    }
    
    protected void stopAllProcesses() {
        // Stop in reverse order (highest PID first)
        processes.keySet().stream()
            .sorted(Comparator.reverseOrder())
            .forEach(this::terminateProcess);
    }
    
    protected void unmountFileSystems() {
        if (fileSystem != null) {
            fileSystem.unmountAll();
        }
    }
    
    protected void stopNetworkServices() {
        devices.values().stream()
            .filter(d -> d instanceof NetworkAdapter)
            .forEach(Device::disable);
    }
    
    protected abstract FileSystem createFileSystem();
    protected abstract long getDefaultMemorySize();
    protected abstract int calculateProcessMemory(Process process);
    protected abstract Process getCurrentProcess();
    
    public enum OSState {
        SHUTDOWN,
        BOOTING,
        RUNNING,
        SHUTTING_DOWN,
        SUSPENDED,
        ERROR
    }
}