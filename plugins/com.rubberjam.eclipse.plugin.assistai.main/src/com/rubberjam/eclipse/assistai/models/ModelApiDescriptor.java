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
         float temperature,
         boolean vision,
         boolean functionCalling
         ) {

    public static final float TEMPERATURE_NOT_SUPPORTED = -1f;
    
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
     * Returns the API temperature (typically 0.0–2.0). {@link #TEMPERATURE_NOT_SUPPORTED} is omitted.
     * Legacy preferences stored 0–10 integers; values above 2.0 are scaled down by 10.
     */
    public Optional<Float> scaledTemperature()
    {
        if ( temperature < 0 )
        {
            return Optional.empty();
        }
        if ( temperature > 2.0f )
        {
            return Optional.of( temperature / 10.0f );
        }
        return Optional.of( temperature );
    }
    
} 
