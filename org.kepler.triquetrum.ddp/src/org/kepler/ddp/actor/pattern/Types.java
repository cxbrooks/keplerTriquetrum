/* A class providing the Ptolemy types used between the pattern actors.
 *  
 * Copyright (c) 2011-2012 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2014-05-08 16:03:56 -0700 (Thu, 08 May 2014) $' 
 * '$Revision: 32711 $'
 * 
 * Permission is hereby granted, without written agreement and without
 * license or royalty fees, to use, copy, modify, and distribute this
 * software and its documentation for any purpose, provided that the above
 * copyright notice and the following two paragraphs appear in all copies
 * of this software.
 *
 * IN NO EVENT SHALL THE UNIVERSITY OF CALIFORNIA BE LIABLE TO ANY PARTY
 * FOR DIRECT, INDIRECT, SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES
 * ARISING OUT OF THE USE OF THIS SOFTWARE AND ITS DOCUMENTATION, EVEN IF
 * THE UNIVERSITY OF CALIFORNIA HAS BEEN ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 * THE UNIVERSITY OF CALIFORNIA SPECIFICALLY DISCLAIMS ANY WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE. THE SOFTWARE
 * PROVIDED HEREUNDER IS ON AN "AS IS" BASIS, AND THE UNIVERSITY OF
 * CALIFORNIA HAS NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES,
 * ENHANCEMENTS, OR MODIFICATIONS.
 *
 */
package org.kepler.ddp.actor.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ptolemy.data.expr.Parameter;
import ptolemy.data.type.ArrayType;
import ptolemy.data.type.BaseType;
import ptolemy.data.type.ObjectType;
import ptolemy.data.type.RecordType;
import ptolemy.data.type.Type;
import ptolemy.kernel.util.IllegalActionException;

/** A class providing the Ptolemy types used between the pattern actors.
 * 
 *  @author Daniel Crawl
 *  @version $Id: Types.java 32711 2014-05-08 23:03:56Z crawl $
 */
public class Types {

   
    /** Create the Ptolemy type for array of key values for specific key and value types. */
    public static Type createKeyValueArrayType(Type keyType, Type valueType) {
        return new ArrayType(new RecordType(new String[] {"key","value"},
                new Type[] {keyType, valueType}));    
    }
    
    /** Get the Ptolemy type for a key-value type string.
     *  @param the parameter containing the type string.
     *  @param typesStr the type string, e.g., "int string", "string long",
     *  where the first type is the key and the second type is the value.
     *  @return a Type for an array of records that have key and value fields
     *  whose types are specified by the typeStr parameter.  
     */
    public static Type getKeyValueType(Parameter parameter, String typesStr) throws IllegalActionException {
        
        Type[] types = getKeyValueTypes(parameter, typesStr);
        
        return Types.createKeyValueArrayType(types[0], types[1]);
    }
    
    /** Get the Ptolemy types for a key-value type string.
     *  @param the parameter containing the type string.
     *  @param typesStr the type string, e.g., "int string", "string long",
     *  where the first type is the key and the second type is the value. 
     *  @return an array of Type, where the first element is the key type,
     *  and the second element is the value type. 
     */
    public static Type[] getKeyValueTypes(Parameter parameter, String typesStr) throws IllegalActionException {
        
        final String typesCleanedStr = typesStr.replaceAll(",", "").trim();
        if(typesCleanedStr.isEmpty()) {
            throw new IllegalActionException(parameter, "Must provide non-empty value for types.");
        }
        
        final String[] typesArray = typesCleanedStr.split("\\s+");
        if(typesArray.length != 2) {
            throw new IllegalActionException(parameter, "Must provide type for both key and value.");
        }
        
        final Type keyType = getTypeFromString(typesArray[0]);
        if(keyType == null) {
            throw new IllegalActionException(parameter, "Unknown key type " + typesArray[0]);
        }

        final Type valueType = getTypeFromString(typesArray[1]);
        if(valueType == null) {
            throw new IllegalActionException(parameter, "Unknown value type " + typesArray[1]);
        }
        
        return new Type[] {keyType, valueType};
    }

    
    /** Get the Ptolemy type from a string. */
    public static Type getTypeFromString(String typeStr) {
        
        Type type;
        
        // FIXME this is fragile.
        final Matcher keyMatcher = ARRAY_TYPE_PATTERN.matcher(typeStr);
        final Matcher keyMatcher4Obj = OBJECT_TYPE_PATTERN.matcher(typeStr);
        if(keyMatcher.matches()) {
            Type elementType = BaseType.forName(keyMatcher.group(1));
            type = new ArrayType(elementType);
        } else if (keyMatcher4Obj.matches()) {
        	Type elementType;
        	if (keyMatcher4Obj.groupCount() > 1) {
        		elementType = BaseType.forName(keyMatcher4Obj.group(1));
        		type = new ObjectType(elementType.getTokenClass());
        	} else
        		type = new ObjectType(byte[].class);    	
        } else {
            type = BaseType.forName(typeStr);
        }

        return type;
    }


    
    /** The Ptolemy type for top-level ports of DDP pattern actors. */
    public final static Type keyValueArrayType = createKeyValueArrayType(BaseType.GENERAL, BaseType.GENERAL);
    
    /** Regular expression to match the array type and capture the
     *  element type.
     */
    private final static Pattern ARRAY_TYPE_PATTERN = Pattern.compile("arrayType\\((.*)\\)");
    
    /** Regular expression to match the object type and capture the
     *  element type.
     */   
    private final static Pattern OBJECT_TYPE_PATTERN = Pattern.compile("^object$|object\\((.*)\\)");

}
