/* A director to run DDP models.
 * 
 * Copyright (c) 2013 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2015-11-04 16:26:32 -0800 (Wed, 04 Nov 2015) $' 
 * '$Revision: 34215 $'
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
package org.kepler.ddp.director;

import java.lang.reflect.Constructor;
import java.util.List;

import org.kepler.configuration.ConfigurationManager;
import org.kepler.configuration.ConfigurationProperty;

import ptolemy.actor.Director;
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
import ptolemy.kernel.util.Settable;
import ptolemy.kernel.util.Workspace;
import ptolemy.util.MessageHandler;

/** A director that converts DDP pattern actors (Map, Reduce, Cross, CoGroup,
 *  and Match) and I/O actors (DDPDataSink and DDPDataSource) into a job that
 *  is executed on a DDP engine such as Hadoop or Stratosphere.
 *  <p>
 *  <b>NOTE:</b> Only DDP pattern and I/O actors may be present in the
 *  workflow. Other actors must placed inside the composite pattern actors
 *  or in a different sub-workflow.
 *  </p>
 * 
 * 
 * @author Daniel Crawl
 * @version $Id: DDPDirector.java 34215 2015-11-05 00:26:32Z crawl $
 */
public class DDPDirector extends Director {
	
    /** Construct a new DDPBaseDirector in a container with a given name. */
	public DDPDirector(CompositeEntity container, String name)
			throws IllegalActionException, NameDuplicationException {
		super(container, name);
		_initializeParameters();
	}

    /** Construct a new DDPBaseDirector for a workspace. */
	public DDPDirector(Workspace workspace) throws IllegalActionException,
			NameDuplicationException {
		super(workspace);
		_initializeParameters();
	}
	
	/** React to a change in an attribute. This overrides the base class to
	 *  handles changes to local parameters.
	 */
	@Override
	public void attributeChanged(Attribute attribute) throws IllegalActionException {

	    if(attribute == engine) {
	        // see if engine actually changed
	        String newEngineStr = engine.stringValue();

            if(newEngineStr.equals("default")) {
                newEngineStr = _defaultEngine;
            }

	        if(_engine == null || !_engine.getName().equals(newEngineStr)) {
    	        DDPEngine newEngine = _getEngine(newEngineStr);
    	        if(newEngine == null) {
    	            throw new IllegalActionException(this,
    	                    "Unable to load DDP engine " + newEngineStr + ".");
    	        } else {
    	            
    	            // remove engine-specific parameters from the director if
    	            // there was a previous engine.
    	            if(_engine != null) {
        	            try {
                            _engine._removeParameters();
                        } catch (NameDuplicationException e) {
                            throw new IllegalActionException(this, e,
                                    "Error removing parameters for " + _engine.getName());
                        }
    	            }
    	            _engine = newEngine;
    	           
    	            // update the values in the startServerType parameter
    	            
    	            boolean found = false;
    	            final String oldServerType = startServerType.stringValue();
    	            
    	            startServerType.removeAllChoices();
    	            
    	            String[] serverTypes = _engine.getServerTypes();
                    for(String typeStr : serverTypes) {
                        startServerType.addChoice(typeStr);
                        if(oldServerType.equals(typeStr)) {
                            found = true;
                        }
                    }
                    
                    if(found) {
                        startServerType.setToken(oldServerType);
                    } else {
                        startServerType.setToken(serverTypes[0]);
                    }

    	        }
	        }
	    } else if(attribute == startServerType) {
            String newTypeStr = startServerType.stringValue();
            if(!newTypeStr.isEmpty() && !newTypeStr.equals("default")) {
                boolean found = false;
                boolean haveDistributed = false;
                // see if the start type is found
                for(String typeStr : startServerType.getChoices()) {
                    if(typeStr.equals(newTypeStr)) {
                        found = true;
                        break;
                    }
                    // see if it supports distributed
                    if(typeStr.equals(DISTRIBUTED_STRING)) {
                        haveDistributed = true;
                    }
                }
                // backwards compatibility: if the type was set to local or cluster,
                // change to distributed
                if(haveDistributed && 
                        (newTypeStr.equals("local") || newTypeStr.equals("cluster"))) {
                    startServerType.setExpression(DISTRIBUTED_STRING);
                    found = true;
                }
                
                if(!found) {
                    throw new IllegalActionException(this, "Invalid type: " + newTypeStr);
                }
            }
	    } else {	
	    	if(_engine != null) {
	    		_engine.attributeChanged(attribute);
	    	}
            super.attributeChanged(attribute);
	    }
	}
	
    /** Clone the object into the specified workspace.
     *  @param workspace The workspace for the cloned object.
     *  @exception CloneNotSupportedException Not thrown in this base class
     *  @return The new Attribute.
     */
    @Override
    public Object clone(Workspace workspace) throws CloneNotSupportedException {
        DDPDirector newObject = (DDPDirector) super.clone(workspace);
        if(_engine != null) {
            newObject._engine = (DDPEngine)_engine.clone(workspace);
            // set the director of the cloned engine to be the cloned director.
            newObject._engine._director = newObject;
        }
        return newObject;
    }

    /** Run the engine. */
    @Override
    public void fire() throws IllegalActionException {
    	_engine.fire();
    }

    /** Get the dir to redirect display related actors. */
    public String getDisplayRedirectDir() throws IllegalActionException {
    	return _engine.getDisplayRedirectDir();
    }
    
    /** Get the name of the DDP engine. */
    public String getEngineName() throws IllegalActionException {
        if(_engine == null) {
        	return null;
        } else {
        	return _engine.getName();
        }
    }
    
    /** Postfire the engine.
     *  @return If true, engine can be fired again. If false, do not run again.
     */
    @Override
	public boolean postfire() throws IllegalActionException {
		return _engine.postfire();
	}

    /** Preinitialize the engine. */
    @Override
    public void preinitialize() throws IllegalActionException {
    	super.preinitialize();
    	_engine.preinitialize();
    }

    /** Stop any running DDP jobs. */
    @Override
    public void stop() {
        super.stop();
        try {
            _engine.stop();
        } catch (IllegalActionException e) {
            MessageHandler.error("Error stopping DDP job.", e);
        }
    }
    
    /** Perform any cleanup in the engine. */
    @Override
    public void wrapup() throws IllegalActionException {
        super.wrapup();
    	_engine.wrapup();
    }

    ///////////////////////////////////////////////////////////////////
    ////                         parameters                        ////

    /** DDP engine configuration directory. */
    public StringParameter configDir;
    
    /** The default degree of parallelism for ddp pattern actors. 
     *  This value is used if the actor's degreeOfParallelism <= 0.
     */
    public Parameter degreeOfParallelism;

    /** A comma-separated list of jar files to include. If the absolute
     *  path to the jar is not specified, then the jar should be
     *  located in a Kepler module. 
     */
    public StringParameter includeJars;
    
    /** A comma-separated list of arguments for the job. It is only useful
     *  when DDP actors' logics are described by java classes, not
     *  sub-workflows.
     */
    public StringParameter jobArguments;
    
    /** The directory where the display related actors in DDP pattern sub-workflows will save their outputs.
     *  If it is empty, the display actors will be discarded before execution.
     *  More information about display redirect can be found at display-redirect module.
     */
    public StringParameter displayRedirectDir;

    /** If true, sub-workflows are written to files in a directory. 
     *  The directory path is printed to stdout when the workflow
     *  executes.
     */
    public Parameter writeSubWorkflowsToFiles;

    /** The execution engine. */
    public StringParameter engine;
    
    /** The type of DDP server to start (if one is not running). */
    public StringParameter startServerType;

    /** String for serverType parameter for running DDP Engine in the same JVM. */
    public final static String SAME_JVM_STRING = "sameJVM";

    /** String for serverType parameter for running DDP Engine in a distributed environment. */
    public final static String DISTRIBUTED_STRING = "distributed";

    /** The value used to signify the default degree of parallelism for
     *  the degreeOfParallelism parameter.
     */
    public static final IntToken DEFAULT_INTTOKEN = new IntToken(-1);

    /** The name of the DEFAULT degree of parallelism parameter. */
    public static final String DEFAULT_NAME = "DEFAULT";
    
    ///////////////////////////////////////////////////////////////////
    ////                      private methods                    //////
	
    /** Get a DDP engine.
     *  @param name The DDP engine name.
     *  @return The DDP engine or null if the engine name could not be
     *  found in the configuration file. 
     */
    private DDPEngine _getEngine(String name) {
        
        // try to find the engine name in the configuration file
        List<ConfigurationProperty> engineProperties = ConfigurationManager
                .getInstance()
                .getProperty(ConfigurationManager.getModule("ddp-common"))
                .getProperties("Engines.Engine");   
        
        if(engineProperties == null || engineProperties.isEmpty()) {
            throw new InternalErrorException("No DDP engines found.");
        }
        
        String engineClassName = null;
        
        for(ConfigurationProperty engineProperty : engineProperties) {
            ConfigurationProperty nameProperty = engineProperty.getProperty("Name");
            if(nameProperty == null) {
                throw new InternalErrorException("DDP Engine does not have a name. Try deleting\n" +
                        "$HOME/KeplerData/modules/ddp-common/configuration/configuration.xml\n" +
                        "and restarting Kepler.");
            }
            
            if (nameProperty.getValue().equalsIgnoreCase(name)) {
                ConfigurationProperty classProperty = engineProperty.getProperty("ImplementationClass");
                if(classProperty == null) {
                    throw new InternalErrorException("DDP Engine does not have an implementation class.\n" +
                            "Try deleting " +
                            "$HOME/KeplerData/modules/ddp-common/configuration/configuration.xml\n" +
                            "and restarting Kepler.");
                }
                engineClassName = classProperty.getValue();
                break;
            }
        }
        
        // try to instantiate the engine        
        DDPEngine newEngine = null;
        if(engineClassName != null) {
            try {
                Class<?> clazz = Class.forName(engineClassName);
                Constructor<?> constructor = clazz.getConstructor(DDPDirector.class);
                newEngine = (DDPEngine) constructor.newInstance(new Object[] {this});
            } catch(Exception e) {
                throw new InternalErrorException(this, e, "Could not instantiate " + name + " engine.");
            }
        }
        
        return newEngine;
    }
    
    /** Create parameters and the default engine. */
	private void _initializeParameters() {
    	    
	    try {			
			jobArguments = new StringParameter(this, "jobArguments");
		} catch (Throwable t) {
			throw new InternalErrorException(this, t,
					"Cannot create jobArguments parameter.");
		}
		
		try {
			configDir = new StringParameter(this, "configDir");
		} catch (Throwable t) {
			throw new InternalErrorException(this, t,
					"Cannot create configDir parameter.");
		}

		try {
			writeSubWorkflowsToFiles = new Parameter(this,
					"writeSubWorkflowsToFiles");
			writeSubWorkflowsToFiles.setTypeEquals(BaseType.BOOLEAN);
			writeSubWorkflowsToFiles.setToken(BooleanToken.FALSE);
		} catch (Throwable t) {
			throw new InternalErrorException(this, t,
					"Cannot create writeModelsToFiles parameter.");
		}

		try {
			includeJars = new StringParameter(this, "includeJars");
		} catch (Throwable t) {
			throw new InternalErrorException(this, t,
					"Cannot create includeJars parameter.");
		}
		
		try {
			displayRedirectDir = new StringParameter(this, "displayRedirectDir");
		} catch (Throwable t) {
			throw new InternalErrorException(this, t,
					"Cannot create displayRedirectDir parameter.");
		}
		
		try {
            Parameter DEFAULT = new Parameter(this, DEFAULT_NAME);
            DEFAULT.setToken(DEFAULT_INTTOKEN);
            DEFAULT.setVisibility(Settable.EXPERT);
            DEFAULT.setPersistent(false);
		} catch(Throwable t) {
		    throw new InternalErrorException(this, t,
		            "Cannot create DEFAULT parameter.");
		}
		
        try {
            degreeOfParallelism = new Parameter(this, "degreeOfParallelism");
        } catch(Throwable t) {
            throw new InternalErrorException(this, t, "Cannot create degreeOfParallelism parameter.");
        }
        degreeOfParallelism.setExpression(DEFAULT_NAME);
        degreeOfParallelism.addChoice(DEFAULT_NAME);
        
        // hide these parameters since they are not used.
        startTime.setVisibility(Settable.NONE);
        stopTime.setVisibility(Settable.NONE);
        localClock.setVisibility(Settable.NONE);

        try {
            startServerType = new StringParameter(this, "startServerType");
            startServerType.setToken("default");
        } catch(Throwable t) {
            throw new InternalErrorException(this, t, "Cannot create startServerType parameter.");
        }
        
        try {
            engine = new StringParameter(this, "engine");
        } catch (Throwable t) {
            throw new InternalErrorException(this, t,
                    "Cannot create engine parameter.");
        }

        // NOTE: all the parameters must be created before setting the engine,
        // since the engine may reference the parameters.

        // get the default engine name
        ConfigurationProperty defaultProperty = ConfigurationManager
                .getInstance()
                .getProperty(ConfigurationManager.getModule("ddp-common"))
                .getProperty("Engines.default");
        if(defaultProperty == null || defaultProperty.getValue().trim().isEmpty()) {
            System.err.println("WARNING: default DDP engine not found in configuration files; using Spark.");
            _defaultEngine = "Spark";
        } else {
            _defaultEngine = defaultProperty.getValue().trim();
        }
        
        // load the default engine
        try {
            engine.setToken("default");
        } catch (IllegalActionException e) {
            throw new InternalErrorException(this, e, "Error getting default DDP engine.");
        }
        
        // add choices for all engines
        List<ConfigurationProperty> engineProperties = ConfigurationManager
                .getInstance()
                .getProperty(ConfigurationManager.getModule("ddp-common"))
                .getProperties("Engines.Engine");   
        
        if(engineProperties == null || engineProperties.isEmpty()) {
            throw new InternalErrorException("No DDP engines found.");
        }
         
        engine.addChoice("default");
        for(ConfigurationProperty engineProperty : engineProperties) {
            ConfigurationProperty nameProperty = engineProperty.getProperty("Name");
            if(nameProperty == null) {
                throw new InternalErrorException("DDP Engine does not have a name. Try deleting\n" +
                        "$HOME/KeplerData/modules/ddp-common/configuration/configuration.xml\n" +
                        "and restarting Kepler.");
            }
            engine.addChoice(nameProperty.getValue());
        }
	}
	    
    ///////////////////////////////////////////////////////////////////
    ////                      private fields                     //////

	/** The engine object. */
	private DDPEngine _engine;
	
	private String _defaultEngine;
}
