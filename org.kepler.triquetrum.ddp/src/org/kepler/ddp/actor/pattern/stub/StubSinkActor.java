/* A base class for DDP pattern actor stubs that read data from the workflow.
 * 
 * Copyright (c) 2011-2012 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2015-07-14 17:24:21 -0700 (Tue, 14 Jul 2015) $' 
 * '$Revision: 33544 $'
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
package org.kepler.ddp.actor.pattern.stub;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import org.kepler.ddp.actor.pattern.Types;

import ptolemy.actor.TypedIOPort;
import ptolemy.data.ArrayToken;
import ptolemy.data.RecordToken;
import ptolemy.data.StringToken;
import ptolemy.data.Token;
import ptolemy.data.expr.Parameter;
import ptolemy.data.expr.SingletonParameter;
import ptolemy.data.type.BaseType;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import ptolemy.kernel.util.Settable;
import ptolemy.kernel.util.Workspace;

/** A base class for DDP pattern actor stubs that read data from the workflow.
 * 
 *  @author Daniel Crawl
 *  @version $Id: StubSinkActor.java 33544 2015-07-15 00:24:21Z crawl $
 */
public class StubSinkActor extends StubBaseActor {

    /** Construct a new StubSinkActor in a container with a given name. */
    public StubSinkActor(CompositeEntity container, String name)
            throws IllegalActionException, NameDuplicationException {
        
        super(container, name);

        keysvalues = new TypedIOPort(this, "keysvalues", true, false);
        keysvalues.setTypeAtMost(Types.keyValueArrayType);
        new SingletonParameter(keysvalues, "_showName");
        
        lines = new TypedIOPort(this, "lines", true, false);
        lines.setTypeEquals(BaseType.STRING);
        new SingletonParameter(lines, "_showName");
        
        out = new TypedIOPort(this, "out", false, true);
        
        // set the production rate to 0 since no tokens are written.
        // the out port is connected to the out port of the pattern
        // actor, which does not transfer tokens.
        // NOTE: if the production rate is not 0, SDF sometimes throws
        // an exception since it expects a token to be written when
        // this actor is fired.
        Parameter tokenProductionRate = new Parameter(out, "tokenProductionRate");
        tokenProductionRate.setVisibility(Settable.NOT_EDITABLE);
        tokenProductionRate.setTypeEquals(BaseType.INT);
        tokenProductionRate.setToken("0");
        
        _emptyKeyValue = new ArrayToken(new Token[] { new StringToken(), new StringToken() });

    }

    /** Clone the actor into the specified workspace. */
    @Override
    public Object clone(Workspace workspace) throws CloneNotSupportedException {
        StubSinkActor newObject = (StubSinkActor)super.clone(workspace);
        newObject._blockingList = new LinkedBlockingQueue<List<Token>>();
        try {
            newObject._emptyKeyValue = new ArrayToken(new Token[] { new StringToken(), new StringToken() });
        } catch (IllegalActionException e) {
            throw new CloneNotSupportedException("Error creating _emptyKeyValue for clone: " + e.getMessage());
        }
        newObject._nonBlockingList = new LinkedList<Token>();
        return newObject;
    }
    
    /** Read the data the input port. */
    @Override
    public void fire() throws IllegalActionException {
        
        
        Token keysValuesToken = null;
        
        if(_keysvaluesConnected) {
            keysValuesToken = keysvalues.get(0);                    
        }
        
        if(_linesConnected) {
            final String linesStr = ((StringToken)lines.get(0)).stringValue();
            if(linesStr.trim().isEmpty()) {
                keysValuesToken = _emptyKeyValue;                
            } else {
                final String[] lineArray = linesStr.split("\n");
                final Token[] tokenArray = new Token[lineArray.length];
                for(int i = 0; i < lineArray.length; i++) {
                    final String line = lineArray[i];
                    tokenArray[i] = new RecordToken(_recordLabels,
                            new Token[] {new StringToken(line.substring(0, line.indexOf("\t"))),
                            new StringToken(line)});
                }
                keysValuesToken = new ArrayToken(tokenArray);
            }   
            
        }
        
        if(_runWorkflowLifecyclePerInput) {
            _nonBlockingList.add(keysValuesToken);
        } else {
            List<Token> list = new LinkedList<Token>();
            list.add(keysValuesToken);
            try {
                _blockingList.put(list);
            } catch(InterruptedException e) {
                throw new IllegalActionException(this, e, "Error waiting for token.");
            }
        }
        
    }
    
    /** Get the data read from the workflow by this actor. */
    public List<Token> getOutput() throws IllegalActionException {
                
        List<Token> retval;
        
        if(_runWorkflowLifecyclePerInput) {
            retval = _nonBlockingList;
            _nonBlockingList = new LinkedList<Token>();
        } else {
            try {
                retval = _blockingList.take();
            } catch(InterruptedException e) {
                // do not rethrow since this exception can occur when stopping a DDP
                // job. instead just print to stderr.
                //throw new IllegalActionException(this, e, "Error waiting for token lists.");
                System.err.println("Got InterruptedException.");
                return null;
            }
        }
        
        return retval;
    }
    
    @Override
    public void preinitialize() throws IllegalActionException {
        
        super.preinitialize();
     
        // see if keysvalues port is connected
        if(keysvalues.numberOfSources() > 0) {
            _keysvaluesConnected = true;
            
            // set the out port type to be based on keysvalues
            out.setTypeAtLeast(keysvalues);
            
        } else {
            _keysvaluesConnected = false;
        }
        
        // see if lines port is connected
        if(lines.numberOfSources() > 0) {
            _linesConnected = true;
            
            // set the out port type to be strings
            out.setTypeEquals(Types.createKeyValueArrayType(BaseType.STRING, BaseType.STRING));
            
            // FIXME fix for problem in hadoop
            if(!_keysvaluesConnected) {
                keysvalues.setTypeEquals(Types.createKeyValueArrayType(BaseType.STRING, BaseType.STRING));
            }
            
        } else {
            _linesConnected = false;
        }
        
        if(!_keysvaluesConnected && !_linesConnected) {
            throw new IllegalActionException(this, "No input connected.");
        } 
        
    }
            
    /** An array of records with key-values. */
    public TypedIOPort keysvalues;
    
    /** Line(s) of text that will be converted into key-value pairs.
     *  For each line, the key is the part before the first tab,
     *  and the value is the entire line.
     */
    public TypedIOPort lines;
    
    /** Output port of key-value pairs. */
    public TypedIOPort out;
    
    /** A blocking list of tokens used when executing one iteration of the workflow per input. */
    private LinkedBlockingQueue<List<Token>> _blockingList = new LinkedBlockingQueue<List<Token>>();
    
    /** A non-blocking list of tokens used when execution the full lifecycle
     *  of the workflow per input.
     */
    private List<Token> _nonBlockingList = new LinkedList<Token>();
    
    /** True if the keysvalues port is connected. */
    private boolean _keysvaluesConnected;
    
    /** True if the lines port is connected. */
    private boolean _linesConnected;
    
    // TODO move to Types, find other instances
    /** Labels for key-value record token. */
    private static final String[] _recordLabels = new String[] {"key", "value"};
    
    /** Array of one record token with an empty key value. Cannot make static final
     *  since ArrayToken constructor throws exception.
     */
    private ArrayToken _emptyKeyValue;
    
}
