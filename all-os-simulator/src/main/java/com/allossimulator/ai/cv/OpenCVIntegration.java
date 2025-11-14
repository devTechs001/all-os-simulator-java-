package com.allossimulator.ai.cv;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;

public class OpenCVIntegration {
    
    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }
    
    private Net objectDetectionNet;
    private Net faceRecognitionNet;
    private CascadeClassifier faceDetector;
    private Map<String, Mat> userFaceTemplates;
    
    public OpenCVIntegration() {
        initialize();
    }
    
    private void initialize() {
        // Load YOLO for object detection
        String modelWeights = "models/yolov4.weights";
        String modelConfig = "models/yolov4.cfg";
        objectDetectionNet = Dnn.readNetFromDarknet(modelConfig, modelWeights);
        
        // Load face recognition model
        faceRecognitionNet = Dnn.readNetFromTorch("models/face_recognition.t7");
        
        // Load cascade classifier
        faceDetector = new CascadeClassifier("models/haarcascade_frontalface.xml");
        
        userFaceTemplates = new HashMap<>();
    }
    
    public List<DetectedObject> detectObjects(Mat image) {
        Mat blob = Dnn.blobFromImage(image, 1/255.0, new Size(416, 416), 
                                     new Scalar(0), true, false);
        objectDetectionNet.setInput(blob);
        
        List<Mat> outputs = new ArrayList<>();
        objectDetectionNet.forward(outputs, getOutputLayers());
        
        return postProcessDetections(outputs, image);
    }
    
    public SecurityResult performBiometricLogin(Mat faceImage) {
        // Detect face
        MatOfRect faceDetections = new MatOfRect();
        faceDetector.detectMultiScale(faceImage, faceDetections);
        
        if (faceDetections.toArray().length == 0) {
            return new SecurityResult(false, "No face detected");
        }
        
        // Extract face features
        Mat faceFeatures = extractFaceFeatures(faceImage, faceDetections.toArray()[0]);
        
        // Match against registered users
        String matchedUser = matchFaceWithUsers(faceFeatures);
        
        if (matchedUser != null) {
            return new SecurityResult(true, "Login successful", matchedUser);
        }
        
        return new SecurityResult(false, "Face not recognized");
    }
    
    private Mat extractFaceFeatures(Mat image, Rect face) {
        Mat faceROI = new Mat(image, face);
        Mat blob = Dnn.blobFromImage(faceROI, 1.0, new Size(224, 224), 
                                     new Scalar(104, 177, 123), false, false);
        faceRecognitionNet.setInput(blob);
        return faceRecognitionNet.forward();
    }
    
    public void enableAugmentedReality(VideoCapture camera) {
        Mat frame = new Mat();
        while (camera.read(frame)) {
            // Detect objects in real-time
            List<DetectedObject> objects = detectObjects(frame);
            
            // Overlay AR information
            for (DetectedObject obj : objects) {
                drawAROverlay(frame, obj);
            }
            
            // Display enhanced frame
            displayFrame(frame);
        }
    }
    
    private void drawAROverlay(Mat frame, DetectedObject obj) {
        // Draw bounding box
        Imgproc.rectangle(frame, obj.getBoundingBox(), new Scalar(0, 255, 0), 2);
        
        // Add label with AI-generated description
        String label = generateObjectDescription(obj);
        Imgproc.putText(frame, label, obj.getPosition(), 
                       Core.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(255, 255, 255), 2);
    }
}