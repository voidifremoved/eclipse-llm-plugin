package com.rubberjam.eclipse.assistai.prompt;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.eclipse.swt.graphics.ImageData;

import com.rubberjam.eclipse.assistai.chat.Attachment;
import com.rubberjam.eclipse.assistai.chat.ChatMessage;
import com.rubberjam.eclipse.assistai.tools.ImageUtilities;

public class ChatMessageUtilities
{
    public static String toMarkdownContent( ChatMessage message )
    {
        String content = message.getContent();

        List<ImageData> images = message.getAttachments()
                .stream()
                .map( Attachment::getImageData )
                .filter( Objects::nonNull )
                .collect( Collectors.toList() );

        List<String> textParts = message.getAttachments()
                .stream()
                .map( Attachment::toMarkdownContent )
                .filter( Objects::nonNull )
                .collect( Collectors.toList() );

        if (!images.isEmpty())
        {
            content += "\n" + images.stream()
                    .map( ImageUtilities::toBase64Jpeg )
                    .map( data -> "![image](data:image/jpeg;base64," + data + ")" )
                    .collect( Collectors.joining( "\n" ) );
        }

        if (!textParts.isEmpty())
        {
            content += "\n" + textParts.stream().collect( Collectors.joining( "\n" ) ) + "\n";
        }

        return content;
    }
}
