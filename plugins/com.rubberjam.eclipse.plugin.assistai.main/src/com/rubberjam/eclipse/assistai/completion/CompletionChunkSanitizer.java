package com.rubberjam.eclipse.assistai.completion;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Strips markdown code fences from inline completion chunks.
 */
final class CompletionChunkSanitizer
{
    private CompletionChunkSanitizer()
    {
    }

    static String sanitize( String rawChunk, AtomicBoolean markdownTruncated )
    {
        if ( rawChunk == null || rawChunk.isEmpty() )
        {
            return "";
        }

        String chunk = rawChunk;

        int fenceIndex = chunk.indexOf( "```" );
        if ( fenceIndex >= 0 )
        {
            markdownTruncated.set( true );
            chunk = chunk.substring( 0, fenceIndex );
        }

        chunk = chunk.replace( "~~~", "" );

        return chunk;
    }
}
