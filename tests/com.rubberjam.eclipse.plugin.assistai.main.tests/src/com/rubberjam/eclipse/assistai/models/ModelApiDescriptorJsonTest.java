package com.rubberjam.eclipse.assistai.models;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

public class ModelApiDescriptorJsonTest
{
    @Test
    public void roundTripPreservesFloatTemperature()
    {
        ModelApiDescriptor original = new ModelApiDescriptor(
                "uid",
                "openai",
                "https://api.openai.com/v1",
                "key",
                10,
                30,
                "gpt-4",
                1.0f,
                true,
                true );
        String json = ModelApiDescriptorRepository.toJson( original );
        List<ModelApiDescriptor> restored = ModelApiDescriptorRepository.fromJson( json );
        assertEquals( 1, restored.size() );
        assertEquals( 1.0f, restored.get( 0 ).temperature(), 0.001f );
        assertEquals( 1.0f, restored.get( 0 ).scaledTemperature().orElseThrow(), 0.001f );
    }
}
