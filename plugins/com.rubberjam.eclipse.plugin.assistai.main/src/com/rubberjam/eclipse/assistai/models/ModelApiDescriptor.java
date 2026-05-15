package com.rubberjam.eclipse.assistai.models;

import java.time.Duration;
import java.util.Optional;

/**
 * 
 */
public record ModelApiDescriptor(
         String uid,
         String apiType,
         String apiUrl,
         String apiKey,
         int connectionTimeoutSeconds,
         int requestTimeoutSeconds,
         String modelName,
         int temperature,
         boolean vision,
         boolean functionCalling
         ) {
   
    public static final int TEMPERATURE_NOT_SUPPORTED = -1;
    
    public static ModelApiDescriptor copyWithUid( String uid, ModelApiDescriptor stub) {
        return new ModelApiDescriptor(
                    uid,
                    stub.apiType(),
                    stub.apiUrl(),
                    stub.apiKey(),
                    stub.connectionTimeoutSeconds(),
                    stub.requestTimeoutSeconds(),
                    stub.modelName(),
                    stub.temperature(),
                    stub.vision(),
                    stub.functionCalling()
                );
    };

    /** Returns the connection timeout as a Duration, defaulting to 10s if unset (0). */
    public Duration connectionTimeout()
    {
        return Duration.ofSeconds(connectionTimeoutSeconds > 0 ? connectionTimeoutSeconds : 10);
    }

    /** Returns the request timeout as a Duration, defaulting to 30s if unset (0). */
    public Duration requestTimeout()
    {
        return Duration.ofSeconds(requestTimeoutSeconds > 0 ? requestTimeoutSeconds : 30);
    }
    
    /**
     * UI stores temperature on a 0–10 scale; {@link #TEMPERATURE_NOT_SUPPORTED} is -1.
     */
    public Optional<Float> scaledTemperature()
    {
        if ( temperature < 0 )
        {
            return Optional.empty();
        }
        return Optional.of( temperature / 10.0f );
    }
    
} 
