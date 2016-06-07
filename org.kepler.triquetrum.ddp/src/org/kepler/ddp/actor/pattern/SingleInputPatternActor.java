/* A DDP pattern composite actor with a single input.
 * 
 * Copyright (c) 2011-2012 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2015-09-08 16:47:26 -0700 (Tue, 08 Sep 2015) $' 
 * '$Revision: 33877 $'
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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.kepler.ddp.Utilities;
import org.kepler.ddp.director.DDPDirector;

import ptolemy.actor.Director;
import ptolemy.actor.Manager;
import ptolemy.actor.TypedCompositeActor;
import ptolemy.actor.TypedIOPort;
import ptolemy.actor.gui.style.TextStyle;
import ptolemy.data.BooleanToken;
import ptolemy.data.IntToken;
import ptolemy.data.expr.Parameter;
import ptolemy.data.expr.StringParameter;
import ptolemy.data.type.BaseType;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.Attribute;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.InternalErrorException;
import ptolemy.kernel.util.NameDuplicationException;
import ptolemy.kernel.util.StringAttribute;
import ptolemy.kernel.util.Workspace;

/** A DDP pattern composite actor with a single input.
 * 
 *  @author Daniel Crawl
 *  @version $Id: SingleInputPatternActor.java 33877 2015-09-08 23:47:26Z crawl $
 */
public class SingleInputPatternActor extends TypedCompositeActor implements DDPPatternActor {


    /** Construct a new SingleInputPatternActor for a workspace. */
    public SingleInputPatternActor(Workspace workspace) {
        super(workspace);
    }

    /** Construct a new SingleInputPatternActor in a container with a given name. */
    public SingleInputPatternActor(CompositeEntity container, String name)
            throws IllegalActionException, NameDuplicationException {
        super(container, name);
        
        in = new TypedIOPort(this, "in", true, false);
        in.setTypeAtMost(Types.keyValueArrayType);
        
        out = new TypedIOPort(this, "out", false, true);
        // NOTE: do not set this, since the value type may change
        // within the model (and possibly the key type)
        //out.setTypeAtLeast(in);
        
        degreeOfParallelism = new Parameter(this, "degreeOfParallelism");
        degreeOfParallelism.setExpression("0");
                
        executionClass = new StringParameter(this, "executionClass");
        
        executionCode = new StringParameter(this, "executionCode");
        executionCode.addChoice("");
        String[] languages = Utilities.getExecutionLanguageNames();
        for(String languageName : languages) {
            executionCode.addChoice(languageName);
        }
        executionCode.setExpression("");
        
        StringAttribute code = new StringAttribute(executionCode, "code");
        new TextStyle(code, "_style");

        inKeyValueTypes = new StringParameter(this, "inKeyValueTypes");
        for(String type : _commonKeyValueTypes) {
            inKeyValueTypes.addChoice(type);
        }
        
        outKeyValueTypes = new StringParameter(this, "outKeyValueTypes");
        for(String type : _commonKeyValueTypes) {
            outKeyValueTypes.addChoice(type);
        }
        
        jars = new StringParameter(this, "jars");
        
        printExeSummary = new Parameter(this,
                "printExeSummary", BooleanToken.FALSE);
        printExeSummary.setTypeEquals(BaseType.BOOLEAN);
        
		try {
			displayRedirectDir = new StringParameter(this, "displayRedirectDir");
		} catch (Throwable t) {
			throw new InternalErrorException(this, t,
					"Cannot create displayRedirectDir parameter.");
		}
		
		runWorkflowLifecyclePerInput = new Parameter(this, "runWorkflowLifecyclePerInput",
		        BooleanToken.FALSE);
		runWorkflowLifecyclePerInput.setTypeEquals(BaseType.BOOLEAN);		
    }

    /** React to a change in an attribute. */
    @Override
    public void attributeChanged(Attribute attribute) throws IllegalActionException {
        
        if(attribute == inKeyValueTypes) {
            in.typeConstraints().clear();
            String typesStr = inKeyValueTypes.stringValue();
            if(typesStr.isEmpty()) {
                in.setTypeAtMost(Types.keyValueArrayType);
            } else {
                in.setTypeAtMost(Types.getKeyValueType(inKeyValueTypes, typesStr));
            }
        } else if(attribute == outKeyValueTypes) {
            out.typeConstraints().clear();
            String typesStr = outKeyValueTypes.stringValue();
            if(!typesStr.isEmpty()) {
                out.setTypeEquals(Types.getKeyValueType(outKeyValueTypes, typesStr));
            }
        } else if(attribute == executionClass) {
            _executionClassName = executionClass.stringValue();
        } else if(attribute == executionCode) {
            _executionCodeType = executionCode.stringValue();
            if(!_executionCodeType.isEmpty()) {
                String[] names = Utilities.getExecutionLanguageNames();
                boolean found = false;
                for(String name : names) {
                    if(name.equals(_executionCodeType)) {
                        found = true;
                        break;
                    }
                }
                if(!found) {
                    throw new IllegalActionException(this,
                            "Invalid execution language: " + _executionCodeType);
                }
            }
        } else {
            super.attributeChanged(attribute);
        }
    }
    
    /** Clone the actor into the specified workspace. */
    /*
    @Override
    public Object clone(Workspace workspace) throws CloneNotSupportedException {
        SingleInputPatternActor newObject = (SingleInputPatternActor) super.clone(workspace);
        return newObject;
    }
    */
    
    /** List the opaque entities that are directly or indirectly contained by this entity. 
     *  During model preinitializing and resolving types, if using a class or code for
     *  execution, there will be no such entities.
     */
    @Override
    public List<?> deepEntityList() {
        
        Manager manager = getManager();
        if(manager != null && (!_executionClassName.isEmpty() || 
                (_executionCodeType != null && !_executionCodeType.isEmpty()))) {
            Manager.State state = manager.getState();
            //System.out.println("manager state = " + state);
            if(state == Manager.PREINITIALIZING ||
                    state == Manager.RESOLVING_TYPES) {
                return new LinkedList<Object>();
            }
        }
        
        return super.deepEntityList();
    }
   
    /** List the opaque entities that are directly or indirectly contained by this entity. 
     *  During model preinitializing and resolving types, if using a class or code for
     *  execution, there will be no such entities.
     */
    @Override
    public List<?> entityList() {

        Manager manager = getManager();
        if(manager != null && (!_executionClassName.isEmpty() || 
                (_executionCodeType != null && !_executionCodeType.isEmpty()))) {
            Manager.State state = manager.getState();
            //System.out.println("manager state = " + state);
            if(state == Manager.PREINITIALIZING ||
                    state == Manager.RESOLVING_TYPES) {
                return new LinkedList<Object>();
            }
        }

        return super.entityList();
    }

    /** Get the number of parallel instances to execute. */
    @Override
    public int getDegreeOfParallelism() throws IllegalActionException {
        return ((IntToken)degreeOfParallelism.getToken()).intValue();
    }

    /** Get the dir to redirect display related actors. */
	@Override
    public String getDisplayRedirectDir() throws IllegalActionException {
    	return displayRedirectDir.stringValue();
    }
    
    /** Get the name of the execution class. If no execution class is set,
     *  i.e., for this class the sub-workflow is to be executed, returns
     *  the empty string.
     */
    @Override
    public String getExecutionClassName() {
        return _executionClassName;
    }

    /** Get the execution code type. Returns null if not set. */
    @Override
    public String getExecutionCodeType() throws IllegalActionException {
        if(_executionCodeType == null || _executionCodeType.isEmpty()) {
            return null;
        }
        return _executionCodeType;
    }
    
    /** Get the execution code. Returns null if not set. */
    @Override
    public String getExecutionCode() throws IllegalActionException {
        return ((StringAttribute) executionCode.getAttribute("code")).getExpression();
    }

    /** Get a comma-separated list of jars to use with this actor. */
    @Override
    public String getJars() throws IllegalActionException {
        return jars.stringValue();
    }

    /** Get whether print execution summary when running workflow inside of Hadoop/Stratosphere job. */
    @Override
    public boolean getPrintExeInfo() throws IllegalActionException {
    	return ((BooleanToken) printExeSummary
                .getToken()).booleanValue();
    }
    
    /** Get a set of name-value pairs of input/output format parameters for the execution engine. */
    @Override
    public java.util.Map<String,String> getParameters() throws IllegalActionException {
        // return an empty map.
        return new HashMap<String,String>();
    }
    
    /** Get a set of (kepler name, implementation name) pairs of input/output format parameters for the execution engine. */
    @Override
    public java.util.Map<String, String> getParaImplNames(String engineName) throws IllegalActionException {
        // return an empty map.
        return new HashMap<String,String>();
    }
    
    /** Check if the full lifecycle of the sub-workflow should be executed for each input.*/
    public boolean getRunWorkflowLifecyclePerInput() throws IllegalActionException {
        return ((BooleanToken) runWorkflowLifecyclePerInput
                .getToken()).booleanValue();
    }
    
    /** Override the parent class to check that parameter values are correctly set. */
    @Override
    public void preinitialize() throws IllegalActionException {
        
        super.preinitialize();
        
        // verify that execution class is set iff the key value types are set
        
        // NOTE: only perform these checks if we are not the top level model,
        // i.e., not the model running as a task by the DDP engine.
        if(toplevel() != this) {
        
            Director director = getExecutiveDirector();
            if (!(director instanceof DDPDirector)) {
                throw new IllegalActionException(this,
                    "This actor only executes with the DDP director.");
            }

            final boolean executionClassSet = !_executionClassName.isEmpty();
            final boolean executionCodeSet = (_executionCodeType != null && !_executionCodeType.isEmpty());
            
            String inStr = inKeyValueTypes.stringValue();
            boolean inSet = false;
            if(inStr != null && !inStr.isEmpty()) {
                inSet = true;
            }
            
            String outStr = outKeyValueTypes.stringValue();
            boolean outSet = false;
            if(outStr != null && !outStr.isEmpty()) {
                outSet = true;
            }
            
            if((executionClassSet || executionCodeSet) && (!inSet || !outSet)) {
                throw new IllegalActionException(this, "The execution class or code is set, " +
                        "but not both the inKeyValueTypes and outKeyValueTypes. If you " +
                        "specify the execution class or code, set both inKeyValueTypes " +
                        "and outKeyValueTypes.");
            }
            
            if(!executionClassSet && !executionCodeSet && (inSet || outSet)) {
                throw new IllegalActionException(this, "The execution class and code is not set, " +
                        "but inKeyValueTypes or outKeyValueTypes are set. If you do not specify " +
                        "the execution class or code, clear both inKeyValueTypes, and outKeyValueTypes.");
            }

        }        
    }
        
    /** Data input. */
    public TypedIOPort in;
    
    /** Data output. */
    public TypedIOPort out;
    
    /** The number of parallel instances to execute. */
    public Parameter degreeOfParallelism;
    
    /** The name of the execution class. If not set, the sub-workflow is executed.
     *  To use a class not included with Kepler, add the class's jar to the jars
     *  parameter.
     */
    public StringParameter executionClass;
    
    /** The directory where the display related actors in DDP pattern sub-workflows will save their outputs.
     *  If it is empty, the display actors will be discarded before execution.
     *  More information about display redirect can be found at display-redirect module.
     */
    public StringParameter displayRedirectDir;
        
    /** The input key value types. This parameter should only be set
     *  when executionClass is specified.
     */
    public StringParameter inKeyValueTypes;

    /** The output key value types. This parameter should only be set
     *  when executionClass is specified.
     */
    public StringParameter outKeyValueTypes;
    
    /** A comma-separated list of jars to use with this actor. The 
     *  full path to each jar must be specified. 
     */
    public StringParameter jars;
    
    /** If true, print an execution summary to the log file each time the
     *  sub-workflow executes in the Hadoop or Stratosphere job. NOTE:
     *  this can severely impact performance and should only be used for
     *  debugging.
     */
    public Parameter printExeSummary;
        
    /** If true, the full lifecycle of the sub-workflow will be executed
     *  for each input. By setting this to true, multiple outputs can
     *  be collected for each input. If false, a single iteration of
     *  the workflow is performed for each input and only a single output
     *  may be generated. Performance is very slow if this is set to true.
     */
    public Parameter runWorkflowLifecyclePerInput;
    
    /** The type of the execution code. */
    public StringParameter executionCode;
    
    ///////////////////////////////////////////////////////////////////
    ////                         protected fields                  ////

    /** The execution class name. */
    protected String _executionClassName = "";
    
    /** The execution code type. */
    protected String _executionCodeType;
     
    ///////////////////////////////////////////////////////////////////
    ////                         private fields                    ////
    
}
