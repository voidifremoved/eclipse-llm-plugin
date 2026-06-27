package com.rubberjam.eclipse.assistai.springai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

public class AssistAiMcpToolNamesTest
{
    @Test
    public void prefixed_joinsServerAndTool()
    {
        assertTrue( "eclipse-ide__getCompilationErrors"
                .equals( AssistAiMcpToolNames.prefixed( "eclipse-ide", "getCompilationErrors" ) ) );
    }

    @Test
    public void bareToolName_stripsServerPrefix()
    {
        assertTrue( "getCompilationErrors"
                .equals( AssistAiMcpToolNames.bareToolName( "eclipse-ide__getCompilationErrors" ) ) );
        assertTrue( "getSource".equals( AssistAiMcpToolNames.bareToolName( "getSource" ) ) );
    }

    @Test
    public void matchesAllowed_exactAndBareToolName()
    {
        Set<String> allowed = Set.of( "eclipse-ide__getCompilationErrors", "memory__store" );
        assertTrue( AssistAiMcpToolNames.matchesAllowed( "eclipse-ide__getCompilationErrors", allowed ) );
        assertTrue( AssistAiMcpToolNames.matchesAllowed( "getCompilationErrors", allowed ) );
        assertFalse( AssistAiMcpToolNames.matchesAllowed( "getSource", allowed ) );
        assertFalse( AssistAiMcpToolNames.matchesAllowed( "other__getCompilationErrors", allowed ) );
    }
}
