package com.rubberjam.eclipse.assistai.mcp.annotations;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention( RUNTIME )
@Target( METHOD )
public @interface Tool
{
    public String name() default "";
    public String description();
    /** Opt in only when a tool does not require SWT/editor access. */
    public boolean uiThread() default true;
    public String type() default "object";
}
