package com.rubberjam.eclipse.assistai.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.rubberjam.eclipse.assistai.mcp.annotations.Tool;
import com.rubberjam.eclipse.assistai.mcp.annotations.ToolParam;

public class ToolExecutorCoercionTest
{
    @Test
    public void mapArgumentsCoercesBooleanToStringParameter() throws Exception
    {
        Method method = CoercionProbe.class.getDeclaredMethod( "probe", String.class );
        ToolExecutor executor = new ToolExecutor( new CoercionProbe() );
        Object[] args = executor.mapArguments( method, Map.of( "value", Boolean.FALSE ) );
        assertEquals( "false", args[0] );
    }

    @Test
    public void coerceArgumentConvertsNumberToString()
    {
        assertEquals( "3", ToolExecutor.coerceArgument( 3, String.class ) );
    }

    private static final class CoercionProbe
    {
        @Tool( name = "probe", description = "probe", type = "object" )
        public void probe(
                @ToolParam( name = "value", description = "value", required = true ) String value )
        {
            // test hook
        }
    }
}
