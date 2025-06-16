package org.protege.editor.owl.ui.renderer;
/*
 * Copyright (C) 2007, University of Manchester
 *
 *
 */


import javax.annotation.Nonnull;

/**
 * Author: Matthew Horridge<br>
 * The University Of Manchester<br>
 * Medical Informatics Group<br>
 * Date: 20-Jun-2006<br><br>

 * matthew.horridge@cs.man.ac.uk<br>
 * www.cs.man.ac.uk/~horridgm<br><br>
 */
public class RenderingEscapeUtils {

    public enum RenderingEscapeSetting {
        ESCAPED_RENDERING,
        UNESCAPED_RENDERING
    }

    /**
     * Produces an "escaped" rendering.  If the original rendering contains
     * spaces, braces, brackets or commas, and various other symbols used as delimeters in the
     * Manchester syntax parser then the returned value is the original rendering enclosed in
     * single quotes.
     * @param originalRendering The rendering to be escaped
     * @return The escaped rendering.
     */
    public static String getEscapedRendering(String originalRendering) {
    	String rendering = originalRendering;
    	rendering = rendering.replace("\\", "\\\\");
    	rendering = rendering.replace("'", "\\'");
    	rendering = rendering.replace("\"", "\\\"");
        if (originalRendering.indexOf(' ') != -1
        		|| originalRendering.indexOf('\\') != -1
                || originalRendering.indexOf(',') != -1
                || originalRendering.indexOf('<') != -1
                || originalRendering.indexOf('>') != -1
                || originalRendering.indexOf('=') != -1
                || originalRendering.indexOf('^') != -1
                || originalRendering.indexOf('@') != -1
                || originalRendering.indexOf('{') != -1
                || originalRendering.indexOf('}') != -1
                || originalRendering.indexOf('[') != -1
                || originalRendering.indexOf(']') != -1
                || originalRendering.indexOf('(') != -1
                || originalRendering.indexOf(')') != -1) {
        	rendering = "'" + rendering + "'";
        }
        return rendering;
    }

    @Nonnull
    public static String unescape(@Nonnull String rendering) {
        if(rendering.startsWith("'") && rendering.endsWith("'")) {
        	rendering = rendering.substring(1, rendering.length() - 1);
        }
        rendering = rendering.replace("\\\"", "\"");
    	rendering = rendering.replace("\\'", "'");
    	rendering = rendering.replace("\\\\", "\\");
        return rendering;
    }

}
