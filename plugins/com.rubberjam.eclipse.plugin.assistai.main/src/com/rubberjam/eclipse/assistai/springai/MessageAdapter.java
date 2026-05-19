package com.rubberjam.eclipse.assistai.springai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.model.ModelOptionsUtils;

import com.rubberjam.eclipse.assistai.chat.Attachment;
import com.rubberjam.eclipse.assistai.chat.Attachment.FileContentAttachment;
import com.rubberjam.eclipse.assistai.chat.Attachment.ImageAttachment;
import com.rubberjam.eclipse.assistai.chat.ChatMessage;
import com.rubberjam.eclipse.assistai.chat.FunctionCall;

public class MessageAdapter
{

    public static Message toSpringAi( ChatMessage chatMessage )
    {
        String role = chatMessage.getRole();
        String content = chatMessage.getContent();

        if ( "user".equalsIgnoreCase( role ) )
        {
            content = appendAttachmentContent( chatMessage, content );
            return new UserMessage( content );
        }
        if ( "assistant".equalsIgnoreCase( role ) )
        {
            return toAssistantMessage( chatMessage, content );
        }
        if ( "system".equalsIgnoreCase( role ) )
        {
            return new SystemMessage( content );
        }
        if ( "tool".equalsIgnoreCase( role ) )
        {
            return toAgentToolHistoryUserMessage( chatMessage, content );
        }
        return new UserMessage( content );
    }

    /**
     * Tool UI bubbles are sent to the model as user messages so providers (OpenAI) are not given
     * orphan {@link ToolResponseMessage}s without a preceding assistant {@code tool_calls} block.
     */
    public static UserMessage toAgentToolHistoryUserMessage( ChatMessage chatMessage, String content )
    {
        String toolName = chatMessage.getName();
        if ( toolName == null || toolName.isBlank() )
        {
            toolName = "tool";
        }
        String body = content != null ? content : "";
        return new UserMessage( "[Tool result: " + toolName + "]\n" + body );
    }

    /**
     * Converts a persisted tool bubble to a Spring AI tool response for multi-turn context.
     */
    public static Message toToolResponseMessage( ChatMessage chatMessage, String toolCallId, String toolName, String responseText )
    {
        String id = toolCallId != null ? toolCallId : chatMessage.getId();
        String name = toolName != null ? toolName : chatMessage.getName();
        if ( name == null || name.isBlank() )
        {
            name = "tool";
        }
        String data = responseText != null ? responseText : "";
        ToolResponseMessage.ToolResponse response = new ToolResponseMessage.ToolResponse( id, name, data );
        return ToolResponseMessage.builder().responses( List.of( response ) ).build();
    }

    private static String appendAttachmentContent( ChatMessage chatMessage, String content )
    {
        List<Attachment> attachments = chatMessage.getAttachments();
        if ( attachments == null )
        {
            return content;
        }
        StringBuilder builder = new StringBuilder( content != null ? content : "" );
        for ( Attachment att : attachments )
        {
            if ( att instanceof ImageAttachment )
            {
                // Images are not inlined into text for Spring AI here yet
            }
            else if ( att instanceof FileContentAttachment fileAtt )
            {
                builder.append( "\n\n" ).append( fileAtt.toChatMessageContent() );
            }
        }
        return builder.toString();
    }

    private static Message toAssistantMessage( ChatMessage chatMessage, String content )
    {
        FunctionCall functionCall = chatMessage.getFunctionCall();
        if ( functionCall == null )
        {
            return new AssistantMessage( content );
        }
        String argsJson = functionCall.arguments() == null
                ? "{}"
                : ModelOptionsUtils.toJsonString( functionCall.arguments() );
        AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall(
                functionCall.id(),
                "function",
                functionCall.name(),
                argsJson );
        List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
        toolCalls.add( toolCall );
        return AssistantMessage.builder()
                .content( content )
                .toolCalls( toolCalls )
                .build();
    }

    public static ChatMessage fromSpringAi( Message springAiMessage )
    {
        String messageType;
        try
        {
            messageType = springAiMessage.getMessageType().getValue();
        }
        catch ( Exception e )
        {
            messageType = "assistant";
        }
        ChatMessage chatMessage = new ChatMessage( UUID.randomUUID().toString(), messageType );

        String text = springAiMessage.toString();
        chatMessage.setContent( text != null ? text : "" );

        if ( springAiMessage instanceof AssistantMessage assistantMessage && assistantMessage.hasToolCalls() )
        {
            List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();
            if ( toolCalls != null && !toolCalls.isEmpty() )
            {
                AssistantMessage.ToolCall firstCall = toolCalls.get( 0 );
                Map<String, Object> args = ModelOptionsUtils.jsonToMap( firstCall.arguments() );
                FunctionCall funcCall = new FunctionCall(
                        firstCall.id(),
                        firstCall.name(),
                        args,
                        null );
                chatMessage.setFunctionCall( funcCall );
            }
        }

        return chatMessage;
    }
}
