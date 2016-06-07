/* Data output for DDP.
 * 
 * Copyright (c) 2011-2012 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2014-11-12 14:42:30 -0800 (Wed, 12 Nov 2014) $' 
 * '$Revision: 33062 $'
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.Path;
import org.kepler.configuration.ConfigurationProperty;

import ptolemy.actor.TypedIOPort;
import ptolemy.data.ArrayToken;
import ptolemy.data.BooleanToken;
import ptolemy.data.StringToken;
import ptolemy.data.Token;
import ptolemy.data.expr.Parameter;
import ptolemy.data.type.ArrayType;
import ptolemy.data.type.BaseType;
import ptolemy.data.type.Type;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;

/** This actor writes data to the storage system for a DDP workflow. 
 *  The actor reads a set of key-value pairs and combines them based
 *  on the data format specified in formatType.
 * 
 *  @author Daniel Crawl
 *  @version $Id: DDPDataSink.java 33062 2014-11-12 22:42:30Z crawl $
 */
public class DDPDataSink extends AtomicPathActor {

    /** Construct a new FileDataSink in a container with a given name. */
    public DDPDataSink(CompositeEntity container, String name)
            throws IllegalActionException, NameDuplicationException {
        super(container, name);

        in = new TypedIOPort(this, "in", true, false);
        
        // set the types for the in port
        _keyType = BaseType.GENERAL;
        _valueType = BaseType.GENERAL;

        out = new TypedIOPort(this, "out", false, true);
        out.setMultiport(true);
        
        // add the output formats in the config file as choices 
        _addFormats("Output");

        _FORMAT_TYPE_CATEGORY = "OutputFormats.Format";

        // set the default format
        _setFormat("LineOutputFormat");        
        
        mergeMultiPartOutputs = new Parameter(this, "mergeMultiPartOutputs");
        mergeMultiPartOutputs.setTypeEquals(BaseType.BOOLEAN);
        mergeMultiPartOutputs.setToken(BooleanToken.TRUE);
    }

    /** Set a list of tokens for a specific DDPDataSink actor. */
    public static void addTokens(String sinkActorName, List<Token> tokenList) throws IllegalActionException {
        List<Token> tokens = _tokenMap.get(sinkActorName);
        if(tokens == null) {
            tokens = new ArrayList<Token>();
            _tokenMap.put(sinkActorName, tokens);
        }
        synchronized(tokens) {
            tokens.addAll(tokenList);
        }
    }

    /** Update the path parameter if connected. */
    @Override
    public boolean prefire() throws IllegalActionException {
        
        boolean rc = super.prefire();
        
        // make sure it's not empty
        if(!_formatTypeStr.equals("TokenOutputFormat") &&
                !_formatTypeStr.equals("NullOutputFormat") &&
                ((StringToken)path.getToken()).stringValue().trim().isEmpty()) {
            throw new IllegalActionException(this, "Path must not be empty.");
        }
        
        return rc;
    }
    
    /** Write the token in the path parameter to the out port. */
    @Override
    public void fire() throws IllegalActionException {
                     
        if(_formatTypeStr.equals("TokenOutputFormat")) {
            // remove the tokens from the map so that these tokens
            // are not present in the next fire().
            List<Token> tokens = _tokenMap.remove(getFullName());
            if(tokens == null || tokens.isEmpty()) {
                throw new IllegalActionException(this, "No tokens were written.");
            }
            Token[] array = tokens.toArray(new Token[tokens.size()]);
            out.broadcast(new ArrayToken(array));
        } else if(_formatTypeStr.equals("NullOutputFormat")) {
            out.broadcast(new StringToken("done"));
        } else {
                        
            if(((BooleanToken)mergeMultiPartOutputs.getToken()).booleanValue()) {
                _mergeMultiPartOutputs(((StringToken)path.getToken()).stringValue());
            }
            
            out.broadcast(path.getToken());
        }
        
    }    
    
    /** Make sure output is either data or file, but not both. */
    @Override
    public void preinitialize() throws IllegalActionException {
        
        super.preinitialize();
        
        boolean pathIsConnected = (path.getPort().numberOfSources() > 0);
        
        Token pathToken = path.getToken();
        
        if((_formatTypeStr.equals("TokenOutputFormat") ||
                _formatTypeStr.equals("NullOutputFormat")) &&
                (pathIsConnected || 
                (pathToken != null && !((StringToken)pathToken).stringValue().isEmpty()))) {
            throw new IllegalActionException(this, 
                    "TokenOutputFormat or NullOutputFormat and the path port/parameter cannot be used at the same time.\n" +
                    "Either change the output format, or disconnect the path port and clear the\n" +
                    "path parameter.");
        }
        
        /*
        if(!pathIsConnected && 
                (pathToken == null || ((StringToken)pathToken).stringValue().isEmpty())) {
            formatType.setToken("TokenOutputFormat");
        }
        */
        
        
    }

    /** Remove any tokens stored for this actor. */
    @Override
    public void wrapup() throws IllegalActionException {
        
        super.wrapup();
        
        _tokenMap.remove(getFullName());
    }

    /** The data to be written. */
    public TypedIOPort in;

    /** After data has been written, this port outputs the path. */
    public TypedIOPort out;
    
    /** If true, merge multiple output files into a single file. */
    public Parameter mergeMultiPartOutputs;
    
    
    ///////////////////////////////////////////////////////////////////
    ////                         protected methods                 ////

    /** Update the key and value types. */
    @Override
    protected void _updateKeyValueTypes() {
        super._updateKeyValueTypes();
        Type type = Types.createKeyValueArrayType(_keyType, _valueType);
        in.typeConstraints().clear();
        in.setTypeAtMost(type);
        out.typeConstraints().clear();
        out.setTypeEquals(BaseType.STRING);
    }
    
    /** Set the key and value types from the types in the configuration property. */
    @Override
    protected void _setTypesFromConfiguration(ConfigurationProperty formatProperty)
            throws IllegalActionException {
    	
    	// there is no formatProperty found for the key/value type, try to use it directly
    	if (formatProperty == null) {
            String typesStr = keyValueTypes.stringValue();
            if(typesStr.isEmpty()) {
            	throw new IllegalActionException(this,
            	        "Parameter keyValueTypes has to be set if third party class\n" +
            	        "is set for parameter formatType.");
            } else {
            	in.typeConstraints().clear();
            	in.setTypeAtMost(Types.getKeyValueType(keyValueTypes, typesStr));
                out.typeConstraints().clear();
                out.setTypeEquals(BaseType.STRING);
            }
    	} else if(formatProperty.getProperty("Name").getValue().equals("TokenOutputFormat")) {
    	    in.setTypeEquals(BaseType.UNKNOWN);
            in.typeConstraints().clear();
            // FIXME want to set to at least unsized array of records with key and value
            in.setTypeAtLeast(ArrayType.ARRAY_UNSIZED_BOTTOM);
    	    out.setTypeEquals(BaseType.UNKNOWN);
            out.typeConstraints().clear();
    	    out.setTypeAtLeast(in);
    	} else if(formatProperty.getProperty("Name").getValue().equals("NullOutputFormat")) {
    	    in.setTypeEquals(BaseType.UNKNOWN);
            in.typeConstraints().clear();
            // FIXME want to set to at least unsized array of records with key and value
            in.setTypeAtLeast(ArrayType.ARRAY_UNSIZED_BOTTOM);
            out.typeConstraints().clear();
            out.setTypeEquals(BaseType.STRING);
    	} else {
    		super._setTypesFromConfiguration(formatProperty);
    	}
    }
    
    ///////////////////////////////////////////////////////////////////
    ////                         private methods                 ////
    
    /** Merge a directory containing multiple output files into a single file.
     *  This method deletes the directory when finished.
     * 
     * TODO move to parent class?
     */
    private void _mergeMultiPartOutputs(String pathStr) throws IllegalActionException {
        
        Configuration configuration = new Configuration();

        Path srcPath = new Path(pathStr);
        
        try {
            FileSystem srcPathFileSystem = srcPath.getFileSystem(configuration);
            // only merge if the output is a directory.
            if(srcPathFileSystem.isDirectory(srcPath)) {
            
                Path destPath = new Path(pathStr + "-TMP1234");        
                
                try {
                    // TODO if there is only one part-r-nnnnnn file, copyMerge() will still
                    // copy it instead of simply renaming it. 
                    if(!FileUtil.copyMerge(srcPath.getFileSystem(configuration), srcPath,
                            destPath.getFileSystem(configuration), destPath,
                            true, configuration, "")) {
                        throw new IllegalActionException(this, "Unable to merge output files in " + srcPath + "/.");
                    }
                } catch (IOException e) {
                    throw new IllegalActionException(this, e, "Error merging multi-part output files in " + srcPath + "/.");
                }
                
                try {
                    if(!destPath.getFileSystem(configuration).rename(destPath, srcPath)) {
                        throw new IllegalActionException(this, "Unable to rename " + destPath + " to " + srcPath);
                    }
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        } catch(IOException e) {
            throw new IllegalActionException(this, e, "Error accessing output file " + srcPath);
        }
    }

    /** A mapping of DDPDataSink actor name to tokens. */
    private static final java.util.Map<String,List<Token>> _tokenMap =
            Collections.synchronizedMap(new HashMap<String,List<Token>>());
            
}
