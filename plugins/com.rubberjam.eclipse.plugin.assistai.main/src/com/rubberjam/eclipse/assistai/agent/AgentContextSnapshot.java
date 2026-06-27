package com.rubberjam.eclipse.assistai.agent;

import java.util.ArrayList;
import java.util.List;

/**
 * Workspace context shown in the agent context panel (Phase 3.2).
 */
public final class AgentContextSnapshot
{
    private final String projectName;

    private final String filePath;

    private final String fileName;

    private final String selectionPreview;

    private final List<String> cachedResourceLabels;

    public AgentContextSnapshot(
            String projectName,
            String filePath,
            String fileName,
            String selectionPreview,
            List<String> cachedResourceLabels )
    {
        this.projectName = projectName != null ? projectName : "";
        this.filePath = filePath != null ? filePath : "";
        this.fileName = fileName != null ? fileName : "";
        this.selectionPreview = selectionPreview != null ? selectionPreview : "";
        if ( cachedResourceLabels == null )
        {
            this.cachedResourceLabels = List.of();
        }
        else
        {
            this.cachedResourceLabels = List.copyOf( cachedResourceLabels );
        }
    }

    public static AgentContextSnapshot empty()
    {
        return new AgentContextSnapshot( "", "", "", "", new ArrayList<>() );
    }

    public String projectName()
    {
        return projectName;
    }

    public String filePath()
    {
        return filePath;
    }

    public String fileName()
    {
        return fileName;
    }

    public String selectionPreview()
    {
        return selectionPreview;
    }

    public List<String> cachedResourceLabels()
    {
        return cachedResourceLabels;
    }
}
