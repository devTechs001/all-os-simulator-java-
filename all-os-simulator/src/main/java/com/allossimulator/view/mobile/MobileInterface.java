package com.allossimulator.view.mobile;

import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class MobileInterface extends BorderPane {
    
    private HomeScreen homeScreen;
    private AppDrawer appDrawer;
    private NotificationBar notificationBar;
    private ControlCenter controlCenter;
    private GestureHandler gestureHandler;
    
    private boolean isTablet;
    private String osType; // android or ios
    
    public MobileInterface(String osType, boolean isTablet) {
        this.osType = osType;
        this.isTablet = isTablet;
        initialize();
    }
    
    private void initialize() {
        // Setup notification bar
        notificationBar = new NotificationBar(osType);
        setTop(notificationBar);
        
        // Setup home screen with app icons
        homeScreen = new HomeScreen(osType, isTablet);
        setCenter(homeScreen);
        
        // Setup app drawer
        appDrawer = new AppDrawer();
        
        // Setup control center (iOS) or quick settings (Android)
        controlCenter = new ControlCenter(osType);
        
        // Setup gesture handling
        gestureHandler = new GestureHandler(this);
        setupGestures();
        
        // Apply OS-specific styling
        applyOSTheme();
    }
    
    private void setupGestures() {
        gestureHandler.onSwipeUp(() -> {
            if (osType.equals("ios")) {
                showControlCenter();
            } else {
                showAppDrawer();
            }
        });
        
        gestureHandler.onSwipeDown(() -> {
            notificationBar.expand();
        });
        
        gestureHandler.onPinch(scale -> {
            homeScreen.zoom(scale);
        });
    }
    
    private void applyOSTheme() {
        String themeFile = osType.equals("ios") ? "ios.css" : "android.css";
        getStylesheets().add(getClass().getResource("/css/themes/" + themeFile).toExternalForm());
    }
}