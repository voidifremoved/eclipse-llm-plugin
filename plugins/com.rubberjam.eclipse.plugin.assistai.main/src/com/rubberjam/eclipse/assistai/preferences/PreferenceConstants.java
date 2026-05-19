package com.rubberjam.eclipse.assistai.preferences;

/**
 * Constant definitions for plug-in preferences
 */
public class PreferenceConstants
{
    public static final String ASSISTAI_CHAT_MODEL = "AssistaAISelectedModel";
    public static final String ASSISTAI_DEFINED_MODELS = "AssistAIDefinedModels";
    public static final String ASSISTAI_AGENT_TABS = "AssistAIAgentTabs";
    
    // MCP Server preferences
    public static final String ASSISTAI_DEFINED_MCP_SERVERS = "AssistAIDefinedMCPServers";
    public static final String ASSISTAI_SELECTED_MCP_SERVER = "AssistAISelectedMCPServer";
    
    // MCP Http
    public static final String ASSISTAI_MCP_HTTP_HOSTNAME = "AssistAIMcpHttpHostname";
    public static final String ASSISTAI_MCP_HTTP_PORT = "AssistAIMcpHttpPort";
    public static final String ASSISTAI_MCP_HTTP_AUTH_TOKEN = "AssistAIMcpHttpToken";
    public static final String ASSISTAI_MCP_HTTP_ENABLED = "AssistAIMcpHttpEnabled";
    
    // Code Completion preferences
    public static final String ASSISTAI_COMPLETION_ENABLED = "AssistAICompletionEnabled";
    public static final String ASSISTAI_COMPLETION_MODEL = "AssistAICompletionModel";
    public static final String ASSISTAI_COMPLETION_TIMEOUT_SECONDS = "AssistAICompletionTimeoutSeconds";
    public static final String ASSISTAI_COMPLETION_HOTKEY = "AssistAICompletionHotkey";
    
    // Default hotkey: Alt+/ (cross-platform friendly)
    public static final String ASSISTAI_COMPLETION_HOTKEY_DEFAULT = "Alt+/";

    // AI Ignore preferences
    public static final String ASSISTAI_IGNORE_FILENAME = "AssistAIIgnoreFilename";
    public static final String ASSISTAI_GLOBAL_EXCLUDE_PATTERNS = "AssistAIGlobalExcludePatterns";

    /** Agent: allow duck-duck-search and webpage-reader MCP tools */
    public static final String ASSISTAI_AGENT_ALLOW_WEB_TOOLS = "AssistAIAgentAllowWebTools";

    /** Agent: append bundled Eclipse workflow hints to the system prompt */
    public static final String ASSISTAI_AGENT_USE_ECLIPSE_SKILLS = "AssistAIAgentUseEclipseSkills";

    /** Agent: after eclipse-coder tool success, auto-call getCompilationErrors */
    public static final String ASSISTAI_AGENT_VERIFY_AFTER_EDIT = "AssistAIAgentVerifyAfterEdit";

    /** Agent: max tool-call rounds per user message */
    public static final String ASSISTAI_AGENT_MAX_TOOL_ROUNDS = "AssistAIAgentMaxToolRounds";
}
