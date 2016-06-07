/* A DDP pattern composite actor with two inputs.
 *  
 * Copyright (c) 2011-2012 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2014-05-09 14:10:24 -0700 (Fri, 09 May 2014) $' 
 * '$Revision: 32714 $'
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

import ptolemy.actor.TypedIOPort;
import ptolemy.data.expr.StringParameter;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.Attribute;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import ptolemy.kernel.util.Workspace;

/** A DDP pattern composite actor with two inputs.
 * 
 *  @author Daniel Crawl
 *  @version $Id: DualInputPatternActor.java 32714 2014-05-09 21:10:24Z crawl $
 */
public class DualInputPatternActor extends SingleInputPatternActor {

    /** Construct a new DualInputPatternActor in a workspace. */
    public DualInputPatternActor(Workspace workspace) {
        super(workspace);
    }

    /** Construct a new DualInputPatternActor in a container with a given name. */
    public DualInputPatternActor(CompositeEntity container, String name)
            throws IllegalActionException, NameDuplicationException {
        super(container, name);

        in2 = new TypedIOPort(this, "in2", true, false);
        in2.setTypeAtMost(Types.keyValueArrayType);
        
        in2KeyValueTypes = new StringParameter(this, "in2KeyValueTypes");
        for(String type : _commonKeyValueTypes) {
            in2KeyValueTypes.addChoice(type);
        }

    }
    
    /** React to a change in an attribute. */
    @Override
    public void attributeChanged(Attribute attribute) throws IllegalActionException {
        
        if(attribute == in2KeyValueTypes) {
            in2.typeConstraints().clear();
            String typesStr = in2KeyValueTypes.stringValue();
            if(typesStr.isEmpty()) {
                in2.setTypeAtMost(Types.keyValueArrayType);
            } else {
                in2.setTypeAtMost(Types.getKeyValueType(in2KeyValueTypes, typesStr));
            }
        } else {
            super.attributeChanged(attribute);
        }
    }
    
    /** Override the parent class to check that parameter values are correctly set. */
    @Override
    public void preinitialize() throws IllegalActionException {
        
        super.preinitialize();
        
        // verify that execution class is set iff the key value types are set
        
        // NOTE: only perform these checks if we are not the top level model,
        // i.e., not the model running as a task by the DDP engine.
        if(toplevel() != this) {
            
            final boolean executionClassSet = !_executionClassName.isEmpty();
            final boolean executionCodeSet = (_executionCodeType != null && !_executionCodeType.isEmpty());

            String in2Str = in2KeyValueTypes.stringValue();
            boolean in2Set = false;
            if(in2Str != null && !in2Str.isEmpty()) {
                in2Set = true;
            }
                        
            if((executionClassSet || executionCodeSet) && !in2Set) {
                throw new IllegalActionException(this, "The execution class or code is set, " +
                        "but not in2KeyValueTypes. If you specify the execution class or " +
                        "code, set in2KeyValueTypes.");
            }
            
            if(!executionClassSet && !executionCodeSet && in2Set) {
                throw new IllegalActionException(this, "The execution class and code is not " +
                        "set, but in2KeyValueTypes is set. If you do not specify the " +
                        "execution class or code, clear in2KeyValueTypes.");
            }

        }
        
    }    
    
    /** Second data input. */
    public TypedIOPort in2;

    /** The input 2 key value types. This parameter should only be set
     *  when executionClass is specified.
     */
    public StringParameter in2KeyValueTypes;

}
