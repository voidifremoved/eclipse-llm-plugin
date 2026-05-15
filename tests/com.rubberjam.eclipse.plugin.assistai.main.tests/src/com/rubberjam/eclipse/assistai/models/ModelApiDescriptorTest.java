package com.rubberjam.eclipse.assistai.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class ModelApiDescriptorTest
{
    @Test
    public void scaledTemperatureReturnsEmptyWhenNotSupported()
    {
        ModelApiDescriptor descriptor = descriptorWithTemperature( ModelApiDescriptor.TEMPERATURE_NOT_SUPPORTED );
        assertTrue( descriptor.scaledTemperature().isEmpty() );
    }

    @Test
    public void scaledTemperatureScalesZeroToTenRange()
    {
        ModelApiDescriptor descriptor = descriptorWithTemperature( 7 );
        assertEquals( 0.7f, descriptor.scaledTemperature().orElseThrow(), 0.001f );
    }

    private static ModelApiDescriptor descriptorWithTemperature( int temperature )
    {
        return new ModelApiDescriptor(
                "uid",
                "openai",
                "https://api.example.com",
                "key",
                10,
                30,
                "model",
                temperature,
                false,
                false );
    }
}
