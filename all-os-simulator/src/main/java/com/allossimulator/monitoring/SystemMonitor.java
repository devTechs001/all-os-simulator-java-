package com.allossimulator.monitoring;

import io.micrometer.core.instrument.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/monitor")
public class SystemMonitor {
    
    private final MeterRegistry meterRegistry;
    private final PerformanceCollector performanceCollector;
    private final ResourceTracker resourceTracker;
    private final ProcessMonitor processMonitor;
    private final NetworkMonitor networkMonitor;
    
    // Real-time metrics
    private final Map<String, Metric> metrics = new ConcurrentHashMap<>();
    private final CircularBuffer cpuHistory = new CircularBuffer(1000);
    private final CircularBuffer memoryHistory = new CircularBuffer(1000);
    
    @Autowired
    public SystemMonitor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.performanceCollector = new PerformanceCollector();
        this.resourceTracker = new ResourceTracker();
        this.processMonitor = new ProcessMonitor();
        this.networkMonitor = new NetworkMonitor();
        
        initializeMetrics();
        startMonitoring();
    }
    
    private void initializeMetrics() {
        // CPU metrics
        Gauge.builder("system.cpu.usage", this, SystemMonitor::getCpuUsage)
            .description("Current CPU usage percentage")
            .register(meterRegistry);
        
        // Memory metrics
        Gauge.builder("system.memory.used", this, SystemMonitor::getMemoryUsed)
            .description("Used memory in bytes")
            .register(meterRegistry);
        
        // Process metrics
        Gauge.builder("system.process.count", processMonitor, ProcessMonitor::getProcessCount)
            .description("Number of running processes")
            .register(meterRegistry);
        
        // Network metrics
        Counter.builder("system.network.bytes.received")
            .description("Total bytes received")
            .register(meterRegistry);
        
        Counter.builder("system.network.bytes.sent")
            .description("Total bytes sent")
            .register(meterRegistry);
    }
    
    private void startMonitoring() {
        // Start a thread to collect metrics periodically
        Thread monitoringThread = new Thread(this::monitoringLoop);
        monitoringThread.setDaemon(true);
        monitoringThread.start();
    }
    
    private void monitoringLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                collectMetrics();
                Thread.sleep(100); // Sleep for 100ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("Error collecting metrics: " + e.getMessage());
            }
        }
    }
    
    private void collectMetrics() {
        // Collect CPU metrics
        double cpuUsage = performanceCollector.getCpuUsage();
        cpuHistory.add(cpuUsage);
        metrics.put("cpu.usage", new Metric("cpu.usage", cpuUsage, System.currentTimeMillis()));
        
        // Collect memory metrics
        MemoryInfo memInfo = resourceTracker.getMemoryInfo();
        memoryHistory.add(memInfo.getUsedPercent());
        metrics.put("memory.used", new Metric("memory.used", memInfo.getUsed(), System.currentTimeMillis()));
        metrics.put("memory.free", new Metric("memory.free", memInfo.getFree(), System.currentTimeMillis()));
        
        // Collect process metrics
        List<ProcessInfo> processes = processMonitor.getTopProcesses(10);
        metrics.put("process.top", new Metric("process.top", processes, System.currentTimeMillis()));
        
        // Collect network metrics
        NetworkStats netStats = networkMonitor.getStats();
        metrics.put("network.rx", new Metric("network.rx", netStats.getRxBytes(), System.currentTimeMillis()));
        metrics.put("network.tx", new Metric("network.tx", netStats.getTxBytes(), System.currentTimeMillis()));
        
        // Collect disk I/O metrics
        DiskStats diskStats = resourceTracker.getDiskStats();
        metrics.put("disk.read", new Metric("disk.read", diskStats.getReadBytes(), System.currentTimeMillis()));
        metrics.put("disk.write", new Metric("disk.write", diskStats.getWriteBytes(), System.currentTimeMillis()));
        
        // Detect anomalies
        detectAnomalies();
    }
    
    private void detectAnomalies() {
        // CPU spike detection
        if (cpuHistory.getAverage() > 80) {
            triggerAlert(new Alert(
                AlertLevel.WARNING,
                "High CPU usage",
                "CPU usage has been above 80% for the last minute"
            ));
        }
        
        // Memory leak detection
        if (isMemoryLeaking()) {
            triggerAlert(new Alert(
                AlertLevel.CRITICAL,
                "Potential memory leak",
                "Memory usage is continuously increasing"
            ));
        }
        
        // Network anomaly detection
        if (networkMonitor.detectAnomaly()) {
            triggerAlert(new Alert(
                AlertLevel.WARNING,
                "Network anomaly detected",
                "Unusual network traffic pattern detected"
            ));
        }
    }
    
    private boolean isMemoryLeaking() {
        // Simple algorithm to detect potential memory leaks
        // Check if memory usage has been consistently increasing
        if (memoryHistory.getSize() < 100) {
            return false; // Not enough data points
        }
        
        double[] recentMemory = memoryHistory.getRecentValues(100);
        double trend = calculateTrend(recentMemory);
        
        return trend > 0.7; // If trend is positive and significant
    }
    
    private double calculateTrend(double[] values) {
        // Simple linear regression to calculate trend
        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
        int n = values.length;
        
        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += values[i];
            sumXY += i * values[i];
            sumXX += i * i;
        }
        
        return (n * sumXY - sumX * sumY) / (n * sumXX - sumX * sumX);
    }
    
    private void triggerAlert(Alert alert) {
        System.out.println("ALERT: " + alert.getMessage());
        // In a real system, this would send the alert to a monitoring system
    }
    
    @GetMapping("/metrics")
    public Map<String, Metric> getMetrics() {
        return new HashMap<>(metrics);
    }
    
    @GetMapping("/metrics/{metric}")
    public Metric getMetric(@PathVariable String metric) {
        return metrics.get(metric);
    }
    
    @GetMapping("/metrics/history/{metric}")
    public List<Metric> getMetricHistory(
            @PathVariable String metric,
            @RequestParam(defaultValue = "1h") String timeRange) {
        
        long startTime = parseTimeRange(timeRange);
        return getHistoricalMetrics(metric, startTime);
    }
    
    private long parseTimeRange(String timeRange) {
        if (timeRange.endsWith("h")) {
            return System.currentTimeMillis() - (Long.parseLong(timeRange.replace("h", "")) * 3600 * 1000L);
        } else if (timeRange.endsWith("m")) {
            return System.currentTimeMillis() - (Long.parseLong(timeRange.replace("m", "")) * 60 * 1000L);
        } else if (timeRange.endsWith("s")) {
            return System.currentTimeMillis() - (Long.parseLong(timeRange.replace("s", "")) * 1000L);
        } else {
            return System.currentTimeMillis() - (1 * 3600 * 1000L); // Default to 1 hour
        }
    }
    
    private List<Metric> getHistoricalMetrics(String metric, long startTime) {
        // This would normally query a time-series database
        // For now, return the current value
        List<Metric> history = new ArrayList<>();
        Metric current = metrics.get(metric);
        if (current != null) {
            history.add(current);
        }
        return history;
    }
    
    // Getter methods for metrics
    public double getCpuUsage() {
        Metric cpuMetric = metrics.get("cpu.usage");
        return cpuMetric != null ? (Double) cpuMetric.getValue() : 0.0;
    }
    
    public long getMemoryUsed() {
        Metric memoryMetric = metrics.get("memory.used");
        return memoryMetric != null ? (Long) memoryMetric.getValue() : 0L;
    }
    
    @ServerEndpoint("/ws/metrics")
    public static class MetricsWebSocket {
        private static Set<Session> activeSessions = Collections.synchronizedSet(new HashSet<>());
        
        @OnOpen
        public void onOpen(Session session) {
            activeSessions.add(session);
            System.out.println("WebSocket connection opened: " + session.getId());
        }
        
        @OnClose
        public void onClose(Session session) {
            activeSessions.remove(session);
            System.out.println("WebSocket connection closed: " + session.getId());
        }
        
        @OnMessage
        public void onMessage(String message, Session session) {
            System.out.println("Received message: " + message);
        }
        
        @OnError
        public void onError(Session session, Throwable throwable) {
            System.err.println("WebSocket error: " + throwable.getMessage());
        }
        
        public static void broadcastMetrics(String metricsJson) {
            synchronized (activeSessions) {
                for (Session session : activeSessions) {
                    if (session.isOpen()) {
                        try {
                            session.getBasicRemote().sendText(metricsJson);
                        } catch (Exception e) {
                            System.err.println("Error sending WebSocket message: " + e.getMessage());
                        }
                    }
                }
            }
        }
    }
    
    public class PerformanceProfiler {
        private final Map<String, MethodProfile> methodProfiles = new ConcurrentHashMap<>();
        
        public Object profileMethod(String methodName, Runnable method) throws Exception {
            long startTime = System.nanoTime();
            long startMemory = getUsedMemory();
            
            try {
                method.run();
                
                long duration = System.nanoTime() - startTime;
                long memoryDelta = getUsedMemory() - startMemory;
                
                updateProfile(methodName, duration, memoryDelta);
                
                return null;
            } catch (Exception e) {
                recordException(methodName, e);
                throw e;
            }
        }
        
        private void updateProfile(String methodName, long duration, long memoryDelta) {
            methodProfiles.compute(methodName, (key, profile) -> {
                if (profile == null) {
                    profile = new MethodProfile(methodName);
                }
                
                profile.addExecution(duration, memoryDelta);
                
                // Detect performance issues
                if (duration > profile.getAverageDuration() * 2) {
                    System.out.println("Performance degradation detected in " + 
                            methodName + ": " + (duration / 1_000_000) + "ms (avg: " + 
                            (profile.getAverageDuration() / 1_000_000) + "ms)");
                }
                
                return profile;
            });
        }
        
        private void recordException(String methodName, Exception e) {
            System.err.println("Exception in method " + methodName + ": " + e.getMessage());
        }
        
        private long getUsedMemory() {
            return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        }
    }
}

// Supporting classes
class Metric {
    private String name;
    private Object value;
    private long timestamp;
    
    public Metric(String name, Object value, long timestamp) {
        this.name = name;
        this.value = value;
        this.timestamp = timestamp;
    }
    
    // Getters and setters
    public String getName() { return name; }
    public Object getValue() { return value; }
    public long getTimestamp() { return timestamp; }
    
    public void setName(String name) { this.name = name; }
    public void setValue(Object value) { this.value = value; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}

class CircularBuffer {
    private double[] buffer;
    private int size;
    private int index = 0;
    private int count = 0;
    
    public CircularBuffer(int size) {
        this.buffer = new double[size];
        this.size = size;
    }
    
    public void add(double value) {
        buffer[index] = value;
        index = (index + 1) % size;
        if (count < size) {
            count++;
        }
    }
    
    public double getAverage() {
        if (count == 0) return 0;
        
        double sum = 0;
        for (int i = 0; i < count; i++) {
            sum += buffer[i];
        }
        return sum / count;
    }
    
    public double[] getRecentValues(int numValues) {
        int actualCount = Math.min(count, numValues);
        double[] values = new double[actualCount];
        
        for (int i = 0; i < actualCount; i++) {
            int idx = (index - actualCount + i + size) % size;
            values[i] = buffer[idx];
        }
        
        return values;
    }
    
    public int getSize() {
        return count;
    }
}

class MemoryInfo {
    private long total;
    private long used;
    private long free;
    
    public MemoryInfo(long total, long used) {
        this.total = total;
        this.used = used;
        this.free = total - used;
    }
    
    public long getTotal() { return total; }
    public long getUsed() { return used; }
    public long getFree() { return free; }
    public double getUsedPercent() { return (double) used / total * 100; }
    
    public void setTotal(long total) { this.total = total; }
    public void setUsed(long used) { this.used = used; }
    public void setFree(long free) { this.free = free; }
}

class ProcessInfo {
    private String name;
    private long pid;
    private double cpuUsage;
    private long memoryUsage;
    
    public ProcessInfo(String name, long pid, double cpuUsage, long memoryUsage) {
        this.name = name;
        this.pid = pid;
        this.cpuUsage = cpuUsage;
        this.memoryUsage = memoryUsage;
    }
    
    // Getters and setters
    public String getName() { return name; }
    public long getPid() { return pid; }
    public double getCpuUsage() { return cpuUsage; }
    public long getMemoryUsage() { return memoryUsage; }
}

class NetworkStats {
    private long rxBytes;
    private long txBytes;
    
    public NetworkStats(long rxBytes, long txBytes) {
        this.rxBytes = rxBytes;
        this.txBytes = txBytes;
    }
    
    public long getRxBytes() { return rxBytes; }
    public long getTxBytes() { return txBytes; }
    
    public void setRxBytes(long rxBytes) { this.rxBytes = rxBytes; }
    public void setTxBytes(long txBytes) { this.txBytes = txBytes; }
}

class DiskStats {
    private long readBytes;
    private long writeBytes;
    
    public DiskStats(long readBytes, long writeBytes) {
        this.readBytes = readBytes;
        this.writeBytes = writeBytes;
    }
    
    public long getReadBytes() { return readBytes; }
    public long getWriteBytes() { return writeBytes; }
    
    public void setReadBytes(long readBytes) { this.readBytes = readBytes; }
    public void setWriteBytes(long writeBytes) { this.writeBytes = writeBytes; }
}

class Alert {
    private AlertLevel level;
    private String title;
    private String message;
    
    public Alert(AlertLevel level, String title, String message) {
        this.level = level;
        this.title = title;
        this.message = message;
    }
    
    // Getters and setters
    public AlertLevel getLevel() { return level; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
}

enum AlertLevel {
    INFO, WARNING, CRITICAL
}

class MethodProfile {
    private String methodName;
    private List<Long> durations = new ArrayList<>();
    private List<Long> memoryDeltas = new ArrayList<>();
    
    public MethodProfile(String methodName) {
        this.methodName = methodName;
    }
    
    public void addExecution(long duration, long memoryDelta) {
        durations.add(duration);
        memoryDeltas.add(memoryDelta);
    }
    
    public long getAverageDuration() {
        return durations.isEmpty() ? 0 : durations.stream().mapToLong(Long::longValue).average().orElse(0.0);
    }
    
    public long getAverageMemoryDelta() {
        return memoryDeltas.isEmpty() ? 0 : memoryDeltas.stream().mapToLong(Long::longValue).average().orElse(0.0);
    }
    
    // Getters and setters
    public String getMethodName() { return methodName; }
    public List<Long> getDurations() { return durations; }
    public List<Long> getMemoryDeltas() { return memoryDeltas; }
}

// Placeholder implementations of dependencies
class PerformanceCollector {
    public double getCpuUsage() {
        // Simulate CPU usage (random value between 0 and 100)
        return Math.random() * 100;
    }
}

class ResourceTracker {
    public MemoryInfo getMemoryInfo() {
        long total = Runtime.getRuntime().maxMemory();
        long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        return new MemoryInfo(total, used);
    }
    
    public DiskStats getDiskStats() {
        // Simulate disk stats
        return new DiskStats((long)(Math.random() * 1000000), (long)(Math.random() * 1000000));
    }
}

class ProcessMonitor {
    public List<ProcessInfo> getTopProcesses(int limit) {
        // Simulate process list
        List<ProcessInfo> processes = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            processes.add(new ProcessInfo("process-" + i, i, Math.random() * 100, (long)(Math.random() * 1000000)));
        }
        return processes;
    }
    
    public int getProcessCount() {
        return (int)(Math.random() * 100);
    }
}

class NetworkMonitor {
    public NetworkStats getStats() {
        // Simulate network stats
        return new NetworkStats((long)(Math.random() * 10000000), (long)(Math.random() * 10000000));
    }
    
    public boolean detectAnomaly() {
        // Simulate anomaly detection
        return Math.random() > 0.95; // 5% chance of anomaly
    }
}