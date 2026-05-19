package com.rubberjam.eclipse.assistai.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

public class AgentTaskChecklistParserTest
{
    @Test
    public void parse_extractsMarkdownChecklistItems()
    {
        String markdown = """
                Plan:
                - [ ] Read errors
                - [x] Apply patch
                - [ ] Re-run compile
                """;
        List<AgentTaskItem> items = AgentTaskChecklistParser.parse( markdown );
        assertEquals( 3, items.size() );
        assertEquals( "Read errors", items.get( 0 ).text() );
        assertFalse( items.get( 0 ).isDone() );
        assertEquals( "Apply patch", items.get( 1 ).text() );
        assertTrue( items.get( 1 ).isDone() );
    }

    @Test
    public void markCompletedByTool_matchesCompilationKeywords()
    {
        List<AgentTaskItem> items = new ArrayList<>();
        items.add( new AgentTaskItem( "Verify compilation", false ) );
        AgentTaskChecklistParser.markCompletedByTool( items, "eclipse-ide__getCompilationErrors" );
        assertTrue( items.get( 0 ).isDone() );
    }

    @Test
    public void markCompletedByTool_matchesEditKeywords()
    {
        List<AgentTaskItem> items = new ArrayList<>();
        items.add( new AgentTaskItem( "Fix imports in file", false ) );
        AgentTaskChecklistParser.markCompletedByTool( items, "eclipse-coder__organizeImports" );
        assertTrue( items.get( 0 ).isDone() );
    }
}
