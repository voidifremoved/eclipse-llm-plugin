package com.rubberjam.eclipse.assistai.springai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class OpenAiCompatibleChatModelProviderTest
{
    @Test
    public void toBaseUrl_appendsV1ForMistralHost()
    {
        assertEquals( "https://api.mistral.ai/v1", OpenAiCompatibleChatModelProvider.toBaseUrl( "https://api.mistral.ai" ) );
    }

    @Test
    public void toBaseUrl_stripsChatCompletionsPath()
    {
        assertEquals( "https://api.groq.com/openai/v1",
                OpenAiCompatibleChatModelProvider.toBaseUrl( "https://api.groq.com/openai/v1/chat/completions" ) );
    }

    @Test
    public void toBaseUrl_normalizesOpenAiResponsesUrl()
    {
        assertEquals( "https://api.openai.com/v1",
                OpenAiCompatibleChatModelProvider.toBaseUrl( "https://api.openai.com/v1/responses" ) );
    }
}
