package com.rubberjam.eclipse.assistai.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class AgentToolPolicyTest
{
    @Test
    public void tierForServer_classifiesBuiltins()
    {
        AgentToolPolicy policy = new AgentToolPolicy();
        assertEquals( AgentToolTier.WORKSPACE, policy.tierForServer( "eclipse-ide", true ) );
        assertEquals( AgentToolTier.WEB, policy.tierForServer( "duck-duck-search", true ) );
        assertEquals( AgentToolTier.UTILITY, policy.tierForServer( "memory", true ) );
        assertEquals( AgentToolTier.USER, policy.tierForServer( "my-custom-server", false ) );
    }

    @Test
    public void isWorkspacePresetServer()
    {
        AgentToolPolicy policy = new AgentToolPolicy();
        assertTrue( policy.isWorkspacePresetServer( "eclipse-coder" ) );
        assertTrue( policy.isWorkspacePresetServer( "memory" ) );
        assertFalse( policy.isWorkspacePresetServer( "duck-duck-search" ) );
    }
}
