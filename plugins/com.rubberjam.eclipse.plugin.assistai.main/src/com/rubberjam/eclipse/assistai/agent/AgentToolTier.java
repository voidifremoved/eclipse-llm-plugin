package com.rubberjam.eclipse.assistai.agent;

/**
 * MCP server categories for agent tool allowlisting.
 */
public enum AgentToolTier
{
    /** Eclipse workspace: ide, coder, runner, context, git, pde */
    WORKSPACE,
    /** In-IDE helpers: memory (and optionally time) */
    UTILITY,
    /** External web: search, page reader */
    WEB,
    /** User-configured MCP servers (stdio/HTTP) */
    USER
}
