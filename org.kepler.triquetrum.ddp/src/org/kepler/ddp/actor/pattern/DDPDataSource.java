/* Data input for DDP.
 * 
 * Copyright (c) 2011-2012 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2015-12-03 10:04:07 -0800 (Thu, 03 Dec 2015) $' 
 * '$Revision: 34291 $'
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.kepler.configuration.ConfigurationProperty;

import ptolemy.actor.TypedIOPort;
import ptolemy.data.ArrayToken;
import ptolemy.data.IntToken;
import ptolemy.data.RecordToken;
import ptolemy.data.StringToken;
import ptolemy.data.Token;
import ptolemy.data.expr.Parameter;
import ptolemy.data.type.ArrayType;
import ptolemy.data.type.BaseType;
import ptolemy.data.type.MonotonicFunction;
import ptolemy.data.type.RecordType;
import ptolemy.data.type.Type;
import ptolemy.graph.Inequality;
import ptolemy.graph.InequalityTerm;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.Attribute;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import ptolemy.kernel.util.SingletonAttribute;

/** This actor reads data from the storage system into downstream DDP 
 *  pattern actors. Based on the data format in formatType, the data
 *  is partitioned or split and output as a set of key-value pairs.
 * 
 *  @author Daniel Crawl
 *  @version $Id: DDPDataSource.java 34291 2015-12-03 18:04:07Z crawl $
 */
public class DDPDataSource extends AtomicPathActor {

    /** Construct a new FileDataSource in a container with a given name. */
    public DDPDataSource(CompositeEntity container, String name)
            throws IllegalActionException, NameDuplicationException {
        super(container, name);
       
        out = new TypedIOPort(this, "out", false, true);
        
        // set the default types for the out port
        _keyType = BaseType.STRING;
        _valueType = BaseType.STRING;
                
        // add the input formats in the config file as choices 
        _addFormats("Input");

        _FORMAT_TYPE_CATEGORY = "InputFormats.Format";

        // set default format
        _setFormat("LineInputFormat");
        
        data = new TypedIOPort(this, "data");
        data.setInput(true);
        new SingletonAttribute(data, "_showName");
        
        chunkSize = new Parameter(this, "chunkSize");
        chunkSize.setTypeEquals(BaseType.INT);
        chunkSize.setToken(IntToken.ONE);
    }
    
    /** React to a parameter change. */
    @Override
    public void attributeChanged(Attribute attribute) throws IllegalActionException {
        
        if(attribute == chunkSize) {
            Token token = chunkSize.getToken();
            if(token != null) {
                int val = ((IntToken)token).intValue();
                if(val < 1) {
                    throw new IllegalActionException(this, "Chunk size must be at least 1.");
                }
                _chunkSize = val;
            }
        } else {
            super.attributeChanged(attribute);
        }
    }
    
    
    /** Get the token for a DDPDataSource actor.
     *  @param name the full name of the DDPDataSource actor.
     */
    public static ArrayToken getToken(String name) {
    	return _tokenMap.get(name);
    }
    
    /** Read the data input path and make sure it exists. */
    @Override
    public void fire() throws IllegalActionException {
        
        super.fire();
        
        if(data.numberOfSources() > 0) {
            Token token = data.get(0);
            ArrayToken arrayToken;
            if(token instanceof ArrayToken) {
                arrayToken = (ArrayToken) token;
            } else {
                arrayToken = new ArrayToken(new Token[] {token});
            }
            
            if(_chunkSize > 1) {
                
                List<ArrayToken> list = new ArrayList<ArrayToken>();
                List<Token> curChunk = new ArrayList<Token>();
                for(int i = 0 ; i < arrayToken.length(); i++) {
                    Token element = arrayToken.getElement(i);
                    curChunk.add(element);
                    if(curChunk.size() >= _chunkSize) {
                        ArrayToken curChunkArray = new ArrayToken(curChunk.toArray(new Token[curChunk.size()]));
                        list.add(curChunkArray);
                        curChunk = new ArrayList<Token>();
                    }
                }
                
                // add any remaining elements
                if(!curChunk.isEmpty()) {
                    ArrayToken curChunkArray = new ArrayToken(curChunk.toArray(new Token[curChunk.size()]));
                    list.add(curChunkArray);
                }
                
                RecordToken[] elements = new RecordToken[list.size()];
                for(int i = 0; i < list.size(); i++) {
                    elements[i] = new RecordToken(new String[] {"data", "id"},
                            new Token[] { list.get(i),
                            new IntToken(i) });
                }
                
                arrayToken = new ArrayToken(elements);
            }
            
            //System.out.println("array token = " + arrayToken);
            
            _tokenMap.put(getFullName(), arrayToken);
        }
        
        // make sure path exists
        
        // use asFile() so that relative paths are treated relative to
        // directory containing workflow file.      
//        final File file = path.asFile();
//        if(!file.exists()) {
//            throw new IllegalActionException(this, "Path does not exist: " + file);
//        }
        
    }
    
    /** Make sure input is either data or file, but not both. */
    @Override
    public void preinitialize() throws IllegalActionException {
    	
    	super.preinitialize();
    	
    	boolean dataIsConnected = (data.numberOfSources() > 0);
    	boolean pathIsConnected = (path.getPort().numberOfSources() > 0);
    	
    	Token pathToken = path.getToken();
    	
    	if(dataIsConnected && (pathIsConnected || 
    			(pathToken != null && !((StringToken)pathToken).stringValue().isEmpty()))) {
    		throw new IllegalActionException(this, 
    				"The data port and path port/parameter cannot be used at the same time.\n" +
    				"Either disconnect the data port, or disconnect the path port and clear the\n" +
    				"path parameter.");
    	}
    	
    	if(dataIsConnected) {
    		formatType.setToken("TokenInputFormat");
    	} else if(_formatTypeStr.equals("TokenInputFormat")) {
    		throw new IllegalActionException(this, "TokenInputFormat can only be used " +
    				"if the data port is connected.");
    	}

    	if(_chunkSize > 1 && pathIsConnected) {
    	    throw new IllegalActionException(this, "The chunk size must be 1 " +
    	            " when using the path port.");
    	}
    }
    
    /** Remove any token stored for this actor. */
    @Override
    public void wrapup() throws IllegalActionException {
    	
    	super.wrapup();
    	
    	_tokenMap.remove(getFullName());
    }
       
    ///////////////////////////////////////////////////////////////////
    ////                         public fields                     ////

    /** Data output. */
    public TypedIOPort out;
        
    /** An input token. */
    public TypedIOPort data;

    /** The chunk size for token data. If greater than 1, then the input
     *  data array is split into arrays equal to or smaller than this size.
     */
    public Parameter chunkSize;
    
    ///////////////////////////////////////////////////////////////////
    ////                         protected methods                 ////

    /** Update the key and value types. */
    @Override
    protected void _updateKeyValueTypes() {
        super._updateKeyValueTypes();
        Type type = Types.createKeyValueArrayType(_keyType, _valueType);
        out.setTypeEquals(type);
    }   
    
    /** Set the key and value types from the types in the configuration property. */
    @Override
    protected void _setTypesFromConfiguration(ConfigurationProperty formatProperty)
            throws IllegalActionException {
    	
    	//there is no formatProperty found for the key/value type, try to use it directly
    	if (formatProperty == null) {
            String typesStr = keyValueTypes.stringValue();
            if(typesStr.isEmpty()) {
            	throw new IllegalActionException(this, "Parameter keyValueTypes has to be set if third party class is set for parameter formatType.");
            } else {
                out.setTypeEquals(Types.getKeyValueType(keyValueTypes, typesStr));
            }
    	}
    	else if(formatProperty.getProperty("Name").getValue().equals("TokenInputFormat")) {
    	    _useDefaultTypeConstraints = false;
            out.setTypeEquals(BaseType.UNKNOWN);
    	} else {
    		super._setTypesFromConfiguration(formatProperty);
    	}
    }
    
    /** If using the default type constraints, return the custom
     *  type constraints from the parent class. Otherwise, return
     *  a constraint on the out port using the DataPortFunction class.
     */
    @Override
    protected Set<Inequality> _customTypeConstraints() {

        if(_useDefaultTypeConstraints) {
            return super._customTypeConstraints();
        }
        
        // set the constraints between record fields and output ports
        Set<Inequality> constraints = new HashSet<Inequality>();

        Inequality inequality = new Inequality(new DataPortFunction(),
                    out.getTypeTerm());
        constraints.add(inequality);

        return constraints;
    }

    /** If using the default type constraints, return the default type
     *  constraints from the parent class. Otherwise, return null.
     */
    @Override
    protected Set<Inequality> _defaultTypeConstraints() {
        if(_useDefaultTypeConstraints) {
            return super._defaultTypeConstraints();
        } 
        return null;
    }

    ///////////////////////////////////////////////////////////////////
    ////                         inner classes                     ////

    /** A MonotonicFunction for setting the output port types. */
    private class DataPortFunction extends MonotonicFunction {

        ///////////////////////////////////////////////////////////////
        ////                       public inner methods            ////

        /** Return the function result.
         *  @return A Type.
         */
        @Override
        public Object getValue() throws IllegalActionException {
            
            final Type dataPortType = data.getType();
            Type retval = null;
            
            if (dataPortType == BaseType.UNKNOWN) {
                retval = BaseType.UNKNOWN;
            } else {
                Type valueType;
                
                if (dataPortType instanceof ArrayType) {
                    valueType = ((ArrayType)dataPortType).getElementType();
                } else {
                    valueType = dataPortType;
                }
                
                // if the chunk size is greater than one, the construct
                // the record type containing the array of chunks and id.
                if(_chunkSize > 1) {
                    valueType = new RecordType(
                            new String[] {"data", "id"},
                            new Type[] {new ArrayType(valueType), BaseType.INT});
                }
                
                retval = Types.createKeyValueArrayType(BaseType.NIL, valueType);                
            }
            
            return retval;
        }

        /** Return an additional string describing the current value
         *  of this function.
         */
        @Override
        public String getVerboseString() {
            if (!(data.getType() instanceof ArrayType)) {
                return "Data is not an array";
            }
            return null;
        }

        /** Return the type variable in this inequality term. If the
         *  type of the input port is not declared, return an one
         *  element array containing the inequality term representing
         *  the type of the port; otherwise, return an empty array.
         *  @return An array of InequalityTerm.
         */
        @Override
        public InequalityTerm[] getVariables() {
            InequalityTerm portTerm = data.getTypeTerm();

            if (portTerm.isSettable()) {
                InequalityTerm[] variable = new InequalityTerm[1];
                variable[0] = portTerm;
                return variable;
            }

            return (new InequalityTerm[0]);
        }
    }
        
    ///////////////////////////////////////////////////////////////////
    ////                         private fields                    ////

    /** A mapping of DDPDataSource actor name to token. */
    private static java.util.Map<String,ArrayToken> _tokenMap = 
    		Collections.synchronizedMap(new HashMap<String,ArrayToken>());
    
    /** If true, use the default type constraints set in _updateKeyValueTypes().
     *  Otherwise, use custom type constraints defined in _customTypeConstraints().
     */
    private boolean _useDefaultTypeConstraints;

    /** The chunk size. */
    private int _chunkSize = 1;
}
