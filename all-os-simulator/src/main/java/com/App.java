package com.allossimulator;

import com.allossimulator.core.kernel.Kernel;
import com.allossimulator.core.boot.BootLoader;
import com.allossimulator.ai.core.AIEngine;
import com.allossimulator.view.MainView;
import javafx.application.Application;
import javafx.stage.Stage;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class App extends Application {
    
    private static Kernel kernel;
    private static AIEngine aiEngine;
    private static BootLoader bootLoader;
    
    public static void main(String[] args) {
        // Initialize Spring Boot backend
        SpringApplication.run(App.class, args);
        
        // Launch JavaFX frontend
        launch(args);
    }
    
    @Override
    public void start(Stage primaryStage) throws Exception {
        // Initialize boot sequence
        bootLoader = new BootLoader();
        bootLoader.showSplashScreen(primaryStage);
        
        // Initialize kernel
        kernel = new Kernel();
        kernel.initialize();
        
        // Initialize AI Engine
        aiEngine = new AIEngine();
        aiEngine.loadModels();
        aiEngine.startServices();
        
        // Boot the selected OS
        bootLoader.bootOS(primaryStage, kernel, aiEngine);
    }
    
    @Override
    public void stop() throws Exception {
        // Graceful shutdown
        if (kernel != null) kernel.shutdown();
        if (aiEngine != null) aiEngine.shutdown();
        super.stop();
    }
}