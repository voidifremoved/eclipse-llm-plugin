package com.rubberjam.eclipse.assistai.springai;

/**
 * One streamed fragment of an assistant reply (Spring AI–free API for the agent UI).
 */
public record AgentStreamChunk( String text, String thinking )
{
    public AgentStreamChunk( String text )
    {
        this( text, null );
    }

    public boolean hasText()
    {
        return text != null && !text.isEmpty();
    }

    public boolean hasThinking()
    {
        return thinking != null && !thinking.isEmpty();
    }
}
