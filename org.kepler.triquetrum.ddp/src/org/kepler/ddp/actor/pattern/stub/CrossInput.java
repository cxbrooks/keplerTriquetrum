/* A stub actor that writes data into workflows for the Cross pattern.
 * 
 * Copyright (c) 2011-2012 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2014-04-13 14:25:55 -0700 (Sun, 13 Apr 2014) $' 
 * '$Revision: 32663 $'
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

/** A stub actor that writes data into workflows for the Cross pattern.
 * 
 *  @author Daniel Crawl
 *  @version $Id: CrossInput.java 32663 2014-04-13 21:25:55Z crawl $
 */
public class CrossInput extends MapInput {

    /** Construct a new CrossInput in a container with a given name. */
    public CrossInput(CompositeEntity container, String name)
            throws IllegalActionException, NameDuplicationException {
        
        super(container, name);
        
        in2 = new TypedIOPort(this, "in2", true, false);
        in2.setTypeAtMost(Types.keyValueArrayType);        
            
        key2 = new TypedIOPort(this, "key2", false, true);
        key2.setMultiport(true);

        value2 = new TypedIOPort(this, "value2", false, true);
        value2.setMultiport(true);
    }

    /** Clone the actor into the specified workspace. */
    @Override
    public Object clone(Workspace workspace) throws CloneNotSupportedException {
        CrossInput newObject = (CrossInput) super.clone(workspace);
        newObject._key2List = new LinkedBlockingQueue<Token>();
        newObject._value2List = new LinkedBlockingQueue<Token>();
        return newObject;
    }
    
    /** Write the key and value from both inputs to the workflow. */
    @Override
    public void fire() throws IllegalActionException {

        super.fire();
        
        Token key2Token;
        Token value2Token;
            
        try {
            key2Token = _key2List.take();
            value2Token = _value2List.take();
        } catch(InterruptedException e) {
            throw new IllegalActionException(this, e, "Error waiting for token.");
        }
            
        if(!_finish.get()) {
            key2.broadcast(key2Token);
            value2.broadcast(value2Token);
        }
    }
    
    /** Stop execution of the workflow the next time postfire() is called. */
    @Override
    public void finish() throws InterruptedException {
        super.finish();
        // add a token to unblock the take() in fire()
        _key2List.put(Token.NIL);
        _value2List.put(Token.NIL);
    }

    /** Set the key and value from both inputs. */
    public void setInput(Token key1Token, Token value1Token, Token key2Token, Token value2Token) throws IllegalActionException {

        super.setInput(key1Token, value1Token);
        try {
            
            if(key2Token == null) {
                key2Token = Token.NIL;
            }
            _key2List.put(key2Token);
            
            if(value2Token == null) {
                value2Token = Token.NIL;
            }
            _value2List.put(value2Token);
        } catch(InterruptedException e) {
            throw new IllegalActionException(this, e, "Error waiting for token lists.");
        }
    }
    
    /** Second input port for key-value pairs. */
    public TypedIOPort in2;
    
    /** Port to write the key from the second input. */
    public TypedIOPort key2;
    
    /** Port to write the value from the second input. */
    public TypedIOPort value2;

    ///////////////////////////////////////////////////////////////////
    ////                         protected methods                 ////
    
    /** Create a function used for setting the type for an output port. */
    @Override
    protected MonotonicFunction _createPortFunction(TypedIOPort outputPort) {
        return new CrossInputPortFunction(outputPort);
    }

    ///////////////////////////////////////////////////////////////////
    ////                         inner classes                     ////

    /** A MonotonicFunction for setting the output port types. */
    private class CrossInputPortFunction extends MonotonicFunction {
        private CrossInputPortFunction(TypedIOPort outputPort) {

            // if output port is key2 or value2, set the name
            // to be "key" or "value2", respectively, since "key2"
            // and "value2" are not fields in the key-values pair
            // record type.
            if(outputPort == key2) {
                _outputPortName = "key";
            } else if(outputPort == value2) {
                _outputPortName = "value";
            } else {
                _outputPortName = outputPort.getName();
            }
            
            if(outputPort == key || outputPort == value) {
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
            } else if(inPortType instanceof ArrayType) {
                
                final Type elementType = ((ArrayType)inPortType).getElementType();
                if (elementType instanceof RecordType) {
                    final Type fieldType = ((RecordType)elementType).get(_outputPortName);

                    if (fieldType == null) {
                        retval = BaseType.UNKNOWN;
                    } else {
                        retval = fieldType;
                    }
                }
            } 

            if(retval == null) {
                throw new IllegalActionException(CrossInput.this,
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
                    Type fieldType = type.get(_outputPortName);

                    if (fieldType == null) {
                        return "Input Record in port " + _inputPort.getName() +
                            " doesn't have field named " + _outputPortName;
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
        private String _outputPortName;
        
    }
        
    /** A list containing tokens to be written by the key2 port. */
    private LinkedBlockingQueue<Token> _key2List = new LinkedBlockingQueue<Token>();

    /** A list containing tokens to be written by the value2 port. */
    private LinkedBlockingQueue<Token> _value2List = new LinkedBlockingQueue<Token>();

}
