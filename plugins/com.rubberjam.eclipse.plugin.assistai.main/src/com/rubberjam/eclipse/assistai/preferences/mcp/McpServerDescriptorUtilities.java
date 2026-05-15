package com.rubberjam.eclipse.assistai.preferences.mcp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rubberjam.eclipse.assistai.mcp.McpServerDescriptor;

/**
 * Utilities for serializing and deserializing MCP Server descriptors
 */
public class McpServerDescriptorUtilities {

    private static final ObjectMapper objectMapper = new ObjectMapper();

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
            // In case of error, return empty JSON array
            return "[]";
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
            return new ArrayList<>();
        }

        try {
            return objectMapper.readValue(json, new TypeReference<List<McpServerDescriptor>>() {});
        } catch (Exception e) {
            try {
                return objectMapper.readValue(
                        migrateLegacyJson( json ),
                        new TypeReference<List<McpServerDescriptor>>() {} );
            } catch (Exception migrationError) {
                return new ArrayList<>();
            }
        }
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
