/* A base class for DDP engines.
 * 
 * Copyright (c) 2013 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2015-11-23 12:26:41 -0800 (Mon, 23 Nov 2015) $' 
 * '$Revision: 34245 $'
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.kepler.build.modules.Module;
import org.kepler.build.modules.ModuleTree;
import org.kepler.configuration.ConfigurationProperty;
import org.kepler.ddp.Utilities;
import org.kepler.ddp.actor.pattern.DDPDataSink;
import org.kepler.ddp.actor.pattern.DDPDataSource;
import org.kepler.ddp.actor.pattern.DDPPatternActor;
import org.kepler.ddp.actor.pattern.SingleInputPatternActor;
import org.kepler.sms.SemanticType;

import ptolemy.actor.CompositeActor;
import ptolemy.actor.Executable;
import ptolemy.actor.TypeAttribute;
import ptolemy.actor.TypedIOPort;
import ptolemy.actor.gui.Effigy;
import ptolemy.actor.gui.ModelDirectory;
import ptolemy.actor.gui.PtolemyEffigy;
import ptolemy.actor.parameters.PortParameter;
import ptolemy.data.BooleanToken;
import ptolemy.data.IntToken;
import ptolemy.data.StringToken;
import ptolemy.data.expr.Parameter;
import ptolemy.data.expr.Variable;
import ptolemy.data.type.Type;
import ptolemy.kernel.util.Attribute;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import ptolemy.kernel.util.NamedObj;
import ptolemy.kernel.util.ScopeExtender;
import ptolemy.kernel.util.Workspace;

/** A base class for DDP engines.
 * 
 * @author Daniel Crawl
 * @version $Id: DDPEngine.java 34245 2015-11-23 20:26:41Z crawl $
 * 
 */
public abstract class DDPEngine implements Cloneable {

	/** Create a new DDPEngine.
	 *  @param director The director containing this engine.
	 */
    public DDPEngine(DDPDirector director) throws IllegalActionException, NameDuplicationException {
        _director = director;
        _addParameters();
    }
    
    /** React to a parameter change. */
	public void attributeChanged(Attribute attribute) throws IllegalActionException {
	    
	    if(attribute == _director.jobArguments) {
            _jobArgumentsMap.clear();
	        String val = _director.jobArguments.stringValue();
	        if (val != null && !val.isEmpty()) {
	            for(String entry : val.split(",")) {
	            	if (entry != null && !val.isEmpty()) {
		                String[] nameVal = entry.split("=");
		                if(nameVal.length != 2) {
		                    throw new IllegalActionException(_director, 
		                            "Job arguments must be of the form: name1 = value1, name2 = value2");
		                }
		                _jobArgumentsMap.put(nameVal[0].trim(), nameVal[1].trim());
	            	}
	            }
	        }
	    }
	}

	/** Clone the engine into the containing director's workspace. */
	@Override
	public Object clone() throws CloneNotSupportedException {
		return clone(_director.workspace());
	}
	
    /** Clone the object into the specified workspace.
     *  @param workspace The workspace for the cloned object.
     *  @exception CloneNotSupportedException Not thrown in this base class
     *  @return The new Attribute.
     */
    public Object clone(Workspace workspace) throws CloneNotSupportedException {
        DDPEngine newObject = (DDPEngine) super.clone();
        newObject._additionalJars = new HashSet<String>();
        newObject._classLoader = null;
        newObject._configDirStr = null;
        newObject._container = null;
        newObject._degreeOfParallelism = 1;
        // newObject._director is set in DDPDirector.clone().
        newObject._displayRedirectDir = "";
        newObject._jobArgumentsMap = new HashMap<String,String>();
        newObject._jobDir = null;
        newObject._jobLock = new Object();
        newObject._random = new Random();
        newObject._stopRequested = false;
        newObject._writeSubWorkflowsToFiles = false;
        return newObject;
    }

    /** Close all the effigies created. */
    public static void closeAllEffigies() throws IllegalActionException {
        synchronized(_effigiesForStubModels) {
            for(Effigy effigy : _effigiesForStubModels) {
                _setEffigiesToNotModified(effigy);
                effigy.closeTableaux();
                try {
                    effigy.setContainer(null);
                } catch(NameDuplicationException e) {
                    throw new IllegalActionException(
                        "Error setting effigy container to null: " +
                            e.getMessage());
                }
            }
            _effigiesForStubModels.clear();
        }
    }

    /** Create an Effigy for a model so that windows may be opened by gui actors. */
    public static synchronized void createEffigy(CompositeActor model) {
        
        List<?> configurations = ptolemy.actor.gui.Configuration.configurations();
        
        // make sure there is at least one Configuration. there are none when
        // running headless.
        if(!configurations.isEmpty()) {
            ptolemy.actor.gui.Configuration configuration = 
                    (ptolemy.actor.gui.Configuration) configurations.iterator().next();

        try {
    
                PtolemyEffigy effigy = new PtolemyEffigy(configuration.workspace());
                effigy.setModel(model);
                ModelDirectory directory = (ModelDirectory) configuration
                        .getEntity("directory");
                
                effigy.setName(model.getName());
                
                effigy.identifier.setExpression(model.getName());
                if (directory != null) {
                    if (directory.getEntity(model.getName()) != null) {
                        // Name is already taken.
                        int count = 2;
                        String newName = effigy.getName() + " " + count;
                        while (directory.getEntity(newName) != null) {
                            newName = effigy.getName() + " " + ++count;
                        }
                        effigy.setName(newName);
                    }
                }
                effigy.setContainer(directory);
    
                // do not open a new window for the workflow
                /*Tableau t = configuration.openModel(model);*/
                
                _effigiesForStubModels.add(effigy);
                
            } catch (Exception e) {
                throw new RuntimeException("Error creating Effigy.", e);
            }
        }
    }

    /** Get the directory to redirect display related actors. */
    public String getDisplayRedirectDir() throws IllegalActionException {
    	return _displayRedirectDir;
    }

    /** Get the model for a specific name. */
    public static CompositeActor getModel(String name) {
        return _subWorkflows.get(name);
    }

    /** Get the name of the engine. */
    public final String getName() {
    	return _engineName;
    }
    
    /** Valid types of servers that can be started. The first value is the
     *  default for the startServerType parameter.
     */
    public String[] getServerTypes() {
        return new String[] {"default", DDPDirector.SAME_JVM_STRING, DDPDirector.DISTRIBUTED_STRING};
    }

    /** Execute the engine. In this base class, does nothing. */
    public void fire() throws IllegalActionException {

        // iterate the source actors once
        final List<DDPDataSource> sourceActors = _container.entityList(DDPDataSource.class);
        for(DDPDataSource source : sourceActors) {
            final int rc = source.iterate(1);
            if(rc == Executable.NOT_READY) {
                throw new IllegalActionException(_director, "Actor " + source.getName() +
                    " is not ready to fire. (Maybe prefire() returned false?)");
            }
        }
        
        // call prefire for all the sink actors
        final List<DDPDataSink> sinkActors = _container.entityList(DDPDataSink.class);
        for(DDPDataSink sink : sinkActors) {
            if(!sink.prefire()) {
                throw new IllegalActionException(_director, "Actor " + sink.getName() +
                    " is not ready to fire. (Prefire() returned false.)");
            }
        }

        // run the DDP job
        _executeDDPJob();
        
        if(!_stopRequested) {
            // call fire and postfire for all the sink actors
            for(Object object : sinkActors) {
                final DDPDataSink sink = (DDPDataSink)object;
                sink.fire();
                sink.postfire();            
            }
        }
    }
    
    /** Postfire the engine. In this base class, returns true. */
	public boolean postfire() throws IllegalActionException {
		return true;
	}

    /** Initialize fields from parameters. */
    public void preinitialize() throws IllegalActionException {
        _container = (CompositeActor) _director.getContainer();
        _configDirStr = _director.configDir.stringValue();
        _writeSubWorkflowsToFiles = ((BooleanToken)_director.writeSubWorkflowsToFiles.getToken()).booleanValue();
        _displayRedirectDir = _director.displayRedirectDir.stringValue();
		_classLoader = Thread.currentThread().getContextClassLoader();
		_stopRequested = false;
        _jobDir = null;

        
        IntToken parallelism = (IntToken)_director.degreeOfParallelism.getToken();
        if(parallelism == DDPDirector.DEFAULT_INTTOKEN) {
            _degreeOfParallelism = 1;
        } else {
            _degreeOfParallelism = parallelism.intValue();
        }

		// see if the we should use a server in the same jvm
		String typeStr = ((StringToken)_director.startServerType.getToken()).stringValue();
		if(typeStr != null && (typeStr.equals("default") ||
		        typeStr.equals(DDPDirector.SAME_JVM_STRING))) {
		    _sameJVM = true;
		} else {
		    _sameJVM = false;
		}
		
		// close any effigies that were opened during the previous execution
		// FIXME this closes all the effigies, not just the ones used by
		// this engine.
		closeAllEffigies();
    }
    
    /** Stop any running DDP jobs. */
    public void stop() throws IllegalActionException {
        _stopRequested = true;
    }
    
    /** Perform cleanup. */
    public void wrapup() throws IllegalActionException {

        _subWorkflows.clear();

    }
    
    ///////////////////////////////////////////////////////////////////
    ////                      protected methods                  //////

    /** Add parameters to the containing director. In this base class, does nothing. */
    protected void _addParameters() throws IllegalActionException, NameDuplicationException {
        
    }
    
    /** Check that the configuration directory is set and exists on the file system. */
    protected void _checkConfigDir() throws IllegalActionException {
    
        if(_configDirStr.trim().isEmpty()) {
        	
        	String moduleName = _engineName.toLowerCase();
        	
            // set the default location of the config directory
            String workflowDirStr = System.getProperty(moduleName + ".workflowdir");
            if(workflowDirStr == null) {
                throw new IllegalActionException(_director, "System property " +
                		moduleName + ".workflowdir not set.");
            }
            
            _configDirStr = workflowDirStr + File.separator +
                "tools" + File.separator + "conf";
        }
        
        // make sure conf dir exists
        final File configDirFile = new File(_configDirStr);
        if(!configDirFile.exists()) {
            throw new IllegalActionException(_director, _engineName + " configuration directory " +
                    _configDirStr + " does not exist.");
        }
        
    }
    
    /** Check the existence of required files before starting the DDP server.
     *  The list of required files is found in configuration.xml for each
     *  engine, in Engine.Server.Required.
     */
    protected boolean _checkFilesBeforeStartingServer() throws IllegalActionException {
        
        ConfigurationProperty engineProperty = Utilities.getEngineProperty(_engineName, _director);
        
        List<ConfigurationProperty> requiredProperties = 
            engineProperty.getProperties("Server.Required.File");
        if(requiredProperties != null && !requiredProperties.isEmpty()) {
            final File configDirFile = new File(_configDirStr);
            final String dirStr = configDirFile.getParentFile().getAbsolutePath();
            for(ConfigurationProperty fileProperty : requiredProperties) {
                File requiredFile = new File(dirStr, fileProperty.getValue());
                System.out.println("Check that file exists before starting the server: " + requiredFile);
                if(!requiredFile.exists()) {
                    System.out.println("ERROR: required file not found: " + requiredFile);
                    return false;
                }
            }
        }
        return true;
    }

    /** Check the container of this director for incorrect actors, etc. */
    protected void _checkModel() throws IllegalActionException {
                
        // make sure container only has ddp actors
        _checkModelForNonDDPActors(_container);
        
    }
    
    /** Make sure the container only contains DDP actors. */
    protected void _checkModelForNonDDPActors(CompositeActor container)
            throws IllegalActionException {
        
        // verify all contained actors are ddp pattern actors
        for(Object object : container.entityList()) {
            if(!(object instanceof DDPPatternActor)) {
                throw new IllegalActionException(_director, ((NamedObj)object).getName() +
                        " is not a DDP Pattern actor.");
            } else {
                // make sure composites have a director
                // NOTE: composites must have a director even if the execution
                // class is specified (and the sub-workflow is not used) since
                // the output port type can only be set if the composite actor
                // is opaque. 
                // @see TypedIOPort.getType()
                //
                DDPPatternActor actor = (DDPPatternActor)object;
                if(!actor.isOpaque()) {
                    throw new IllegalActionException(_director, 
                            ((NamedObj)object).getName() +  " must contain a director.");
                }
            }   
        }
    }
    
    /** Check if the DDP engine server is running. If not, try to start it.
     *  @param socketAddress Host and port of the server to check.
     *  @param startScriptStr The script to start the server if not running.
     *  @return True if a server was started, false if could connect to already running server. 
     */
    protected boolean _checkServer(InetSocketAddress socketAddress, String startScriptStr)
    		throws IllegalActionException {
    	
        boolean startedServer = false;
        
    	synchronized(_serverStartStopLock) {
	        Socket socket = null;
	        try {
	        	socket = new Socket();
		        boolean connected = false;
		        try {
		            socket.connect(socketAddress, _CONNECT_TIMEOUT);
		            connected = true;
		        } catch (IOException e) {
		            
		            System.out.println(_engineName + " server " + socketAddress +
		            		" does not appear to be running. Starting...");
		            
		            // start the server
		            
		            if(!_checkFilesBeforeStartingServer()) {
		                throw new IllegalActionException(_director,
		                        "One or more files required to start the server were not found.");
		            }
		            	            
		            // see if the script is executable. kepler modules are zipped,
		            // which does not preserve the permissions.
		            File startScriptFile = new File(startScriptStr);
		            if(!startScriptFile.canExecute()) {
		                throw new IllegalActionException(_director, 
		                        "The script " + startScriptFile + " is not executable.\n" +
		                        		"You must change the permissions so that " +
		                        		startScriptFile.getName() + 
		                        		" and all the other scripts in \n" +
		                        		startScriptFile.getParent() + " are executable.");
		            }
		            
		            ProcessBuilder builder = new ProcessBuilder(startScriptStr);
		            
		            // make sure JAVA_HOME is set
		            java.util.Map<String,String> env = builder.environment();
		            if(env.get("JAVA_HOME") == null) {
		                env.put("JAVA_HOME", System.getProperty("java.home"));
		            }

		            builder.redirectErrorStream(true);
		            
		            try {
		                Process process = builder.start();
		                InetSocketAddress newAddress = 
		                        _parseOutputFromStartingServer(process.getInputStream());
		                if(newAddress != null) {
		                    socketAddress = newAddress;
		                }
		                process.waitFor();
		                startedServer = true;
		            } catch (Exception e1) {
		                throw new IllegalActionException(_director, e1, "Unable to start " +
		                		_engineName + " server.");
		            }
		            	                        
		            int tries = 0;
		            while(tries < 5) {
		                // wait for the server to start
		                try {
		                    Thread.sleep(5000);
		                    tries++;
		                    System.out.print("Connecting to " + _engineName + " server port try #" + tries + ": ");
		                    try {
		                    	socket.close();
		                        socket = new Socket();
		                        socket.connect(socketAddress, _CONNECT_TIMEOUT);
		                        connected = true;
		                        System.out.println("connected.");
		                        break;
		                    } catch (IOException e1) {
		                        // do nothing
		                        System.out.println(e1);
		                    }
		                } catch (InterruptedException e2) {
		                    throw new IllegalActionException(_director, e2, "Error while sleeping.");
		                }
		            }
		            
		            
		            // if we get here, we were able to connect to the master/job manager port.
		            // however, the server may not be completely initialized, so wait a few more seconds
		            System.out.println("Waiting 15 seconds for " + _engineName + " server to initialize.");
		            try {
		                Thread.sleep(15000);
		            } catch (InterruptedException e2) {
		                throw new IllegalActionException(_director, e2, "Error while waiting " +
		                        " for " + _engineName + " server to initialize.");
		            }
		
		        }
		        
		        if(connected) {
		            try {
		                socket.close();
		                socket = null;
		            } catch (IOException e) {
		                throw new IllegalActionException(_director, e, "Error closing socket.");
		            }
		        } else {
		            throw new IllegalActionException(_director, 
		            		"Could not connect to " + _engineName + " server: " + socketAddress);
		        }
	        } finally {
	        	if(socket != null) {
	        		try {
						socket.close();
					} catch (IOException e) {
		                throw new IllegalActionException(_director, e, "Error closing socket.");
					}
	        	}
	        }
    	}
    	
    	return startedServer;
    }

    /** Copy the workflow parameters from one sub-workflow to another including
     *  the parameters in all the containers of the source.
     *
     *  @param sourceSubWorkflow the source sub-workflow
     *  @param destSubWorkflow the destination sub-workflow
     */
	protected void _copyParameters(DDPPatternActor sourceSubWorkflow, DDPPatternActor destSubWorkflow)
		throws IllegalActionException {

		// clone the parameters into the same workspace as the destination
		// subworkflow
		final Workspace workspace = ((NamedObj) destSubWorkflow).workspace();
		
        // get the parameters up the hierarchy
        final java.util.Map<String,Variable> parameters = 
            _getParametersInHierarchy(sourceSubWorkflow.getContainer());
        // copy the parameters into the pactor
        // TODO: only need to get the parameters once per run, instead of once
        // per pactor
        for(Variable p : parameters.values()) {
            
            //System.out.println("copying parameter: " + p.getFullName());
            
            // make sure the cloned actor does not already have a parameter
            // with the same name. this can happen if the parameter is a SharedParameter
            if(destSubWorkflow.getAttribute(p.getName()) == null) {   
                //System.out.println("parameter " + p);
                try {
                    // if the parameter is a PortParameter, create a new parameter
                    // instead of clone it, since we do not want the associated port

                    // we also need to set persistence so the parameter appears when serialized
                    if(p instanceof PortParameter) {
                        final Parameter copiedParameter = new Parameter((NamedObj) destSubWorkflow, p.getName()); 
                    	String value = p.getExpression();
                        if(p.isStringMode()) {
                        	copiedParameter.setExpression("\"" + value + "\"");
                        } else {
                        	copiedParameter.setExpression(value);
                        }
                        copiedParameter.setPersistent(true);
                    } else {
                        final Variable cloneParameter;
                        
                        // NOTE: Variable values are not written during exportMoML(),
                        // so we need to put the value in a new Parameter.
                        if(p instanceof Parameter) {
                            cloneParameter = (Variable) p.clone(workspace);
                        } else {
                            cloneParameter = new Parameter(workspace);
                        }
                        cloneParameter.setContainer((NamedObj) destSubWorkflow);
                        cloneParameter.setPersistent(true);

                        if(!(p instanceof Parameter)) {
                            cloneParameter.setName(p.getName());
                            ((Parameter)cloneParameter).setExpression(p.getExpression());
                        }
                    } 
                } catch(Exception e) {
                    throw new IllegalActionException(_director, e, "Unable to add " +
                        " parameter " + p.getFullName() + " to " + sourceSubWorkflow.getFullName());
                }
            }
        }
	}
	
	/** Create a new directory for this job. */
	protected void _createJobDirectory() throws IllegalActionException {

		int number = _random.nextInt(Integer.MAX_VALUE);
		File directory = new File(System.getProperty("user.home")
				+ File.separator + number);
		while (directory.exists()) {
			number = _random.nextInt(Integer.MAX_VALUE);
			directory = new File(System.getProperty("user.home")
					+ File.separator + number);
		}
		if (!directory.mkdir()) {
			throw new IllegalActionException("Could not create directory "
					+ directory.getPath());
		}
		_jobDir = directory.getPath() + File.separator;
		_log.debug("Job directory is " + _jobDir);
	}

    /** Get the parameters for a NamedObj and all its containers. */
    protected static Map<String,Variable> _getParametersInHierarchy(NamedObj namedObj)
    {
        java.util.Map<String,Variable> retval = new HashMap<String,Variable>();
        final List<?> attributes = namedObj.attributeList();
        for(Object object : attributes) {
            if(object instanceof Variable) {
                retval.put(((Variable)object).getName(), (Variable)object);
            }
            
            
            if(object instanceof ScopeExtender) {
                try {
                    ((ScopeExtender)object).expand();
                } catch (IllegalActionException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                for(Object subAttribute : ((ScopeExtender)object).attributeList()) {
                    if(subAttribute instanceof Variable) {
                        retval.put(((Variable)subAttribute).getName(), (Variable)subAttribute);                        
                    }
                }
            }
        }
               
        // get the parameters above 
        final NamedObj container = namedObj.getContainer();
        if(container != null) {
            final java.util.Map<String,Variable> aboveParameters = _getParametersInHierarchy(container);
            for(java.util.Map.Entry<String,Variable> entry : aboveParameters.entrySet()) {
                final String name = entry.getKey();
                // do not add parameters with the same name since they are overridden
                if(!retval.containsKey(name)) {
                    retval.put(name, entry.getValue());
                }
            }
        }
        
        // remove certain parameters
        java.util.Map<String,Variable> copy = new HashMap<String,Variable>(retval);
        for(java.util.Map.Entry<String,Variable> entry : copy.entrySet()) {
            final String name = entry.getKey();
            final Variable parameter = entry.getValue();
            // remove parameters whose name begins with "_"
            // remove semantic type parameters
            if(name.startsWith("_") || (parameter instanceof SemanticType)) {
                retval.remove(name);
            }
        }
        
        return retval;
    }

    /** Get a list of jars required for director to start. 
     *  It also set _classLoader value based on the jars.
     */
    protected List<URI> _getJarList() throws IllegalActionException{
        final List<URI> jarPaths = new LinkedList<URI>();
        final List<File> jarsWithRelativePaths = new LinkedList<File>();
        
        for(String additionalJar : _additionalJars) {
            jarsWithRelativePaths.add(new File(additionalJar));
        }
        
        // get the jars in the director's includeJars parameter
        String includeJarsStr = _director.includeJars.stringValue();
        if(includeJarsStr != null && !includeJarsStr.isEmpty()) {
            for(String jarPath : includeJarsStr.split(",")) {
                File jarFile = new File(jarPath);
                // see if jar is an absolute path
                if(jarFile.isAbsolute()) {
                    if(!jarFile.exists() || !jarFile.canRead()) {
                        throw new IllegalActionException(_director,
                                "Jar does not exist or cannot be read: " + jarFile);
                    }
                    // jars with absolute paths are added directly
                    System.out.println("Adding jar: " + jarFile.getAbsolutePath());
                    jarPaths.add(jarFile.toURI());
                } else {
                    jarsWithRelativePaths.add(jarFile);
                }
            }
        }
                    
        // add the module jars, e.g., actors.jar, ptolemy.jar, etc.
        // also add any jars in includeJars with a relative path - these jars
        // are assumed to be module/lib/jar.
        final ModuleTree moduleTree = ModuleTree.instance();
        for(Module module : moduleTree) {
            
            final File moduleJar = module.getTargetJar();
            
            // add the module jar if it exists.
            // some modules, e.g., outreach, do not have jars.
            if(moduleJar.exists()) {
            	jarPaths.add(moduleJar.toURI());                
            }

            final List<File> moduleJars = module.getJars();
            for(File jar : moduleJars) {
                // include kepler-tasks.jar since we need classes
                // in org.kepler.build to initialize kepler in the
                // stub. see StubUtilities.initializeKepler()
                if(jar.getName().equals("kepler-tasks.jar")) {
                    //System.out.println("adding jar " + jar);
                	jarPaths.add(jar.toURI());                
                } else if(jar.getName().matches("^log4j.*jar$") || //add log4j jar since it is used for display-redirect function in DDP.
                        jar.getName().equals("ant.jar")) {
                        jarPaths.add(jar.toURI());
                } else if(!jarsWithRelativePaths.isEmpty()) {
                    for(File jarFile : jarsWithRelativePaths) {
                        if(jar.getName().equals(jarFile.getName())) {
                            System.out.println("Adding jar in module " + module.getName() +
                                    ": " + jar);
                            jarPaths.add(jar.toURI());
                        }
                    }
                }
            }                        
        }
        
        // add any jars specified by the actors.
        final List<DDPPatternActor> actors = _container.entityList(DDPPatternActor.class);
        for(DDPPatternActor actor : actors) {
            final String jarsStr = actor.getJars();
            if(!jarsStr.isEmpty()) {
                final String[] jars = jarsStr.split(",");
                for(String jar : jars) {
                    final File jarFile = new File(jar);
                    if(!jarFile.exists() || !jarFile.canRead()) {
                        throw new IllegalActionException(actor,
                                "Jar does not exist or cannot be read: " + jarFile.getAbsolutePath());
                    }
                    System.out.println("Adding jar for " + actor.getFullName() + ": " +
                            jarFile.getAbsolutePath());
                    jarPaths.add(jarFile.toURI());
                }
            }
        }
        
		URL[] jarArray;
		try{
	        List<URL> jarURLs = new LinkedList<URL>();
	        for (URI jarURI : jarPaths) {
	        	jarURLs.add(jarURI.toURL());
	        }
        
	        jarArray = jarURLs.toArray(new URL[jarURLs.size()]);	

			if (jarArray != null && jarArray.length > 0) {
				_classLoader = new URLClassLoader(jarArray, Thread.currentThread()
						.getContextClassLoader());
				Thread.currentThread().setContextClassLoader(_classLoader);
			}			
			
		} catch (MalformedURLException e) {
			e.printStackTrace();
			throw new IllegalActionException(_director, e.getMessage());
		}
		
        return jarPaths;
    }
    
    /** Execute the DDP job. */
    protected abstract void _executeDDPJob() throws IllegalActionException;

    /** Parse the output from the script that starts the server. In this
     *  class, does nothing and returns null.
     *  @return If the start script specifies a server URL, returns the
     *  socket address for that URL. Otherwise, returns null.  
     */
    protected InetSocketAddress _parseOutputFromStartingServer(InputStream input) throws IOException, IllegalActionException {
        return null;
    }
    
    /** Remove engine-specific parameters from the director.
     *  Does nothing in this base class.
     */
    protected void _removeParameters() throws IllegalActionException, NameDuplicationException {
                
    }

    /** Set the port types inside a cloned pattern actor.
     *  @param actor the cloned actor
     */
	protected Map<String, Type> _setPortTypes(DDPPatternActor actor)
		throws IllegalActionException {
		
		final Map<String,Type> typeMap = new HashMap<String,Type>();
		
        // set the input ports of the stub source actors. the output ports will be
        // set by the actor's PortFunction class, see _createPortFunction().
        // the stub sink actor ports do not need to be set.       
        final List<?> pactorPorts = actor.inputPortList();
        for(Object object : pactorPorts) {
            final TypedIOPort pactorPort = (TypedIOPort) object; 
            
            // get the connected ports - the input ports of the source actors
            final List<?> connectedPorts = pactorPort.insidePortList();
            for(Object object2 : connectedPorts) {
                final TypedIOPort connectedPort = (TypedIOPort) object2;
                
                // set the types of ports connected to the pactor port so their
                // types can be used to set the pact types in the stub
                TypeAttribute typeAttribute;
                try {
                    typeAttribute = new TypeAttribute(connectedPort, connectedPort.getName() + "Type");
                } catch(NameDuplicationException e) {
                    throw new IllegalActionException(_director, e,
                        "Error creating type attribute for " + connectedPort.getFullName());
                }
                typeMap.put(connectedPort.getName(), connectedPort.getType());
                typeAttribute.setExpression(connectedPort.getType().toString());
                
                // call TypedIOPort.attributeChanged() with the type attribute so that
                // the port type will be set. this is necessary if the cloned subworkflow
                // is not serialized.
                connectedPort.attributeChanged(typeAttribute);
            }
        }   
        return typeMap;
	}    
    
    ///////////////////////////////////////////////////////////////////
    ////                      protected fields                   //////

    /** DDP Engine configuration directory. */
    protected String _configDirStr;

    /** Directory for the current job. */
    protected String _jobDir;

	/** The ClassLoader class for loading implementation class for DDP actors. */
    protected ClassLoader _classLoader;

    /** Timeout when seeing if server is running. */
    protected final static int _CONNECT_TIMEOUT = 5*1000;

    /** Directory for display redirect. */
    protected String _displayRedirectDir;

    /** If true, write the sub-workflows to files, otherwise pass them as
     *  to in the configuration object.
     */
    protected boolean _writeSubWorkflowsToFiles = false;

	/** The container of this director. */
    protected CompositeActor _container;

    /** The default degree of parallelism for ddp pattern actors. */
	protected int _degreeOfParallelism = 1;
	
    /** A collection of job arguments as key-values. */
    protected Map<String,String> _jobArgumentsMap = new HashMap<String,String>();

    /** The containing director. */
	protected DDPDirector _director;
	
	 /** The name of the engine. */
	protected String _engineName = "unknown";
	
    /** A mapping of model name to model. FIXME: change to full name */
    protected static final java.util.Map<String,SingleInputPatternActor> _subWorkflows =
            Collections.synchronizedMap(new HashMap<String,SingleInputPatternActor>());

    /** If true, run and use server in the same JVM as Kepler. */
    protected boolean _sameJVM = true;
    
    /** A set of additional jar names to send to the server. */
    protected Set<String> _additionalJars = new HashSet<String>();
    
    /** A lock for DDP jobs. */
    protected Object _jobLock = new Object();
    
    /** A static object used for synchronization. */
    protected final static Object _serverStartStopLock = new Object();  

    
    ///////////////////////////////////////////////////////////////////
    ////                      private methods                    //////

    /** Set an effigy and any contained effigies to be not modified. */
    private static void _setEffigiesToNotModified(Effigy effigy) {
        //System.out.println("setting not modified for : " + effigy.getFullName());
        effigy.setModified(false);
        for(Effigy containedEffigy : effigy.entityList(Effigy.class)) {
            _setEffigiesToNotModified(containedEffigy);
        }
    }

    ///////////////////////////////////////////////////////////////////
    ////                      private fields                     //////

	/** Random number generator for job directories. */
	private Random _random = new Random();

	/** Logging. */
    private final static Log _log = LogFactory.getLog(DDPEngine.class);
    
    /** A list of Effigies opened for models. */
    private static List<Effigy> _effigiesForStubModels = 
        Collections.synchronizedList(new LinkedList<Effigy>());
    
    /** If true, the user requested the workflow to stop. */
    private boolean _stopRequested = false;
}
