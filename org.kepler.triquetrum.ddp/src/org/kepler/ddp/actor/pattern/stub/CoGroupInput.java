/* A stub actor that writes data into workflows for the CoGroup pattern.
 * 
 * Copyright (c) 2011-2012 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2014-04-11 17:09:39 -0700 (Fri, 11 Apr 2014) $' 
 * '$Revision: 32657 $'
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

import java.util.concurrent.LinkedBlockingQueue;

import org.kepler.ddp.actor.pattern.Types;

import ptolemy.actor.TypedIOPort;
import ptolemy.data.Token;
import ptolemy.data.type.ArrayType;
import ptolemy.data.type.BaseType;
import ptolemy.data.type.MonotonicFunction;
import ptolemy.data.type.RecordType;
import ptolemy.data.type.Type;
import ptolemy.graph.InequalityTerm;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import ptolemy.kernel.util.Workspace;

/** A stub actor that writes data into workflows for the CoGroup pattern.
 * 
 *  @author Daniel Crawl
 *  @version $Id: CoGroupInput.java 32657 2014-04-12 00:09:39Z crawl $
 */
public class CoGroupInput extends StubSourceActor {

    /** Construct a new CoGroupInput in a container with a given name. */
    public CoGroupInput(CompositeEntity container, String name)
            throws IllegalActionException, NameDuplicationException {
        
        super(container, name);

        in2 = new TypedIOPort(this, "in2", true, false);
        in2.setTypeAtMost(Types.keyValueArrayType);        

        values1 = new TypedIOPort(this, "values1", false, true);
        values1.setMultiport(true);

        values2 = new TypedIOPort(this, "values2", false, true);
        values2.setMultiport(true);

    }
    
    /** Clone the actor into the specified workspace. */
    @Override
    public Object clone(Workspace workspace) throws CloneNotSupportedException {
        CoGroupInput newObject = (CoGroupInput)super.clone(workspace);
        newObject._values1List = new LinkedBlockingQueue<Token>();
        newObject._values2List = new LinkedBlockingQueue<Token>();
        return newObject;
    }
    
    /** Write the key and values from both inputs to the workflow. */
    @Override
    public void fire() throws IllegalActionException {
        
        Token keyToken;
        Token values1Token;
        Token values2Token;
        
        try {
            keyToken = _keyList.take();          
            values1Token = _values1List.take();                
            values2Token = _values2List.take();
        } catch(InterruptedException e) {
            throw new IllegalActionException(this, e, "Error waiting for token lists.");
        }
        
        if(!_finish.get()) {
            key.broadcast(keyToken);
            values1.broadcast(values1Token);
            values2.broadcast(values2Token);
        }

    }
    
    /** Stop execution of the workflow the next time postfire() is called. */
    @Override
    public void finish() throws InterruptedException {
        super.finish();
        // add a token to unblock the take() in fire()
        _values1List.put(Token.NIL);
        _values2List.put(Token.NIL);
    }

    /** Set the key and values from both inputs. */
    public void setInput(Token keyToken, Token values1Token, Token values2Token) throws IllegalActionException {
        
        try {
            _keyList.put(keyToken);
            _values1List.put(values1Token);
            _values2List.put(values2Token);
        } catch(InterruptedException e) {
            throw new IllegalActionException(this, e, "Error waiting for token lists.");
        }
    }
    
    /** The input for key-value pairs. */
    public TypedIOPort in2;
    
    /** Port to write the values from the first input. */
    public TypedIOPort values1;
    
    /** Port to write the values from the second input. */
    public TypedIOPort values2;

    ///////////////////////////////////////////////////////////////////
    ////                         protected methods                 ////

    /** Create a function used for setting the type for an output port. */
    @Override
    protected MonotonicFunction _createPortFunction(TypedIOPort outputPort) {
        return new CoGroupInputPortFunction(outputPort);
    }

    ///////////////////////////////////////////////////////////////////
    ////                         inner classes                     ////

    /** A MonotonicFunction for setting the output port types. */
    private class CoGroupInputPortFunction extends MonotonicFunction {
        private CoGroupInputPortFunction(TypedIOPort outputPort) {
            
            _outputPort = outputPort;
            
            if(outputPort == key || outputPort == values1) {
                _inputPort = in;
            } else {
                _inputPort = in2;
            }
        }

        ///////////////////////////////////////////////////////////////
        ////                       public inner methods            ////

        /** Return the function result.
         *  @return A Type.
         */
        @Override
        public Object getValue() throws IllegalActionException {
            
            Type retval = null;
            final Type inPortType = _inputPort.getType();
            
            if (inPortType == BaseType.UNKNOWN) {
                retval = BaseType.UNKNOWN;
            } else if (inPortType instanceof ArrayType) {
                
                final Type elementType = ((ArrayType)inPortType).getElementType();
                
                if(elementType instanceof RecordType) {
                    Type fieldType;
                    if(_outputPort == key) {
                        fieldType = ((RecordType)elementType).get("key");
                    } else {
                        fieldType = ((RecordType)elementType).get("value");
                    }
    
                    if (fieldType == null) {
                        retval = BaseType.UNKNOWN;
                    } else if(_outputPort == key) {
                        retval = fieldType;
                    } else {
                        retval = new ArrayType(fieldType);
                    }
                }
            } 
            
            if(retval == null) {
                throw new IllegalActionException(CoGroupInput.this,
                        "Invalid type for input port " + _inputPort.getName() + " : " + inPortType);
            }
            
            return retval;
        }

        /** Return an additional string describing the current value
         *  of this function.
         */
        @Override
        public String getVerboseString() {
            if (_inputPort.getType() instanceof ArrayType) {
                if(((ArrayType)_inputPort.getType()).getElementType() instanceof RecordType) {
                    RecordType type = (RecordType) ((ArrayType)_inputPort.getType()).getElementType();
                    Type fieldType = type.get("value");

                    if (fieldType == null) {
                        return "Input Record in port " + _inputPort.getName() +
                            " doesn't have field named \"value\".";
                    }
                }
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
            InequalityTerm portTerm = _inputPort.getTypeTerm();

            if (portTerm.isSettable()) {
                InequalityTerm[] variable = new InequalityTerm[1];
                variable[0] = portTerm;
                return variable;
            }

            return (new InequalityTerm[0]);
        }

        ///////////////////////////////////////////////////////////////
        ////                       private inner variable          ////
        
        private TypedIOPort _inputPort;
        private TypedIOPort _outputPort;
        
    }

    /** Values from the first input.*/
    private LinkedBlockingQueue<Token> _values1List = new LinkedBlockingQueue<Token>();
    
    /** Values from the second input. */
    private LinkedBlockingQueue<Token> _values2List = new LinkedBlockingQueue<Token>();
    
}
