package com.rubberjam.eclipse.assistai.chat;

@Deprecated
public record Incoming( Type type, Object payload )
{
    public enum Type
    {
        CONTENT,
        FUNCTION_CALL
    }
}
