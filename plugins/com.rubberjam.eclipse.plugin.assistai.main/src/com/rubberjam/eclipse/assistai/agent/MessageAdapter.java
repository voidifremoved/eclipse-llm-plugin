package com.rubberjam.eclipse.assistai.agent;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;

import com.rubberjam.eclipse.assistai.chat.ChatMessage;
import com.rubberjam.eclipse.assistai.chat.FunctionCall;
import com.rubberjam.eclipse.assistai.chat.Attachment;
import com.rubberjam.eclipse.assistai.chat.Attachment.FileContentAttachment;
import com.rubberjam.eclipse.assistai.chat.Attachment.ImageAttachment;
import org.springframework.ai.model.ModelOptionsUtils;

public class MessageAdapter
{

    public static Message toSpringAi(ChatMessage chatMessage)
    {
        String role = chatMessage.getRole();
        String content = chatMessage.getContent();

        if ("user".equalsIgnoreCase(role))
        {
            List<Attachment> attachments = chatMessage.getAttachments();
            if (attachments != null) {
                for (Attachment att : attachments) {
                    if (att instanceof ImageAttachment) {
                        ImageAttachment imgAtt = (ImageAttachment) att;
                        // Ignoring for now
                    } else if (att instanceof FileContentAttachment) {
                        FileContentAttachment fileAtt = (FileContentAttachment) att;
                        content += "\n\n" + fileAtt.toChatMessageContent();
                    }
                }
            }
            return createProxy(MessageType.USER, content);
        }
        else if ("assistant".equalsIgnoreCase(role))
        {
            return createProxy(MessageType.ASSISTANT, content);
        }
        else if ("system".equalsIgnoreCase(role))
        {
            return createProxy(MessageType.SYSTEM, content);
        }

        return createProxy(MessageType.USER, content);
    }

    private static Message createProxy(MessageType type, String content) {
        return (Message) Proxy.newProxyInstance(
            Message.class.getClassLoader(),
            new Class[] { Message.class },
            new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    String name = method.getName();
                    if ("getMessageType".equals(name)) return type;
                    if ("getText".equals(name)) return content;
                    if ("getContent".equals(name)) return content;
                    if ("toString".equals(name)) return content;
                    if ("hasToolCalls".equals(name)) return false;
                    if ("getMetadata".equals(name)) return Collections.emptyMap();
                    return null;
                }
            }
        );
    }

    public static ChatMessage fromSpringAi(Message springAiMessage)
    {
        String messageType = null;
        try {
            messageType = springAiMessage.getMessageType().getValue();
        } catch(Exception e) {
            messageType = "assistant";
        }
        ChatMessage chatMessage = new ChatMessage(UUID.randomUUID().toString(), messageType);

        String text = null;
        try {
            Object obj = springAiMessage;
            Method getText = obj.getClass().getMethod("getText");
            text = (String) getText.invoke(obj);
        } catch (Exception e) {
            try {
                Object obj = springAiMessage;
                Method getContent = obj.getClass().getMethod("getContent");
                text = (String) getContent.invoke(obj);
            } catch (Exception e2) {
                text = ""; // Avoid toString() entirely to prevent OSGi verification issues on Message supertype Content
            }
        }

        try {
            Object obj = springAiMessage;
            Method hasToolCalls = obj.getClass().getMethod("hasToolCalls");
            if ((Boolean)hasToolCalls.invoke(obj)) {
                Method getToolCalls = obj.getClass().getMethod("getToolCalls");
                List<?> toolCalls = (List<?>) getToolCalls.invoke(obj);
                if (!toolCalls.isEmpty()) {
                    Object firstCall = toolCalls.get(0);
                    String id = (String) firstCall.getClass().getMethod("id").invoke(firstCall);
                    String name = (String) firstCall.getClass().getMethod("name").invoke(firstCall);
                    String argsStr = (String) firstCall.getClass().getMethod("arguments").invoke(firstCall);
                    Map<String, Object> args = ModelOptionsUtils.jsonToMap(argsStr);
                    FunctionCall funcCall = new FunctionCall(id, name, args, null);
                    chatMessage.setFunctionCall(funcCall);
                }
            }
        } catch (Exception e) {
            // Ignore if method not found
        }

        chatMessage.setContent(text == null ? "" : text);
        return chatMessage;
    }
}
