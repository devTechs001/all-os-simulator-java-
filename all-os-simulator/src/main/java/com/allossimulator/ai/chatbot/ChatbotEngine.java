package com.allossimulator.ai.chatbot;

import com.allossimulator.ai.nlp.NLPProcessor;
import com.allossimulator.ai.ml.ModelLoader;

public class ChatbotEngine {
    
    private ConversationManager conversationManager;
    private IntentClassifier intentClassifier;
    private ResponseGenerator responseGenerator;
    private ContextManager contextManager;
    private NLPProcessor nlpProcessor;
    
    @Autowired
    private ModelLoader modelLoader;
    
    public ChatbotEngine() {
        initialize();
    }
    
    private void initialize() {
        // Load pre-trained models
        var chatModel = modelLoader.loadModel("chatbot/gpt2-medium");
        var intentModel = modelLoader.loadModel("intent/bert-classifier");
        
        nlpProcessor = new NLPProcessor();
        conversationManager = new ConversationManager();
        intentClassifier = new IntentClassifier(intentModel);
        responseGenerator = new ResponseGenerator(chatModel);
        contextManager = new ContextManager();
    }
    
    public ChatResponse processMessage(String message, String userId) {
        // Process natural language
        var processedText = nlpProcessor.process(message);
        
        // Get conversation context
        var context = contextManager.getContext(userId);
        
        // Classify intent
        var intent = intentClassifier.classify(processedText, context);
        
        // Generate response
        var response = responseGenerator.generate(intent, context);
        
        // Update context
        contextManager.updateContext(userId, message, response);
        
        // Store in conversation history
        conversationManager.addToHistory(userId, message, response);
        
        return new ChatResponse(response, intent, context);
    }
    
    public void trainOnConversations() {
        // Continuous learning from user interactions
        var conversations = conversationManager.getAllConversations();
        responseGenerator.finetune(conversations);
    }
}