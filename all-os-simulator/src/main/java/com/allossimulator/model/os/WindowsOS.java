package com.allossimulator.model.os;

import com.allossimulator.model.filesystem.FileSystem;
import com.allossimulator.model.filesystem.NTFSFileSystem;
import com.allossimulator.model.process.Process;
import com.allossimulator.service.WindowsServiceManager;
import lombok.extern.slf4j.Slf4j;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Entity
@DiscriminatorValue("WINDOWS")
public class WindowsOS extends OperatingSystem {
    
    private static final String WINDOWS_VERSION = "11";
    private static final String BUILD_NUMBER = "22000.1219";
    
    private WindowsServiceManager serviceManager;
    private WindowsRegistry registry;
    
    public WindowsOS() {
        setName("Windows");
        setVersion(WINDOWS_VERSION);
        setType(OSType.WINDOWS);
    }
    
    @Override
    public void initializeKernel() {
        log.info("Initializing Windows NT kernel");
        
        // Initialize Windows-specific kernel modules
        kernel.loadModule("ntoskrnl.exe");
        kernel.loadModule("hal.dll");
        kernel.loadModule("win32k.sys");
        kernel.loadModule("ntfs.sys");
        
        // Initialize Windows registry
        registry = new WindowsRegistry();
        registry.initialize();
        
        // Initialize service manager
        serviceManager = new WindowsServiceManager();
    }
    
    @Override
    public void loadSystemServices() {
        log.info("Starting Windows services");
        
        // Core Windows services
        createService("lsass", "Local Security Authority");
        createService("services", "Service Control Manager");
        createService("csrss", "Client/Server Runtime");
        createService("winlogon", "Windows Logon");
        createService("explorer", "Windows Explorer");
        createService("svchost", "Service Host");
        
        // Network services
        createService("dhcp", "DHCP Client");
        createService("dns", "DNS Client");
        createService("netman", "Network Connections");
        
        // System services
        createService("eventlog", "Windows Event Log");
        createService("schedule", "Task Scheduler");
        createService("themes", "Themes");
        createService("audio", "Windows Audio");
    }
    
    @Override
    public void configureNetworking() {
        log.info("Configuring Windows networking");
        
        // Initialize Winsock
        initializeWinsock();
        
        // Configure network adapters
        configureNetworkAdapters();
        
        // Start network services
        serviceManager.startService("dhcp");
        serviceManager.startService("dns");
        serviceManager.startService("netman");
    }
    
    @Override
    public void mountFileSystems() {
        log.info("Mounting Windows file systems");
        
        // Mount C: drive (system)
        fileSystem.mount("C:", "/", "NTFS");
        
        // Create Windows directory structure
        createWindowsDirectoryStructure();
    }
    
    @Override
    public String getShellCommand() {
        return "cmd.exe";
    }
    
    @Override
    public Map<String, String> getEnvironmentVariables() {
        Map<String, String> env = new HashMap<>();
        env.put("SystemRoot", "C:\\Windows");
        env.put("SystemDrive", "C:");
        env.put("ProgramFiles", "C:\\Program Files");
        env.put("ProgramFiles(x86)", "C:\\Program Files (x86)");
        env.put("ProgramData", "C:\\ProgramData");
        env.put("TEMP", "C:\\Windows\\Temp");
        env.put("TMP", "C:\\Windows\\Temp");
        env.put("USERNAME", "Administrator");
        env.put("USERPROFILE", "C:\\Users\\Administrator");
        env.put("PUBLIC", "C:\\Users\\Public");
        env.put("APPDATA", "C:\\Users\\Administrator\\AppData\\Roaming");
        env.put("LOCALAPPDATA", "C:\\Users\\Administrator\\AppData\\Local");
        env.put("PATH", "C:\\Windows\\System32;C:\\Windows;C:\\Windows\\System32\\Wbem");
        env.put("PATHEXT", ".COM;.EXE;.BAT;.CMD;.VBS;.VBE;.JS;.JSE;.WSF;.WSH;.MSC");
        env.put("ComSpec", "C:\\Windows\\System32\\cmd.exe");
        env.put("OS", "Windows_NT");
        env.put("PROCESSOR_ARCHITECTURE", "AMD64");
        env.put("NUMBER_OF_PROCESSORS", "4");
        return env;
    }
    
    @Override
    protected FileSystem createFileSystem() {
        return new NTFSFileSystem();
    }
    
    @Override
    protected long getDefaultMemorySize() {
        return 4L * 1024 * 1024 * 1024; // 4GB
    }
    
    @Override
    protected int calculateProcessMemory(Process process) {
        // Windows process memory calculation
        return 10 * 1024 * 1024; // 10MB base
    }
    
    @Override
    protected Process getCurrentProcess() {
        // Get current process from Windows context
        return processes.get(1); // Simplified - return system process
    }
    
    private void createService(String name, String displayName) {
        Process service = createProcess(name + ".exe", "C:\\Windows\\System32\\" + name + ".exe", 
                                       new String[]{}, getEnvironmentVariables());
        service.setType(ProcessType.SERVICE);
        serviceManager.registerService(name, service);
    }
    
    private void initializeWinsock() {
        log.debug("Initializing Winsock 2.2");
        kernel.loadModule("ws2_32.dll");
        kernel.loadModule("mswsock.dll");
    }
    
    private void configureNetworkAdapters() {
        // Configure network adapters with Windows-specific settings
        devices.values().stream()
            .filter(d -> d instanceof NetworkAdapter)
            .forEach(adapter -> {
                ((NetworkAdapter) adapter).configure(new WindowsNetworkConfig());
            });
    }
    
    private void createWindowsDirectoryStructure() {
        fileSystem.createDirectory("C:\\Windows");
        fileSystem.createDirectory("C:\\Windows\\System32");
        fileSystem.createDirectory("C:\\Windows\\SysWOW64");
        fileSystem.createDirectory("C:\\Program Files");
        fileSystem.createDirectory("C:\\Program Files (x86)");
        fileSystem.createDirectory("C:\\Users");
        fileSystem.createDirectory("C:\\Users\\Administrator");
        fileSystem.createDirectory("C:\\Users\\Public");
        fileSystem.createDirectory("C:\\ProgramData");
    }
    
    public class WindowsRegistry {
        private Map<String, RegistryKey> hives;
        
        public void initialize() {
            hives = new HashMap<>();
            hives.put("HKEY_LOCAL_MACHINE", new RegistryKey("HKEY_LOCAL_MACHINE"));
            hives.put("HKEY_CURRENT_USER", new RegistryKey("HKEY_CURRENT_USER"));
            hives.put("HKEY_CLASSES_ROOT", new RegistryKey("HKEY_CLASSES_ROOT"));
            hives.put("HKEY_USERS", new RegistryKey("HKEY_USERS"));
            hives.put("HKEY_CURRENT_CONFIG", new RegistryKey("HKEY_CURRENT_CONFIG"));
            
            // Load default registry values
            loadDefaultRegistry();
        }
        
        private void loadDefaultRegistry() {
            // System settings
            setValue("HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion", 
                    "ProductName", "Windows 11");
            setValue("HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion", 
                    "CurrentBuild", BUILD_NUMBER);
        }
        
        public void setValue(String path, String name, Object value) {
            String[] parts = path.split("\\\\", 2);
            RegistryKey hive = hives.get(parts[0]);
            if (hive != null) {
                hive.setValue(parts[1], name, value);
            }
        }
    }
}