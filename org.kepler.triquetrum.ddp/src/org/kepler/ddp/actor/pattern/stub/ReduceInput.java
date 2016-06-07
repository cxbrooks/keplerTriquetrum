/* A stub actor that writes data into workflows for the Reduce pattern.
 * 
 * Copyright (c) 2011-2012 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2014-05-19 13:43:29 -0700 (Mon, 19 May 2014) $' 
 * '$Revision: 32724 $'
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

/** A stub actor that writes data into workflows for the Reduce pattern.
 * 
 *  @author Daniel Crawl
 *  @version $Id: ReduceInput.java 32724 2014-05-19 20:43:29Z crawl $
 */
public class ReduceInput extends StubSourceActor {

    /** Construct a new ReduceInput in a container with a given name. */
    public ReduceInput(CompositeEntity container, String name)
            throws IllegalActionException, NameDuplicationException {

        super(container, name);

        values = new TypedIOPort(this, "values", false, true);
        values.setMultiport(true);
    }
    
    /** Clone the actor into the specified workspace. */
    @Override
    public Object clone(Workspace workspace) throws CloneNotSupportedException {
        ReduceInput newObject = (ReduceInput)super.clone(workspace);
        newObject._valuesList = new LinkedBlockingQueue<Token>();
        return newObject;
    }

    /** Stop execution of the workflow the next time postfire() is called. */
    @Override
    public void finish() throws InterruptedException {
        super.finish();
        // add a token to unblock the take() in fire()
        _valuesList.put(Token.NIL);
    }

    /** Write the key and values from the input to the workflow. */
    @Override
    public void fire() throws IllegalActionException {

        Token keyToken = null;
        Token valuesToken = null;
        
        try {
            keyToken = _keyList.take();
            valuesToken = _valuesList.take();
        } catch(InterruptedException e) {
            throw new IllegalActionException(this, e, "Error waiting for token.");
        }

        if(!_finish.get()) {
            key.broadcast(keyToken);
            values.broadcast(valuesToken);
        }
        
    }
    
    /** Set the input and values. */
    public void setInput(Token keyToken, Token valuesToken) throws IllegalActionException {
        try {
            _keyList.put(keyToken);
            _valuesList.put(valuesToken);
        } catch(InterruptedException e) {
            // do not rethrow since this exception can occur when stopping a DDP
            // job. instead just print to stderr.
            //throw new IllegalActionException(this, e, "Error waiting for token lists.");
            System.err.println("Got InterruptedException.");
            return;
        } 
    }
            
    /** Port to write the values to the workflow. */
    public TypedIOPort values;

    
    ///////////////////////////////////////////////////////////////////
    ////                         protected methods                 ////
    
    /** Create a function used for setting the type for an output port. */
    @Override
    protected MonotonicFunction _createPortFunction(TypedIOPort outputPort) {
        return new ReduceInputPortFunction(outputPort);
    }

    ///////////////////////////////////////////////////////////////////
    ////                         inner classes                     ////
    
    /** A MonotonicFunction for setting the output port types. */
    private class ReduceInputPortFunction extends MonotonicFunction {
        private ReduceInputPortFunction(TypedIOPort port) {
            _port = port;
        }

        ///////////////////////////////////////////////////////////////
        ////                       public inner methods            ////

        /** Return the function result.
         *  @return A Type.
         */
        @Override
        public Object getValue() throws IllegalActionException {
            
            Type retval = null;
            final Type inPortType = in.getType();
            
            if (inPortType == BaseType.UNKNOWN || inPortType == BaseType.ARRAY_BOTTOM) {
                retval = BaseType.UNKNOWN;
            } else if(inPortType instanceof ArrayType) {
                
                final Type elementType = ((ArrayType)inPortType).getElementType();
                
                if (elementType == BaseType.UNKNOWN) {
                    retval = BaseType.UNKNOWN;
                } else if (elementType instanceof RecordType) {

                    Type fieldType;
                    if(_port == values) {
                        fieldType = ((RecordType)elementType).get("value");
                    } else {
                        fieldType = ((RecordType)elementType).get(_port.getName());
                    }
    
                    if (fieldType == null) {
                        retval = BaseType.UNKNOWN;
                    } else if (_port == values) {
                        retval = new ArrayType(fieldType);
                    } else {
                        retval = fieldType;
                    }
                }
            } 
            
            if(retval == null) {
                throw new IllegalActionException(ReduceInput.this,
                        "Invalid type for input port: " + inPortType);
            }
            
            return retval;
            
        }

        /** Return an additional string describing the current value
         *  of this function.
         */
        @Override
        public String getVerboseString() {
            if (in.getType() instanceof ArrayType) {
                if(((ArrayType)in.getType()).getElementType() instanceof RecordType) {
                    RecordType type = (RecordType) ((ArrayType)in.getType()).getElementType();
                    if(_port == values) {
                        if (type.get("value") == null) {
                            return "Input Record doesn't have field named value";
                        }
                    } else if (type.get(_port.getName()) == null) {
                            return "Input Record doesn't have field named value";
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
            InequalityTerm portTerm = in.getTypeTerm();

            if (portTerm.isSettable()) {
                InequalityTerm[] variable = new InequalityTerm[1];
                variable[0] = portTerm;
                return variable;
            }

            return (new InequalityTerm[0]);
        }

        ///////////////////////////////////////////////////////////////
        ////                       private inner variable          ////
        private TypedIOPort _port;
    }
    
    /** A list containing tokens to be written by the values port. */
    private LinkedBlockingQueue<Token> _valuesList = new LinkedBlockingQueue<Token>();

}
