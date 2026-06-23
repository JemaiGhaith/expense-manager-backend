package com.coralio.chatbotmicroservice.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VectorStoreConfig {

    // REMOVE THIS - it's already defined in OllamaConfig
    // @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    // private String baseUrl;
    //
    // @Bean
    // public OllamaApi ollamaApi() {
    //     return new OllamaApi(baseUrl);
    // }

    @Bean
    public EmbeddingModel embeddingModel(OllamaApi ollamaApi) {  // This will use the bean from OllamaConfig
        return OllamaEmbeddingModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(
                        OllamaOptions.builder()
                                .model("nomic-embed-text")  // You might want to make this configurable
                                .build()
                )
                .build();
    }

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}