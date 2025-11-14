package com.allossimulator.view;

import com.allossimulator.controller.OSController;
import com.allossimulator.model.os.OperatingSystem;
import com.allossimulator.view.component.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class MainView {
    
    @Autowired
    private OSController osController;
    
    private Stage primaryStage;
    private Scene mainScene;
    private BorderPane rootLayout;
    
    // Menu components
    private MenuBar menuBar;
    private ToolBar toolBar;
    private StatusBar statusBar;
    
    // Main content areas
    private TabPane mainTabPane;
    private Map<String, Tab> openTabs;
    
    // Components
    private ProcessTable processTable;
    private MemoryVisualizer memoryVisualizer;
    private FileSystemBrowser fileSystemBrowser;
    private TerminalEmulator terminalEmulator;
    private SystemMonitor systemMonitor;
    private NetworkMonitor networkMonitor;
    private OSDashboard osDashboard;
    
    // Sidebar
    private VBox sidebar;
    private TreeView<String> navigationTree;
    
    public void initialize(Stage stage) {
        this.primaryStage = stage;
        this.openTabs = new HashMap<>();
        
        setupUI();
        setupEventHandlers();
        applyTheme();
        
        primaryStage.setTitle("All-OS-Simulator");
        primaryStage.setScene(mainScene);
        primaryStage.setMaximized(true);
        primaryStage.show();
        
        log.info("Main view initialized");
    }
    
    private void setupUI() {
        rootLayout = new BorderPane();
        
        // Create menu bar
        createMenuBar();
        
        // Create toolbar
        createToolBar();
        
        // Create main content area
        createMainContent();
        
        // Create sidebar
        createSidebar();
        
        // Create status bar
        createStatusBar();
        
        // Assemble layout
        VBox topContainer = new VBox();
        topContainer.getChildren().addAll(menuBar, toolBar);
        rootLayout.setTop(topContainer);
        
        rootLayout.setLeft(sidebar);
        rootLayout.setCenter(mainTabPane);
        rootLayout.setBottom(statusBar);
        
        mainScene = new Scene(rootLayout, 1400, 900);
        mainScene.getStylesheets().add(getClass().getResource("/css/main.css").toExternalForm());
    }
    
    private void createMenuBar() {
        menuBar = new MenuBar();
        
        // File menu
        Menu fileMenu = new Menu("File");
        MenuItem newOS = new MenuItem("New OS Instance");
        MenuItem openOS = new MenuItem("Open OS");
        MenuItem saveState = new MenuItem("Save State");
        MenuItem loadState = new MenuItem("Load State");
        MenuItem exit = new MenuItem("Exit");
        
        newOS.setOnAction(e -> showNewOSDialog());
        saveState.setOnAction(e -> saveCurrentState());
        loadState.setOnAction(e -> loadSavedState());
        exit.setOnAction(e -> exitApplication());
        
        fileMenu.getItems().addAll(newOS, openOS, new SeparatorMenuItem(), 
                                  saveState, loadState, new SeparatorMenuItem(), exit);
        
        // Edit menu
        Menu editMenu = new Menu("Edit");
        MenuItem preferences = new MenuItem("Preferences");
        MenuItem settings = new MenuItem("System Settings");
        
        preferences.setOnAction(e -> showPreferences());
        settings.setOnAction(e -> showSystemSettings());
        
        editMenu.getItems().addAll(preferences, settings);
        
        // View menu
        Menu viewMenu = new Menu("View");
        CheckMenuItem showSidebar = new CheckMenuItem("Sidebar");
        CheckMenuItem showStatusBar = new CheckMenuItem("Status Bar");
        CheckMenuItem darkMode = new CheckMenuItem("Dark Mode");
        
        showSidebar.setSelected(true);
        showStatusBar.setSelected(true);
        
        showSidebar.setOnAction(e -> toggleSidebar(showSidebar.isSelected()));
        showStatusBar.setOnAction(e -> toggleStatusBar(showStatusBar.isSelected()));
        darkMode.setOnAction(e -> toggleDarkMode(darkMode.isSelected()));
        
        viewMenu.getItems().addAll(showSidebar, showStatusBar, new SeparatorMenuItem(), darkMode);
        
        // Tools menu
        Menu toolsMenu = new Menu("Tools");
        MenuItem processManager = new MenuItem("Process Manager");
        MenuItem resourceMonitor = new MenuItem("Resource Monitor");
        MenuItem terminal = new MenuItem("Terminal");
        MenuItem networkTools = new MenuItem("Network Tools");
        MenuItem diskManager = new MenuItem("Disk Manager");
        
        processManager.setOnAction(e -> openProcessManager());
        resourceMonitor.setOnAction(e -> openResourceMonitor());
        terminal.setOnAction(e -> openTerminal());
        networkTools.setOnAction(e -> openNetworkTools());
        diskManager.setOnAction(e -> openDiskManager());
        
        toolsMenu.getItems().addAll(processManager, resourceMonitor, terminal, 
                                   networkTools, diskManager);
        
        // Help menu
        Menu helpMenu = new Menu("Help");
        MenuItem documentation = new MenuItem("Documentation");
        MenuItem about = new MenuItem("About");
        
        documentation.setOnAction(e -> showDocumentation());
        about.setOnAction(e -> showAbout());
        
        helpMenu.getItems().addAll(documentation, about);
        
        menuBar.getMenus().addAll(fileMenu, editMenu, viewMenu, toolsMenu, helpMenu);
    }
    
    private void createToolBar() {
        toolBar = new ToolBar();
        
        Button bootBtn = new Button("Boot OS");
        Button shutdownBtn = new Button("Shutdown");
        Button restartBtn = new Button("Restart");
        Button suspendBtn = new Button("Suspend");
        
        bootBtn.setOnAction(e -> bootSelectedOS());
        shutdownBtn.setOnAction(e -> shutdownCurrentOS());
        restartBtn.setOnAction(e -> restartCurrentOS());
        suspendBtn.setOnAction(e -> suspendCurrentOS());
        
        ComboBox<String> osSelector = new ComboBox<>();
        osSelector.getItems().addAll("Windows 11", "Ubuntu 22.04", "macOS Monterey", 
                                     "Android 13", "iOS 16");
        osSelector.setValue("Windows 11");
        
        Separator separator1 = new Separator();
        
        Button terminalBtn = new Button("Terminal");
        Button fileManagerBtn = new Button("Files");
        Button monitorBtn = new Button("Monitor");
        Button settingsBtn = new Button("Settings");
        
        terminalBtn.setOnAction(e -> openTerminal());
        fileManagerBtn.setOnAction(e -> openFileManager());
        monitorBtn.setOnAction(e -> openResourceMonitor());
        settingsBtn.setOnAction(e -> showSystemSettings());
        
        toolBar.getItems().addAll(
            new Label("OS:"), osSelector,
            separator1,
            bootBtn, shutdownBtn, restartBtn, suspendBtn,
            new Separator(),
            terminalBtn, fileManagerBtn, monitorBtn, settingsBtn
        );
    }
    
    private void createMainContent() {
        mainTabPane = new TabPane();
        mainTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        
        // Create dashboard tab
        Tab dashboardTab = new Tab("Dashboard");
        dashboardTab.setClosable(false);
        osDashboard = new OSDashboard();
        dashboardTab.setContent(osDashboard);
        mainTabPane.getTabs().add(dashboardTab);
        openTabs.put("dashboard", dashboardTab);
    }
    
    private void createSidebar() {
        sidebar = new VBox(10);
        sidebar.setPrefWidth(250);
        sidebar.setPadding(new Insets(10));
        sidebar.getStyleClass().add("sidebar");
        
        // Search box
        TextField searchField = new TextField();
        searchField.setPromptText("Search...");
        searchField.getStyleClass().add("search-field");
        
        // Navigation tree
        TreeItem<String> rootItem = new TreeItem<>("System");
        rootItem.setExpanded(true);
        
        TreeItem<String> osItem = new TreeItem<>("Operating System");
        osItem.getChildren().addAll(
            new TreeItem<>("Boot Manager"),
            new TreeItem<>("Kernel"),
            new TreeItem<>("Services"),
            new TreeItem<>("Drivers")
        );
        
        TreeItem<String> processesItem = new TreeItem<>("Processes");
        processesItem.getChildren().addAll(
            new TreeItem<>("Running"),
            new TreeItem<>("Suspended"),
            new TreeItem<>("Terminated")
        );
        
        TreeItem<String> memoryItem = new TreeItem<>("Memory");
        memoryItem.getChildren().addAll(
            new TreeItem<>("Physical Memory"),
            new TreeItem<>("Virtual Memory"),
            new TreeItem<>("Cache"),
            new TreeItem<>("Swap")
        );
        
        TreeItem<String> storageItem = new TreeItem<>("Storage");
        storageItem.getChildren().addAll(
            new TreeItem<>("Disks"),
            new TreeItem<>("Partitions"),
            new TreeItem<>("File Systems"),
            new TreeItem<>("Mount Points")
        );
        
        TreeItem<String> networkItem = new TreeItem<>("Network");
        networkItem.getChildren().addAll(
            new TreeItem<>("Interfaces"),
            new TreeItem<>("Connections"),
            new TreeItem<>("Firewall"),
            new TreeItem<>("DNS")
        );
        
        TreeItem<String> devicesItem = new TreeItem<>("Devices");
        devicesItem.getChildren().addAll(
            new TreeItem<>("CPU"),
            new TreeItem<>("GPU"),
            new TreeItem<>("USB"),
            new TreeItem<>("Audio")
        );
        
        rootItem.getChildren().addAll(osItem, processesItem, memoryItem, 
                                      storageItem, networkItem, devicesItem);
        
        navigationTree = new TreeView<>(rootItem);
        navigationTree.getStyleClass().add("navigation-tree");
        
        // Add click handler
        navigationTree.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                TreeItem<String> item = navigationTree.getSelectionModel().getSelectedItem();
                if (item != null && item.isLeaf()) {
                    openComponent(item.getValue());
                }
            }
        });
        
        // Quick actions
        VBox quickActions = new VBox(5);
        quickActions.getChildren().addAll(
            new Label("Quick Actions"),
            new Button("New Process"),
            new Button("Kill Process"),
            new Button("Clear Memory"),
            new Button("Network Reset")
        );
        
        sidebar.getChildren().addAll(searchField, navigationTree, new Separator(), quickActions);
    }
    
    private void createStatusBar() {
        statusBar = new StatusBar();
        
        // OS info
        Label osLabel = new Label("OS: Not Loaded");
        osLabel.getStyleClass().add("status-label");
        
        // CPU usage
        ProgressBar cpuBar = new ProgressBar();
        cpuBar.setPrefWidth(100);
        Label cpuLabel = new Label("CPU: 0%");
        
        // Memory usage
        ProgressBar memBar = new ProgressBar();
        memBar.setPrefWidth(100);
        Label memLabel = new Label("RAM: 0%");
        
        // Network status
        Label netLabel = new Label("Network: Connected");
        
        // Time
        Label timeLabel = new Label("00:00:00");
        
        // Update timer
        Timeline timeline = new Timeline(
            new KeyFrame(Duration.seconds(1), e -> {
                updateStatusBar(cpuBar, cpuLabel, memBar, memLabel, timeLabel);
            })
        );
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
        
        statusBar.getLeftItems().addAll(osLabel, new Separator(Orientation.VERTICAL));
        statusBar.getCenterItems().addAll(
            cpuLabel, cpuBar, new Separator(Orientation.VERTICAL),
            memLabel, memBar, new Separator(Orientation.VERTICAL),
            netLabel
        );
        statusBar.getRightItems().add(timeLabel);
    }
    
    private void openComponent(String componentName) {
        // Check if tab already exists
        if (openTabs.containsKey(componentName)) {
            mainTabPane.getSelectionModel().select(openTabs.get(componentName));
            return;
        }
        
        Tab tab = new Tab(componentName);
        Node content = null;
        
        switch (componentName.toLowerCase()) {
            case "running":
                processTable = new ProcessTable();
                content = processTable;
                break;
            case "physical memory":
                memoryVisualizer = new MemoryVisualizer();
                content = memoryVisualizer;
                break;
            case "file systems":
                fileSystemBrowser = new FileSystemBrowser();
                content = fileSystemBrowser;
                break;
            case "kernel":
                content = new KernelViewer();
                break;
            case "services":
                content = new ServiceManager();
                break;
            case "interfaces":
                networkMonitor = new NetworkMonitor();
                content = networkMonitor;
                break;
            case "cpu":
                content = new CPUMonitor();
                break;
            default:
                content = new Label("Component: " + componentName);
        }
        
        tab.setContent(content);
        mainTabPane.getTabs().add(tab);
        mainTabPane.getSelectionModel().select(tab);
        openTabs.put(componentName, tab);
        
        // Remove tab from map when closed
        tab.setOnClosed(e -> openTabs.remove(componentName));
    }
    
    private void showNewOSDialog() {
        Dialog<OperatingSystem> dialog = new Dialog<>();
        dialog.setTitle("Create New OS Instance");
        dialog.setHeaderText("Select OS type and configure settings");
        
        // Create form
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        
        ComboBox<String> osType = new ComboBox<>();
        osType.getItems().addAll("Windows", "Linux", "macOS", "Android", "iOS");
        
        ComboBox<String> version = new ComboBox<>();
        TextField memoryField = new TextField("4096");
        TextField diskField = new TextField("100");
        TextField cpuCores = new TextField("4");
        
        grid.add(new Label("OS Type:"), 0, 0);
        grid.add(osType, 1, 0);
        grid.add(new Label("Version:"), 0, 1);
        grid.add(version, 1, 1);
        grid.add(new Label("Memory (MB):"), 0, 2);
        grid.add(memoryField, 1, 2);
        grid.add(new Label("Disk (GB):"), 0, 3);
        grid.add(diskField, 1, 3);
        grid.add(new Label("CPU Cores:"), 0, 4);
        grid.add(cpuCores, 1, 4);
        
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                return osController.createOS(
                    osType.getValue(),
                    version.getValue(),
                    Integer.parseInt(memoryField.getText()),
                    Integer.parseInt(diskField.getText()),
                    Integer.parseInt(cpuCores.getText())
                );
            }
            return null;
        });
        
        dialog.showAndWait().ifPresent(os -> {
            log.info("Created new OS instance: {}", os.getName());
            refreshDashboard();
        });
    }
    
    private void openTerminal() {
        if (terminalEmulator == null) {
            terminalEmulator = new TerminalEmulator();
        }
        
        Tab terminalTab = openTabs.get("terminal");
        if (terminalTab == null) {
            terminalTab = new Tab("Terminal");
            terminalTab.setContent(terminalEmulator);
            mainTabPane.getTabs().add(terminalTab);
            openTabs.put("terminal", terminalTab);
        }
        
        mainTabPane.getSelectionModel().select(terminalTab);
    }
    
    private void openProcessManager() {
        if (processTable == null) {
            processTable = new ProcessTable();
        }
        
        Tab processTab = openTabs.get("processes");
        if (processTab == null) {
            processTab = new Tab("Process Manager");
            processTab.setContent(processTable);
            mainTabPane.getTabs().add(processTab);
            openTabs.put("processes", processTab);
        }
        
        mainTabPane.getSelectionModel().select(processTab);
    }
    
    private void updateStatusBar(ProgressBar cpuBar, Label cpuLabel, 
                                 ProgressBar memBar, Label memLabel, 
                                 Label timeLabel) {
        Platform.runLater(() -> {
            // Update CPU usage
            double cpuUsage = osController.getCpuUsage();
            cpuBar.setProgress(cpuUsage / 100.0);
            cpuLabel.setText(String.format("CPU: %.1f%%", cpuUsage));
            
            // Update memory usage
            double memUsage = osController.getMemoryUsage();
            memBar.setProgress(memUsage / 100.0);
            memLabel.setText(String.format("RAM: %.1f%%", memUsage));
            
            // Update time
            timeLabel.setText(java.time.LocalTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
            ));
        });
    }
    
    private void applyTheme() {
        String theme = PreferenceManager.getInstance().getTheme();
        mainScene.getStylesheets().clear();
        mainScene.getStylesheets().add(
            getClass().getResource("/css/" + theme + ".css").toExternalForm()
        );
    }
    
    // Event handlers
    private void setupEventHandlers() {
        primaryStage.setOnCloseRequest(event -> {
            if (confirmExit()) {
                exitApplication();
            } else {
                event.consume();
            }
        });
    }
    
    private boolean confirmExit() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Exit");
        alert.setHeaderText("Are you sure you want to exit?");
        alert.setContentText("All running OS instances will be shut down.");
        
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }
    
    private void exitApplication() {
        log.info("Exiting application");
        osController.shutdownAll();
        Platform.exit();
        System.exit(0);
    }
    
    // Additional helper methods
    private void refreshDashboard() {
        if (osDashboard != null) {
            osDashboard.refresh();
        }
    }
    
    private void toggleSidebar(boolean show) {
        sidebar.setVisible(show);
        sidebar.setManaged(show);
    }
    
    private void toggleStatusBar(boolean show) {
        statusBar.setVisible(show);
        statusBar.setManaged(show);
    }
    
    private void toggleDarkMode(boolean enabled) {
        PreferenceManager.getInstance().setDarkMode(enabled);
        applyTheme();
    }
}