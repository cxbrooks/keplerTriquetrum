/*
 * Copyright (c) 2012 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2015-11-23 12:26:17 -0800 (Mon, 23 Nov 2015) $' 
 * '$Revision: 34244 $'
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

package org.kepler.ddp;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.ResourceBundle;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.kepler.configuration.ConfigurationManager;
import org.kepler.configuration.ConfigurationProperty;
import org.kepler.ddp.actor.pattern.stub.StubBaseActor;
import org.kepler.ddp.director.DDPEngine;
import org.kepler.provenance.ProvenanceRecorder;
import org.kepler.reporting.ReportingListener;

import au.edu.jcu.kepler.hydrant.DisplayRedirectFilter;
import ptolemy.actor.CompositeActor;
import ptolemy.actor.Director;
import ptolemy.actor.ExecutionListener;
import ptolemy.actor.IORelation;
import ptolemy.actor.Manager;
import ptolemy.actor.TypedIOPort;
import ptolemy.actor.injection.ActorModuleInitializer;
import ptolemy.actor.injection.ActorModuleInitializer.Initializer;
import ptolemy.actor.injection.PtolemyInjector;
import ptolemy.actor.injection.PtolemyModule;
import ptolemy.data.DoubleToken;
import ptolemy.data.IntToken;
import ptolemy.data.LongToken;
import ptolemy.data.RecordToken;
import ptolemy.data.StringToken;
import ptolemy.data.Token;
import ptolemy.data.type.BaseType;
import ptolemy.data.type.RecordType;
import ptolemy.data.type.Type;
import ptolemy.domains.ddf.kernel.DDFDirector;
import ptolemy.domains.sdf.kernel.SDFDirector;
import ptolemy.kernel.util.Attribute;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.Nameable;
import ptolemy.kernel.util.Workspace;
import ptolemy.moml.MoMLParser;
import ptolemy.moml.filter.BackwardCompatibility;
import ptolemy.moml.filter.RemoveGraphicalClasses;

/** A collection of utilities for DDP classes. The methods and fields in this
 *  class are not specific to a DDP engine.
 * 
 * @author Daniel Crawl and Jianwu Wang
 * @version $Id: Utilities.java 34244 2015-11-23 20:26:17Z crawl $
 * 
 */

public class Utilities {
	    
    /** Check the director and its iterations with the runWorkflowLifecyclePerInput value.
     *  If the director is SDF, this method changes the iterations based on the
     *  value in runWorkflowLifecyclePerInput. If the director is DDF, then
     *  runWorkflowLifecyclePerInput is changed to true.
     *  @param model the model to execute in the stub.
     *  @param  runWorkflowLifecyclePerInput if true, the full lifecycle of the
     *  model will be executed for each input. If false, only one iteration of the
     *  model will be execute for each input.
     *  @return the (possibly changed) runWorkflowLifecyclePerInput value.
     */
    public static boolean checkDirectorIterations(CompositeActor model, boolean runWorkflowLifecyclePerInput) {
        
        Director director = model.getDirector();
        if(director instanceof SDFDirector) {
            Token iterations;
            // change the iterations based on runWorkflowLifecyclePerInput
            if(runWorkflowLifecyclePerInput) {
                iterations = IntToken.ONE;
            } else {
                iterations = SDFDirector.UNBOUNDED_INTTOKEN;
            }
            try {
                ((SDFDirector)director).iterations.setToken(iterations);
            } catch (IllegalActionException e) {
                throw new RuntimeException("Error changing iterations for SDF director: " +
                        e.getMessage());
            }
        } else if((director instanceof DDFDirector) && !runWorkflowLifecyclePerInput) {
            runWorkflowLifecyclePerInput = true;
            String message = "Sub-workflow " + model.getName() + 
                    " uses DDF director, but runWorkflowLifecyclePerInput is false;" +
                    " this is not supported so changing runWorkflowLifecyclePerInput to true.";
            System.out.println("WARNING: " + message);
            LOG.warn(message);
        } else {
            String message = "Unexpected director in sub-workflow " +
                    model.getName() + ": " + director.getClass().getName() +
                    ". The sub-workflow may not execute correctly.";
            System.out.println("WARNING: " + message);
            LOG.warn(message);
        }

        return runWorkflowLifecyclePerInput;
    }
    
    /** Create a Manager for a model and optionally start execution.
     *  @param model the workflow
     *  @param listener an ExecutionListener to register with the Manager to
     *  receive error messages.
     *  @param stubSource the source actor in the model
     *  @param stubSink the sink actor in the model
     *  @param  runWorkflowLifecyclePerInput if true, execute the full lifecycle of the
     *  model for each input and the stub must tell the manager to execute the
     *  model. If false, this method starts execution.
     *  @param enablePrintTimeAndMemory if true, print an execution summary each time
     *  the model executes.  
     */
    public static Manager createManagerForModel(CompositeActor model, ExecutionListener listener,
            StubBaseActor stubSource, StubBaseActor stubSink, boolean runWorkflowLifecyclePerInput,
            boolean enablePrintTimeAndMemory) {
       
        Manager manager = null;
        
        // create manager and add model
        try {
            manager = new Manager(model.workspace(), "Manager");
        } catch(IllegalActionException e) {
            throw new RuntimeException("Error creating Manager: " + e.getMessage());
        }

        try {
            model.setManager(manager);
        } catch(IllegalActionException e) {
            throw new RuntimeException("Error setting Manager for sub-workflow: " + e.getMessage());
        }

        manager.enablePrintTimeAndMemory(enablePrintTimeAndMemory);
        manager.addExecutionListener(listener);
                
        if(runWorkflowLifecyclePerInput) {
            stubSource.setRunWorkflowLifecyclePerInput(runWorkflowLifecyclePerInput);
            stubSink.setRunWorkflowLifecyclePerInput(runWorkflowLifecyclePerInput);
        } else {
            try {
                manager.startRun();
            } catch (Exception e) {
                throw new RuntimeException("Error starting sub-workflow.", e);
            }
        }
        
        return manager;
    }

    /** Load the model for a stub from a Nephele Configuration. The 
     *  top-level ports and connected relations are removed.
     */
    public static synchronized CompositeActor getModel(String modelName,
            String modelString, String modelFile, boolean sameJVM, String redirectDir) {
        
        CompositeActor model = null;

        if(modelName == null) {
            throw new RuntimeException("Subworkflow name was not set in configuration.");
        }
        
        if(sameJVM) {
            
            CompositeActor originalModel = DDPEngine.getModel(modelName);
            try {
                model = (CompositeActor) originalModel.clone(new Workspace());
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException("Error cloning subworkflow: " + e.getMessage());
            }
            
            Utilities.removeModelPorts(model);          
            
            // create an effigy for the model so that gui actors can open windows.
            DDPEngine.createEffigy(model);
                        
        } else {
            
            List<?> filters = MoMLParser.getMoMLFilters();
    
            Workspace workspace = new Workspace();
            final MoMLParser parser = new MoMLParser(workspace);
            
            //parser.resetAll();
            
            MoMLParser.setMoMLFilters(null);
            MoMLParser.setMoMLFilters(BackwardCompatibility.allFilters(), workspace);
    
            if (redirectDir.isEmpty()) {
                MoMLParser.addMoMLFilter(new RemoveGraphicalClasses(), workspace);
            } else {
                //redirect display-related actors 
                final String pid = ManagementFactory.getRuntimeMXBean().getName();
                final String threadId = String.valueOf(Thread.currentThread().getId());
                final String displayPath = redirectDir + File.separator + pid + "_" + threadId;
                MoMLParser.addMoMLFilter(new DisplayRedirectFilter(displayPath), workspace);
                final ArrayList<PtolemyModule> actorModules = new ArrayList<PtolemyModule>();
                actorModules.add(new PtolemyModule(ResourceBundle
                            .getBundle("org/kepler/ActorModuleBatch")));
                Initializer _defaultInitializer = new Initializer() {
                    @Override
                    public void initialize() {
                        PtolemyInjector.createInjector(actorModules);
                    }
                };
                ActorModuleInitializer.setInitializer(_defaultInitializer);
            }
            
            // get the model from the configuration
    
            // see if model is in the configuration.
            if(modelString != null) {
    
                try {
                    model = (CompositeActor)parser.parse(modelString);
                } catch (Exception e) {
                    throw new RuntimeException("Error parsing model " + modelName +
                        ": " + e.getMessage());
                }            
            
                //LOG.info("parsed model from " + modelString);
    
            } else {
                
                // the model was saved as a file.
                
                if(modelFile == null) {
                    throw new RuntimeException("No model for " + modelName +
                        " in configuration.");
                }
                
                // load the model
                try {
                    model = (CompositeActor)parser.parseFile(modelFile);
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException("Error parsing model " + modelName +
                        " in file " + modelFile + ": " + e.getMessage());
                }
            
                LOG.info("parsed model from " + modelFile);
            }
            
            // restore the moml filters
            MoMLParser.setMoMLFilters(null);
            MoMLParser.setMoMLFilters(filters);
            
            // remove provenance recorder and reporting listener
            final List<Attribute> toRemove = new LinkedList<Attribute>(
                    model.attributeList(ProvenanceRecorder.class));
            toRemove.addAll(model.attributeList(ReportingListener.class));
            for(Attribute attribute : toRemove) {
                try {
                    attribute.setContainer(null);
                } catch (Exception e) {
                    throw new RuntimeException("Error removing " + attribute.getName() +
                            ": " + e.getMessage());
                }
            }

        }
        
        return model;
    }
    /** Remove top-level ports and relations from a composite actor. */
    public static void removeModelPorts(CompositeActor model) {
               
        // get a copy of the list since we modify the original when ports
        // are removed.
        final List<?> ports = new LinkedList<Object>(model.portList());
        for(Object object : ports) {
            final TypedIOPort port = (TypedIOPort)object;
            //final String name = port.getName();

            //LOG.info("going to replace port " + name + " in model.");

            // NOTE: do not remove ports connected to the top-level ports, since
            // their types are used to the the input/output types of the stub.
            /*
            final List<?> connectedPorts = new LinkedList<Object>(port.insidePortList());
            for(Object obj : connectedPorts) {
                try {
                    ((IOPort)obj).setContainer(null);
                } catch (Exception e) {
                    throw new RuntimeException("Error removing port " +
                        ((IOPort)obj).getFullName() + " from model: " +
                        e.getMessage());
                }
            }
            */

            /*
            // print types of connected ports
            final List<?> connectedPorts = new LinkedList<Object>(port.insidePortList());
            for(Object obj : connectedPorts) {
                LOG.info("type for " + obj + " is " + ((TypedIOPort)obj).getType());
            } 
            */                   
            
            // remove relations
            final List<?> connectedRelations = new LinkedList<Object>(port.insideRelationList());            
            for(Object obj : connectedRelations) {
                try {
                    ((IORelation)obj).setContainer(null);
                } catch (Exception e) {
                    throw new RuntimeException("Error removing relation " +
                        "from model: " + e.getMessage());
                }
            }            
            
            // remove the top-level port
            try {
                port.setContainer(null);
            } catch (Exception e) {
                throw new RuntimeException("Error removing port " +
                    port.getFullName() + " from model: " + e.getMessage());
            }
        }
        
    }
     
    /** Create a Token from a string based on the Ptolemy Type. */
    public static Token createTokenFromString(String tokenStr, Type type) {
        try {
            if(type == BaseType.STRING) {
                return new StringToken(tokenStr);
            } else if(type == BaseType.INT) {
                return new IntToken(tokenStr);
            } else if(type == BaseType.DOUBLE) {
                return new DoubleToken(tokenStr);
            } else if(type == BaseType.LONG) {
                return new LongToken(tokenStr);
            } else if(type == BaseType.NIL) {
                return Token.NIL;
            } else if(type instanceof RecordType) {
                return new RecordToken(tokenStr);
            } else {
                throw new RuntimeException("Unknown type of token: " + type);
            }
        } catch(IllegalActionException e) {
            throw new RuntimeException("Error creating token.", e);
        }
    }
    
    /** Create a Token from a string based on the TokenType enum. */
    public static Token createTokenFromString(String tokenStr, TokenType type) {
            
        try {
            switch(type) {
                case String:
                    return new StringToken(tokenStr);
                case Int:
                    return new IntToken(tokenStr);
                case Double:
                    return new DoubleToken(tokenStr);
                case Long:
                    return new LongToken(tokenStr);
                case Nil:
                    return Token.NIL;
                case Record:
                    return new RecordToken(tokenStr);
                default:
                    throw new RuntimeException("Unknown type of token: " + type);
            }
        } catch(IllegalActionException e) {
            throw new RuntimeException("Error creating token.", e);
        }
    }
    
    /** Get the TokenType for a token. */
    public static TokenType getTokenTypeForSerialization(Token token) {
        
        Type type = token.getType();
        
        if(type == BaseType.STRING) {
            return TokenType.String;
        } else if(type == BaseType.INT) {
            return TokenType.Int;
        } else if(type == BaseType.DOUBLE) {
            return TokenType.Double;
        } else if(type == BaseType.LONG) {
            return TokenType.Long;
        } else if(type == BaseType.NIL) {
            return TokenType.Nil;
        } else if(type instanceof RecordType) {
            return TokenType.Record;
        } else {
            throw new RuntimeException("Serialization of Ptolemy type " +
                type.toString() + " is unsupported.");
        }

    }
    
    /** Get the ConfigurationProperty for an Engine on the configuration file.
     *  @param engineName The name of the ddp engine
     *  @param source The source object calling this method
     *  @return If found, returns the ConfigurationProperty. If not found,
     *  throws an exception.
     */
    public static ConfigurationProperty getEngineProperty(String engineName,
        Nameable source) throws IllegalActionException {
        
        List<ConfigurationProperty> engineProperties = ConfigurationManager
                .getInstance()
                .getProperty(ConfigurationManager.getModule("ddp-common"))
                .getProperties("Engines.Engine");   
        
        if(engineProperties == null || engineProperties.isEmpty()) {
            throw new IllegalActionException(source, "No DDP engines found.");
        }
        
        for(ConfigurationProperty engineProperty : engineProperties) {
            ConfigurationProperty nameProperty = engineProperty.getProperty("Name");
            if(nameProperty == null) {
                throw new IllegalActionException(source, "DDP Engine does not have a name. Try deleting\n" +
                        "$HOME/KeplerData/modules/ddp-common/configuration/configuration.xml\n" +
                        "and restarting Kepler.");
            }
            
            if (nameProperty.getValue().equalsIgnoreCase(engineName)) {
                return engineProperty;
            }
        }

        // not found
        throw new IllegalActionException(source,
            "No " + engineName + " Engine on configuration file.");
    }
    
    /** Get a list of available execution language names. */
    public static String[] getExecutionLanguageNames() throws IllegalActionException {
        
        List<String> names = new ArrayList<String>();
        
        final List<ConfigurationProperty> languageList = ConfigurationManager
                .getInstance()
                .getProperty(ConfigurationManager.getModule("ddp-common"))
                .getProperties("ExecutionLanguages.Language");
        
        for(ConfigurationProperty property : languageList) {
            ConfigurationProperty nameProperty = property.getProperty("Name");
            if(nameProperty == null) {
                throw new IllegalActionException(
                        "Missing name for execution language in configuration file.");
            }
            names.add(nameProperty.getValue());
        }
        
        String[] array = names.toArray(new String[names.size()]);
        Arrays.sort(array);
        return array;
    }
    
    /** Get the jars for a language. Returns an empty list if there are non jars. */
    public static List<String> getJarsForLanguage(String languageName) throws IllegalActionException {
                
        final List<ConfigurationProperty> languageList = ConfigurationManager
                .getInstance()
                .getProperty(ConfigurationManager.getModule("ddp-common"))
                .getProperties("ExecutionLanguages.Language");
        
        for(ConfigurationProperty property : languageList) {
            ConfigurationProperty nameProperty = property.getProperty("Name");
            if(nameProperty == null) {
                throw new IllegalActionException(
                        "Missing name for execution language in configuration file.");
            }
            if(nameProperty.getValue().equals(languageName)) {
                ConfigurationProperty jarsProperty = property.getProperty("Jars");
                if(jarsProperty != null) {
                    String jarsStr = jarsProperty.getValue();
                    if(jarsStr != null) {
                        String[] jars = jarsStr.split(",");
                        return Arrays.asList(jars);
                    }
                }
            }
        }
        
        return new LinkedList<String>();
        
    }
    
    /** Get the script engine name for a language. Returns null if the language
     *  is not found in the configuration, or is not supported by a script engine.
     */
    public static String getScriptEngineName(String languageName) throws IllegalActionException {
        
        final List<ConfigurationProperty> languageList = ConfigurationManager
                .getInstance()
                .getProperty(ConfigurationManager.getModule("ddp-common"))
                .getProperties("ExecutionLanguages.Language");
        
        for(ConfigurationProperty property : languageList) {
            ConfigurationProperty nameProperty = property.getProperty("Name");
            if(nameProperty == null) {
                throw new IllegalActionException(
                        "Missing name for execution language in configuration file.");
            }
            if(nameProperty.getValue().equals(languageName)) {
                ConfigurationProperty typeProperty = property.getProperty("Type");
                if(typeProperty == null) {
                    throw new IllegalActionException(
                            "Missing type for execution language " +
                                    languageName + " in configuration file.");
                }
                if(typeProperty.getValue().equals("ScriptEngine")) {
                    ConfigurationProperty scriptEngineNameProperty = property.getProperty("ScriptEngineName");
                    if(scriptEngineNameProperty == null) {
                        throw new IllegalActionException(
                                "Missing script engine name for execution language " +
                                        languageName + " in configuration file.");
                    }
                    return scriptEngineNameProperty.getValue();
                }
            }
        }
        // not found
        return null;
    }
    
    /** Get the script engine factory name for a language. Returns null if the language
     *  is not found in the configuration, or is not supported by a script engine.
     */
    public static String getScriptEngineFactoryName(String languageName) throws IllegalActionException {
        
        final List<ConfigurationProperty> languageList = ConfigurationManager
                .getInstance()
                .getProperty(ConfigurationManager.getModule("ddp-common"))
                .getProperties("ExecutionLanguages.Language");
        
        for(ConfigurationProperty property : languageList) {
            ConfigurationProperty nameProperty = property.getProperty("Name");
            if(nameProperty == null) {
                throw new IllegalActionException(
                        "Missing name for execution language in configuration file.");
            }
            if(nameProperty.getValue().equals(languageName)) {
                ConfigurationProperty typeProperty = property.getProperty("Type");
                if(typeProperty == null) {
                    throw new IllegalActionException(
                            "Missing type for execution language " +
                                    languageName + " in configuration file.");
                }
                if(typeProperty.getValue().equals("ScriptEngine")) {
                    ConfigurationProperty scriptEngineFactoryNameProperty =
                            property.getProperty("ScriptEngineFactoryName");
                    if(scriptEngineFactoryNameProperty == null) {
                        throw new IllegalActionException(
                                "Missing script engine factory name for execution language " +
                                        languageName + " in configuration file.");
                    }
                    return scriptEngineFactoryNameProperty.getValue();
                }
            }
        }
        // not found
        return null;
    }
    
    /** Returns true if an environment variable is set when
     *  executing under a resource manager such as PBS or SLURM.
     */
    public static boolean isExecutingUnderResourceManager() {
        // check PBS
        return System.getenv("PBS_JOBID") != null ||
            // check SGE
            System.getenv("PE_NODEFILE") != null ||
            // check SLURM
            System.getenv("SLURM_JOBID") != null;
    }

    public static final String CONFIGURATION_KEPLER_PARA_PREFIX = "KEPLER::parameter";
    
    public static final String CONFIGURATION_KEPLER_PRINT_EXE_INFO = CONFIGURATION_KEPLER_PARA_PREFIX + "::printExeSummary";
    
    public static final String CONFIGURATION_KEPLER_TAG = "KEPLER::tagging";
    
    public static final String CONFIGURATION_KEPLER_PARA_PARALLEL = "KEPLER::parameter::ParallelNumber";
    
    public static final String CONFIGURATION_KEPLER_DISTRIBUTED_CACHE_PATH = "KEPLER::distributedcache::path";
    
    public static final String CONFIGURATION_KEPLER_JOB_KEY_CLASS = "KEPLER::job::key::class";
    
    public static final String CONFIGURATION_KEPLER_JOB_VALUE_CLASS = "KEPLER::job::value::class";
    
    /** The name of the configuration parameter for the MoML of the sub-workflow. */
    public static final String CONFIGURATION_KEPLER_MODEL = "KEPLER::model";
    
    /** The name of the configuration parameter for the name of the sub-workflow. */
    public static final String CONFIGURATION_KEPLER_MODEL_NAME = "KEPLER::modelName";
        
    /** The name of the configuration parameter for the path of the sub-worflow MoML. */
    public static final String CONFIGURATION_KEPLER_MODEL_PATH = "KEPLER::modelPath";

    /** The name of the configuration parameter for the path of the Kepler installation. */
    public static final String CONFIGURATION_KEPLER_MODULES_DIR = "KEPLER::modulesDir";
    
    /** The name of the configuration parameter for the path to redirect display actors. */
    public static final String CONFIGURATION_KEPLER_REDIRECT_DIR = "KEPLER::redirectDir";
    
    /** The name of the configuration parameter to specify running in the same JVM as Kepler. */
    public static final String CONFIGURATION_KEPLER_SAME_JVM = "KEPLER::sameJVM";
    
    /** The name of the configuration parameter specifying the name of a DDPDataSource actor. */
    public static final String CONFIGURATION_KEPLER_SOURCE_ACTOR_NAME = "KEPLER::sourceActorName";

    /** The name of the configuration parameter specifying the name of a DDPDataSink actor. */
    public static final String CONFIGURATION_KEPLER_SINK_ACTOR_NAME = "KEPLER::sinkActorName";
    
    /** The name of the configuration parameter specifying the name of the input token. */
    public static final String CONFIGURATION_KEPLER_INPUT_TOKEN = "Kepler::inputToken";
    
    /** The name of the configuration parameter specifying if the full lifecycle of the
     *  sub-workflow should be executed for each input.
     */
    public static final String CONFIGURATION_KEPLER_RUN_WORKFLOW_LIFECYCLE_PER_INPUT = "Kepler::runWorkflowLifeCyclePerInput";
  
    /** The name of the configuration parameter specifying the code to execute in the stub. */
    public static final String CONFIGURATION_KEPLER_STUB_CODE = "Kepler::code";

    /** The name of the configuration parameter specifying the input key type of the stub. */
    public static final String CONFIGURATION_KEPLER_INPUT_KEY_TYPE = "Kepler::inputKeyType";

    /** The name of the configuration parameter specifying the input value type of the stub. */
    public static final String CONFIGURATION_KEPLER_INPUT_VALUE_TYPE = "Kepler::inputValueType";

    /** The name of the configuration parameter specifying the name of the script engine factory. */
    public static final String CONFIGURATION_KEPLER_SCRIPT_ENGINE_FACTORY_NAME = "Kepler::scriptEngineFactoryName";   
    
    /** Enumeration of types of token that can be serialized to/from strings. */
    public enum TokenType {
        String,
        Int,
        Double,
        Long,
        Nil,
        Record;
        
        public static TokenType getInstance(int value) {
            // FIXME add error checking
            return values()[value];
        }
        
        public int getValue() {
            return ordinal();
        }
    }

    ///////////////////////////////////////////////////////////////////
    ////                         private fields                    ////

    /** Logging. */
    private static final Log LOG = LogFactory.getLog(Utilities.class);

}
