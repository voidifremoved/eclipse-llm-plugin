package com.rubberjam.eclipse.assistai.mcp.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class CompilationErrorPathMatchTest
{
    @Test
    public void pathsReferToSameFile_matchesProjectRelativeAndFull()
    {
        assertTrue( CodeAnalysisService.pathsReferToSameFile(
                "MyProject/src/com/example/Foo.java",
                "src/com/example/Foo.java" ) );
        assertTrue( CodeAnalysisService.pathsReferToSameFile(
                "src/com/example/Foo.java",
                "src/com/example/Foo.java" ) );
        assertFalse( CodeAnalysisService.pathsReferToSameFile(
                "src/com/example/Foo.java",
                "src/com/example/Bar.java" ) );
    }
}
