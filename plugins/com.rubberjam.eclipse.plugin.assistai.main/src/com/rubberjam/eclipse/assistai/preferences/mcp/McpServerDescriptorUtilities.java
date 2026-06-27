package com.rubberjam.eclipse.assistai.preferences.mcp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rubberjam.eclipse.assistai.mcp.McpServerDescriptor;
import com.rubberjam.eclipse.assistai.preferences.mcp.McpServerPreferencesLog;

/**
 * Utilities for serializing and deserializing MCP Server descriptors
 */
public class McpServerDescriptorUtilities {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        objectMapper.configure( DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false );
    }

    /**
     * Convert a list of MCP Server descriptors to JSON
     *
     * @param descriptors the descriptors to convert
     * @return JSON string
     */
    public static String toJson(List<McpServerDescriptor> descriptors) {
        try {
            return objectMapper.writeValueAsString(descriptors);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException( "Failed to serialize MCP server preferences", e );
        }
    }

    /**
     * Convert a list of MCP Server descriptors to JSON
     *
     * @param descriptors the descriptors to convert
     * @return JSON string
     */
    public static String toJson(McpServerDescriptor... descriptors) {
        return toJson(Arrays.asList(descriptors));
    }

    /**
     * Convert JSON to a list of MCP Server descriptors
     *
     * @param json the JSON to convert
     * @return list of descriptors
     */
    public static List<McpServerDescriptor> fromJson(String json) {
        if (json == null || json.isEmpty()) {
            McpServerPreferencesLog.info( "fromJson: empty preference value" );
            return new ArrayList<>();
        }

        try {
            List<McpServerDescriptor> parsed = objectMapper.readValue( json, new TypeReference<List<McpServerDescriptor>>() {} );
            McpServerPreferencesLog.logDescriptors( "fromJson: parsed", parsed );
            return parsed;
        }
        catch ( Exception e )
        {
            McpServerPreferencesLog.warn( "fromJson: direct parse failed, trying migration. jsonLength="
                    + json.length() + " error=" + e.getMessage() );
            try
            {
                String migrated = migrateLegacyJson( json );
                List<McpServerDescriptor> parsed = objectMapper.readValue(
                        migrated,
                        new TypeReference<List<McpServerDescriptor>>() {} );
                McpServerPreferencesLog.logDescriptors( "fromJson: parsed after migration", parsed );
                return parsed;
            }
            catch ( Exception migrationError )
            {
                McpServerPreferencesLog.error( "fromJson: migration failed; returning empty list. jsonPreview="
                        + abbreviateJson( json ), migrationError );
                return new ArrayList<>();
            }
        }
    }

    private static String abbreviateJson( String json )
    {
        if ( json == null )
        {
            return "";
        }
        if ( json.length() <= 500 )
        {
            return json;
        }
        return json.substring( 0, 500 ) + "...";
    }

    /**
     * Ensures each stored server object has a {@code url} field so preferences saved
     * before HTTP MCP support still deserialize after {@link McpServerDescriptor} gained {@code url}.
     */
    private static String migrateLegacyJson( String json ) throws JsonProcessingException
    {
        JsonNode root = objectMapper.readTree( json );
        if ( !root.isArray() )
        {
            return "[]";
        }
        ArrayNode array = (ArrayNode) root;
        for ( JsonNode node : array )
        {
            if ( node.isObject() )
            {
                ObjectNode obj = (ObjectNode) node;
                if ( !obj.has( "url" ) )
                {
                    obj.put( "url", "" );
                }
            }
        }
        return objectMapper.writeValueAsString( root );
    }
}
