/* A base class for DDP pattern actor stubs that write data to the workflow.
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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.kepler.ddp.actor.pattern.Types;

import ptolemy.actor.TypedIOPort;
import ptolemy.data.Token;
import ptolemy.data.type.MonotonicFunction;
import ptolemy.graph.Inequality;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import ptolemy.kernel.util.Workspace;

/** A base class for DDP pattern actor stubs that write data to the workflow.
 * 
 *  @author Daniel Crawl
 *  @version $Id: StubSourceActor.java 32657 2014-04-12 00:09:39Z crawl $
 */
public class StubSourceActor extends StubBaseActor {

    /** Construct a new StubSourceActor in a container with a given name. */
    public StubSourceActor(CompositeEntity container, String name)
            throws IllegalActionException, NameDuplicationException {
        super(container, name);

        in = new TypedIOPort(this, "in", true, false);
        in.setTypeAtMost(Types.keyValueArrayType);
        
        key = new TypedIOPort(this, "key", false, true);
        key.setMultiport(true);
        
    }
    
    /** Clone the actor into the specified workspace. */
    @Override
    public Object clone(Workspace workspace) throws CloneNotSupportedException {
        StubSourceActor newObject = (StubSourceActor)super.clone(workspace);
        newObject._finish = new AtomicBoolean(false);
        newObject._keyList = new LinkedBlockingQueue<Token>();
        return newObject;
    }
    
    /** Stop execution of the workflow the next time postfire() is called. */
    public void finish() throws InterruptedException {
        _finish.set(true);
        // add a token to unblock the take() in fire()
        _keyList.put(Token.NIL);
    }
    
    /** Returns true if the actor should execute again. */
    @Override
    public boolean postfire() throws IllegalActionException {

        if(_runWorkflowLifecyclePerInput) {
            return false;
        } else if(_finish.get()) {
            getDirector().stop();
            return false;
        }
        return true;
    }
        
    /** Input port for key-value pairs. */
    public TypedIOPort in;
    
    /** Output port of key to write to workflow. */
    public TypedIOPort key;

    ///////////////////////////////////////////////////////////////////
    ////                         protected methods                 ////
    
    /** Create a function used for setting the type for an output port. In this class,
     *  returns null. */
    protected MonotonicFunction _createPortFunction(TypedIOPort outputPort) {
        return null;
    }

    /** Return the type constraints of this actor. The type constraint is
     *  that the type of the output ports is no less than the type of the
     *  fields of the input RecordToken.
     *  @return a list of Inequality.
     */
    @Override
    protected Set<Inequality> _customTypeConstraints() {

        // set the constraints between record fields and output ports
        Set<Inequality> constraints = new HashSet<Inequality>();

        // since the input port has a clone of the above RecordType, need to
        // get the type from the input port.
        //   RecordType inputTypes = (RecordType)input.getType();
        Iterator<?> outputPorts = outputPortList().iterator();

        while (outputPorts.hasNext()) {
            TypedIOPort outputPort = (TypedIOPort) outputPorts.next();
            Inequality inequality = new Inequality(_createPortFunction(outputPort),
                    outputPort.getTypeTerm());
            constraints.add(inequality);
        }

        return constraints;
    }

    /** Do not establish the usual default type constraints.
     *  @return null
     */
    @Override
    protected Set<Inequality> _defaultTypeConstraints() {
        return null;
    }
        
    /** If true, the actor will stop workflow execution. */
    protected AtomicBoolean _finish = new AtomicBoolean(false);
        
    /** A list containing tokens to be written by the key port. */
    protected LinkedBlockingQueue<Token> _keyList = new LinkedBlockingQueue<Token>();

}
