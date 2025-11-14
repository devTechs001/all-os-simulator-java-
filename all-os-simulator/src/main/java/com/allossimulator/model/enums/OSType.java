package com.allossimulator.model.enums;

public enum OSType {
    WINDOWS("Windows", "Microsoft Windows", "win"),
    LINUX("Linux", "Linux/GNU", "linux"),
    MACOS("macOS", "Apple macOS", "darwin"),
    ANDROID("Android", "Google Android", "android"),
    IOS("iOS", "Apple iOS", "ios"),
    FREEBSD("FreeBSD", "FreeBSD", "freebsd"),
    CHROMEOS("ChromeOS", "Google ChromeOS", "chromeos"),
    UBUNTU("Ubuntu", "Ubuntu Linux", "ubuntu"),
    DEBIAN("Debian", "Debian Linux", "debian"),
    FEDORA("Fedora", "Fedora Linux", "fedora"),
    ARCH("Arch", "Arch Linux", "arch"),
    CUSTOM("Custom", "Custom OS", "custom");
    
    private final String displayName;
    private final String description;
    private final String identifier;
    
    OSType(String displayName, String description, String identifier) {
        this.displayName = displayName;
        this.description = description;
        this.identifier = identifier;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getIdentifier() {
        return identifier;
    }
    
    public static OSType fromIdentifier(String identifier) {
        for (OSType type : values()) {
            if (type.identifier.equalsIgnoreCase(identifier)) {
                return type;
            }
        }
        return CUSTOM;
    }
}