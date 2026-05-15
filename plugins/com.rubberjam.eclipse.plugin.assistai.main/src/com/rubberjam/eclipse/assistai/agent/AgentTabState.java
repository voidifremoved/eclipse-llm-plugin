package com.rubberjam.eclipse.assistai.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.rubberjam.eclipse.assistai.chat.Attachment;

/**
 * Per-tab UI state for parallel agent conversations (stream, attachments, in-flight reply).
 */
public class AgentTabState
{
    final List<Attachment> attachments = new ArrayList<>();

    /** Avoids hard dependency on reactor in OSGi manifests. */
    Object currentStream;

    boolean generating;

    String pendingAssistantMessageId;

    final StringBuilder pendingAssistantHtml = new StringBuilder();

    final Map<String, String> toolMessageIds = new HashMap<>();
}
