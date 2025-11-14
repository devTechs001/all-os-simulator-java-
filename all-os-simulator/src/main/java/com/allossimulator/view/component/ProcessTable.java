package com.allossimulator.view.component;

import com.allossimulator.model.process.Process;
import com.allossimulator.model.process.ProcessState;
import com.allossimulator.model.enums.Priority;
import com.allossimulator.service.ProcessService;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.util.Callback;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Timer;
import java.util.TimerTask;

@Component
public class ProcessTable extends VBox {
    
    @Autowired
    private ProcessService processService;
    
    private TableView<ProcessRow> table;
    private ObservableList<ProcessRow> processData;
    private TextField searchField;
    private ComboBox<String> filterCombo;
    private Label statusLabel;
    private Timer refreshTimer;
    
    public ProcessTable() {
        initialize();
        startAutoRefresh();
    }
    
    private void initialize() {
        setPadding(new Insets(10));
        setSpacing(10);
        
        // Create toolbar
        HBox toolbar = createToolbar();
        
        // Create table
        table = createProcessTable();
        
        // Create context menu
        ContextMenu contextMenu = createContextMenu();
        table.setContextMenu(contextMenu);
        
        // Create status bar
        HBox statusBar = createStatusBar();
        
        // Add components
        getChildren().addAll(toolbar, table, statusBar);
        
        // Set table to grow
        VBox.setVgrow(table, Priority.ALWAYS);
        
        // Load initial data
        refreshProcessList();
    }
    
    private HBox createToolbar() {
        HBox toolbar = new HBox(10);
        toolbar.setPadding(new Insets(5));
        
        // Search field
        searchField = new TextField();
        searchField.setPromptText("Search processes...");
        searchField.setPrefWidth(200);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterProcesses());
        
        // Filter combo
        filterCombo = new ComboBox<>();
        filterCombo.getItems().addAll("All", "Running", "Suspended", "Blocked", "System", "User");
        filterCombo.setValue("All");
        filterCombo.setOnAction(e -> filterProcesses());
        
        // Action buttons
        Button refreshBtn = new Button("Refresh");
        Button killBtn = new Button("Kill Process");
        Button suspendBtn = new Button("Suspend");
        Button resumeBtn = new Button("Resume");
        Button newProcessBtn = new Button("New Process");
        
        refreshBtn.setOnAction(e -> refreshProcessList());
        killBtn.setOnAction(e -> killSelectedProcess());
        suspendBtn.setOnAction(e -> suspendSelectedProcess());
        resumeBtn.setOnAction(e -> resumeSelectedProcess());
        newProcessBtn.setOnAction(e -> showNewProcessDialog());
        
        // Add to toolbar
        toolbar.getChildren().addAll(
            new Label("Search:"), searchField,
            new Label("Filter:"), filterCombo,
            new Separator(Orientation.VERTICAL),
            refreshBtn, killBtn, suspendBtn, resumeBtn, newProcessBtn
        );
        
        return toolbar;
    }
    
    private TableView<ProcessRow> createProcessTable() {
        table = new TableView<>();
        processData = FXCollections.observableArrayList();
        table.setItems(processData);
        
        // PID column
        TableColumn<ProcessRow, Integer> pidCol = new TableColumn<>("PID");
        pidCol.setCellValueFactory(new PropertyValueFactory<>("pid"));
        pidCol.setPrefWidth(60);
        
        // Name column
        TableColumn<ProcessRow, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(150);
        
        // State column
        TableColumn<ProcessRow, String> stateCol = new TableColumn<>("State");
        stateCol.setCellValueFactory(new PropertyValueFactory<>("state"));
        stateCol.setPrefWidth(80);
        stateCol.setCellFactory(column -> new TableCell<ProcessRow, String>() {
            @Override
            protected void updateItem(String state, boolean empty) {
                super.updateItem(state, empty);
                if (empty || state == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(state);
                    switch (state) {
                        case "RUNNING":
                            setStyle("-fx-text-fill: green;");
                            break;
                        case "SUSPENDED":
                            setStyle("-fx-text-fill: orange;");
                            break;
                        case "TERMINATED":
                            setStyle("-fx-text-fill: red;");
                            break;
                        default:
                            setStyle("");
                    }
                }
            }
        });
        
        // Priority column
        TableColumn<ProcessRow, String> priorityCol = new TableColumn<>("Priority");
        priorityCol.setCellValueFactory(new PropertyValueFactory<>("priority"));
        priorityCol.setPrefWidth(80);
        
        // CPU column
        TableColumn<ProcessRow, Double> cpuCol = new TableColumn<>("CPU %");
        cpuCol.setCellValueFactory(new PropertyValueFactory<>("cpuUsage"));
        cpuCol.setPrefWidth(80);
        cpuCol.setCellFactory(column -> new TableCell<ProcessRow, Double>() {
            @Override
            protected void updateItem(Double cpu, boolean empty) {
                super.updateItem(cpu, empty);
                if (empty || cpu == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(String.format("%.1f%%", cpu));
                    
                    // Add progress bar
                    ProgressBar progressBar = new ProgressBar(cpu / 100.0);
                    progressBar.setPrefWidth(60);
                    progressBar.setPrefHeight(15);
                    setGraphic(progressBar);
                }
            }
        });
        
        // Memory column
        TableColumn<ProcessRow, String> memoryCol = new TableColumn<>("Memory");
        memoryCol.setCellValueFactory(new PropertyValueFactory<>("memory"));
        memoryCol.setPrefWidth(100);
        
        // User column
        TableColumn<ProcessRow, String> userCol = new TableColumn<>("User");
        userCol.setCellValueFactory(new PropertyValueFactory<>("user"));
        userCol.setPrefWidth(80);
        
        // Start time column
        TableColumn<ProcessRow, String> startTimeCol = new TableColumn<>("Start Time");
        startTimeCol.setCellValueFactory(new PropertyValueFactory<>("startTime"));
        startTimeCol.setPrefWidth(150);
        
        // Command column
        TableColumn<ProcessRow, String> commandCol = new TableColumn<>("Command");
        commandCol.setCellValueFactory(new PropertyValueFactory<>("command"));
        commandCol.setPrefWidth(200);
        
        // Add columns to table
        table.getColumns().addAll(pidCol, nameCol, stateCol, priorityCol, 
                                  cpuCol, memoryCol, userCol, startTimeCol, commandCol);
        
        // Enable multi-selection
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        
        // Double-click to show details
        table.setRowFactory(tv -> {
            TableRow<ProcessRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showProcessDetails(row.getItem());
                }
            });
            return row;
        });
        
        return table;
    }
    
    private ContextMenu createContextMenu() {
        ContextMenu menu = new ContextMenu();
        
        MenuItem killItem = new MenuItem("Kill Process");
        MenuItem killTreeItem = new MenuItem("Kill Process Tree");
        MenuItem suspendItem = new MenuItem("Suspend");
        MenuItem resumeItem = new MenuItem("Resume");
        MenuItem setPriorityItem = new MenuItem("Set Priority");
        MenuItem setAffinityItem = new MenuItem("Set CPU Affinity");
        MenuItem detailsItem = new MenuItem("Properties");
        
        killItem.setOnAction(e -> killSelectedProcess());
        killTreeItem.setOnAction(e -> killProcessTree());
        suspendItem.setOnAction(e -> suspendSelectedProcess());
        resumeItem.setOnAction(e -> resumeSelectedProcess());
        setPriorityItem.setOnAction(e -> showSetPriorityDialog());
        setAffinityItem.setOnAction(e -> showSetAffinityDialog());
        detailsItem.setOnAction(e -> showProcessDetails(table.getSelectionModel().getSelectedItem()));
        
        menu.getItems().addAll(killItem, killTreeItem, new SeparatorMenuItem(),
                              suspendItem, resumeItem, new SeparatorMenuItem(),
                              setPriorityItem, setAffinityItem, new SeparatorMenuItem(),
                              detailsItem);
        
        return menu;
    }
    
    private HBox createStatusBar() {
        HBox statusBar = new HBox(10);
        statusBar.setPadding(new Insets(5));
        
        statusLabel = new Label("Processes: 0");
        Label cpuLabel = new Label("Total CPU: 0%");
        Label memoryLabel = new Label("Total Memory: 0 MB");
        
        statusBar.getChildren().addAll(statusLabel, new Separator(Orientation.VERTICAL),
                                       cpuLabel, new Separator(Orientation.VERTICAL),
                                       memoryLabel);
        
        return statusBar;
    }
    
    private void refreshProcessList() {
        Platform.runLater(() -> {
            processData.clear();
            
            // Get processes from service
            processService.getAllProcesses().forEach(process -> {
                processData.add(new ProcessRow(process));
            });
            
            // Update status
            statusLabel.setText("Processes: " + processData.size());
            
            // Apply filters
            filterProcesses();
        });
    }
    
    private void filterProcesses() {
        String searchText = searchField.getText().toLowerCase();
        String filterType = filterCombo.getValue();
        
        ObservableList<ProcessRow> filtered = FXCollections.observableArrayList();
        
        for (ProcessRow row : processData) {
            boolean matchesSearch = searchText.isEmpty() || 
                                   row.getName().toLowerCase().contains(searchText) ||
                                   row.getCommand().toLowerCase().contains(searchText);
            
            boolean matchesFilter = filterType.equals("All") ||
                                   (filterType.equals("Running") && row.getState().equals("RUNNING")) ||
                                   (filterType.equals("Suspended") && row.getState().equals("SUSPENDED")) ||
                                   (filterType.equals("System") && row.getUser().equals("SYSTEM")) ||
                                   (filterType.equals("User") && !row.getUser().equals("SYSTEM"));
            
            if (matchesSearch && matchesFilter) {
                filtered.add(row);
            }
        }
        
        table.setItems(filtered);
    }
    
    private void killSelectedProcess() {
        ProcessRow selected = table.getSelectionModel().getSelectedItem();
        if (selected != null) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Kill Process");
            confirm.setHeaderText("Kill process: " + selected.getName() + " (PID: " + selected.getPid() + ")?");
            confirm.setContentText("This action cannot be undone.");
            
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                processService.killProcess(selected.getPid());
                refreshProcessList();
            }
        }
    }
    
    private void showProcessDetails(ProcessRow processRow) {
        if (processRow == null) return;
        
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Process Details");
        dialog.setHeaderText("Process: " + processRow.getName() + " (PID: " + processRow.getPid() + ")");
        
        TabPane tabPane = new TabPane();
        
        // General tab
        Tab generalTab = new Tab("General");
        generalTab.setClosable(false);
        generalTab.setContent(createGeneralTab(processRow));
        
        // Performance tab
        Tab performanceTab = new Tab("Performance");
        performanceTab.setClosable(false);
        performanceTab.setContent(createPerformanceTab(processRow));
        
        // Threads tab
        Tab threadsTab = new Tab("Threads");
        threadsTab.setClosable(false);
        threadsTab.setContent(createThreadsTab(processRow));
        
        // Memory tab
        Tab memoryTab = new Tab("Memory");
        memoryTab.setClosable(false);
        memoryTab.setContent(createMemoryTab(processRow));
        
        tabPane.getTabs().addAll(generalTab, performanceTab, threadsTab, memoryTab);
        
        dialog.getDialogPane().setContent(tabPane);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefSize(600, 400);
        
        dialog.showAndWait();
    }
    
    private void startAutoRefresh() {
        refreshTimer = new Timer("ProcessTableRefresh", true);
        refreshTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                refreshProcessList();
            }
        }, 0, 2000); // Refresh every 2 seconds
    }
    
    public void stopAutoRefresh() {
        if (refreshTimer != null) {
            refreshTimer.cancel();
        }
    }
    
    @Data
    public static class ProcessRow {
        private final IntegerProperty pid;
        private final StringProperty name;
        private final StringProperty state;
        private final StringProperty priority;
        private final DoubleProperty cpuUsage;
        private final StringProperty memory;
        private final StringProperty user;
        private final StringProperty startTime;
        private final StringProperty command;
        
        public ProcessRow(Process process) {
            this.pid = new SimpleIntegerProperty(process.getPid());
            this.name = new SimpleStringProperty(process.getName());
            this.state = new SimpleStringProperty(process.getState().toString());
            this.priority = new SimpleStringProperty(process.getPriority().toString());
            this.cpuUsage = new SimpleDoubleProperty(process.getCpuUsage());
            this.memory = new SimpleStringProperty(formatMemory(process.getMemoryUsage()));
            this.user = new SimpleStringProperty(process.getUid() == 0 ? "SYSTEM" : "User");
            this.startTime = new SimpleStringProperty(formatTime(process.getStartTime()));
            this.command = new SimpleStringProperty(process.getCommand());
        }
        
        private String formatMemory(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
            if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
            return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        }
        
        private String formatTime(LocalDateTime time) {
            if (time == null) return "N/A";
            return time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        
        // Property getters for TableView
        public int getPid() { return pid.get(); }
        public String getName() { return name.get(); }
        public String getState() { return state.get(); }
        public String getPriority() { return priority.get(); }
        public double getCpuUsage() { return cpuUsage.get(); }
        public String getMemory() { return memory.get(); }
        public String getUser() { return user.get(); }
        public String getStartTime() { return startTime.get(); }
        public String getCommand() { return command.get(); }
    }
}