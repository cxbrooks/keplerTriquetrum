/* An atomic actor for DDP patterns.
 * 
 * Copyright (c) 2011-2012 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2015-09-08 16:43:03 -0700 (Tue, 08 Sep 2015) $' 
 * '$Revision: 33875 $'
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.kepler.build.modules.Module;
import org.kepler.configuration.ConfigurationManager;
import org.kepler.configuration.ConfigurationProperty;
import org.kepler.configuration.ConfigurationUtilities;
import org.kepler.ddp.director.DDPDirector;

import ptolemy.actor.Director;
import ptolemy.actor.TypedAtomicActor;
import ptolemy.data.IntToken;
import ptolemy.data.StringToken;
import ptolemy.data.Token;
import ptolemy.data.expr.Parameter;
import ptolemy.data.expr.StringParameter;
import ptolemy.data.type.Type;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.Attribute;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import ptolemy.kernel.util.Settable;
import ptolemy.kernel.util.Workspace;
import ptolemy.util.MessageHandler;

/**
 * An atomic actor for DDP patterns.
 * 
 *  @author Daniel Crawl
 *  @version $Id: AtomicPatternActor.java 33875 2015-09-08 23:43:03Z crawl $
 */
public class AtomicPatternActor extends TypedAtomicActor implements DDPPatternActor {

    /** Construct a new AtomicPatternActor in a container with a given name. */
    public AtomicPatternActor(CompositeEntity container, String name)
            throws IllegalActionException, NameDuplicationException {
        super(container, name);

        degreeOfParallelism = new Parameter(this, "degreeOfParallelism");
        degreeOfParallelism.setExpression("0");

        formatType = new StringParameter(this, "formatType");
        
        keyValueTypes = new StringParameter(this, "keyValueTypes");
        for(String type : _commonKeyValueTypes) {
           keyValueTypes.addChoice(type);
        }
        
        jars = new StringParameter(this, "jars");
        
        /* This is not supported for the DDP I/O actors.
        printExeInfo = new Parameter(this,
                "printExeSummary", BooleanToken.FALSE);
        printExeInfo.setTypeEquals(BaseType.BOOLEAN);
        */
    }

    /** See if the formatType parameter changed. */
    @Override
    public void attributeChanged(Attribute attribute) throws IllegalActionException {

        if (attribute == formatType) {
            String newFormatStr = formatType.stringValue();
            _setFormat(newFormatStr);
        } else {
            super.attributeChanged(attribute);
        }
    }

    /** Clone this actor into the specified workspace. */
    @Override
    public Object clone(Workspace workspace) throws CloneNotSupportedException {
    	AtomicPatternActor newObject = (AtomicPatternActor) super.clone(workspace);
    	newObject._keyType = null;
    	newObject._valueType = null;
    	newObject._formatTypeStr = _formatTypeStr;
    	newObject._formatProperty = null;
    	newObject._formatProperties = null;
    	newObject._FORMAT_TYPE_CATEGORY = _FORMAT_TYPE_CATEGORY;
    	return newObject;
    }
    
    /** Get the number of parallel instances to execute. */
    @Override
    public int getDegreeOfParallelism() throws IllegalActionException {
        return ((IntToken)degreeOfParallelism.getToken()).intValue();
    }
    
	@Override
	public String getDisplayRedirectDir() throws IllegalActionException {
		return "";
	}

    /** Get the name of the execution class. In this class, always
     *  returns the empty string.
     */
    @Override
    public String getExecutionClassName() {
        return "";
    }

    /** Get the execution code type. Returns null if not set. */
    @Override
    public String getExecutionCodeType() throws IllegalActionException {
        return null;
    }
    
    /** Get the execution code. Returns null if not set. */
    @Override
    public String getExecutionCode() throws IllegalActionException {
        return null;
    }

    /** Get the name of the class that provides the format type implementation
     *  for a specific engine.
     */
    public String getFormatClassName(String engineName) throws IllegalActionException {

        String className = null;

        final ConfigurationProperty formatProperty = _getFormatProperty(engineName);
        if (formatProperty != null) {

            // we found the format with the same name as _formatTypeStr,
            // and has an implementation class for the same engine
            
            // now get the class name.
            className = 
                    formatProperty.getProperty("ImplementationClass").getProperty(engineName).getValue();
            
            // make sure class is specified for the engine
            if (className == null) {
                throw new IllegalActionException(this, "Format "
                        + _formatTypeStr
                        + " does not have an implementation class for "
                        + engineName);
            }
        }

        // if we didn't find the format in the config file, the format type
        // could be a class name.
        if (className == null) {
            className = _formatTypeStr;
        }

        return className;
    }

    /** Get a comma-separated list of jars to use with this actor. */
    @Override
    public String getJars() throws IllegalActionException {
        return jars.stringValue();
    }
    
    /** Get whether print execution summary when running workflow
     *  inside of Hadoop/Stratosphere job. In this class, returns
     *  false since printing execution information is not supported
     *  in the DDP I/O actors.
     */
    @Override
    public boolean getPrintExeInfo() throws IllegalActionException {
        return false;
        /*
    	return ((BooleanToken) printExeInfo
                .getToken()).booleanValue();
        */
    }

    /** Get a set of name-value pairs of input/output format parameters for the execution engine. */
    @Override
    public java.util.Map<String, String> getParameters() throws IllegalActionException {

        final java.util.Map<String, String> retval = new HashMap<String, String>();

        // find all the parameters containing an attribute named
        // _associatedWithFormatType
        for (Parameter parameter : attributeList(Parameter.class)) {
            if (parameter.getAttribute(FORMAT_TYPE_PARAMETER_NAME) != null) {
                // add the name and value to the return map.
                final Token token = parameter.getToken();
                String value;
                if (token instanceof StringToken) {
                    value = ((StringToken)token).stringValue();
                } else {
                    value = token.toString();
                }
                retval.put(parameter.getName(), value);
            }
        }

        return retval;
    }
    
    /** Get a set of (kepler name, implementation name) pairs of input/output format parameters for the execution engine. */
    @Override
    public java.util.Map<String, String> getParaImplNames(String engineName) throws IllegalActionException {  
    	
        final java.util.Map<String, String> retval = new HashMap<String, String>();
        List<ConfigurationProperty> parameterPairs = null;
        if (_formatProperty != null)
        	parameterPairs = _formatProperty.getProperties("Parameters.pair");
        if (parameterPairs != null) {
        	ConfigurationProperty nameInEngine;
	        for (ConfigurationProperty parameterPair: parameterPairs) {
	        	nameInEngine = parameterPair.getProperty(engineName + "Name");
	        	retval.put(parameterPair.getProperty("name").getValue(), nameInEngine == null ? null : nameInEngine.getValue());
	        }
        }
        return retval;
    }

    /** Update the port types based on the format type. */
    @Override
    public void preinitialize() throws IllegalActionException {

        super.preinitialize();

        Director director = getDirector();
        if (!(director instanceof DDPDirector)) {
            throw new IllegalActionException(this,
                "This actor only executes with the DDP director.");
        }

        // set the types based on the format type
        _formatProperty = _getFormatProperty(((DDPDirector) director)
                .getEngineName());
        //formatProperty.prettyPrint();
        _setTypesFromConfiguration(_formatProperty);

    }
    
    ///////////////////////////////////////////////////////////////////
    ////                         public fields                     ////

    /** The number of parallel instances to execute. */
    public Parameter degreeOfParallelism;

    /** The name of the data format or a fully-qualified class name to
     *  parse the data.
     */
    public StringParameter formatType;

    /** A comma-separated list of jars to use with this actor. The 
     *  full path to each jar must be specified.
     */
    public StringParameter jars;  
    
    /** A boolean parameter for whether print execution summary when
     *  running as Hadoop/Stratosphere/etc jobs.
     *  NOTE: this is not support for DDP I/O actors.
     */
    //public Parameter printExeInfo;
    
    /** The key value types of the atomic patter actor. This parameter should only be set
     *  when a class name is specified for formatType parameter.
     */
    public StringParameter keyValueTypes;

    ///////////////////////////////////////////////////////////////////
    ////                         protected methods                 ////

    /** Add the formats in the configuration file as choices to formatType. */
    protected void _addFormats(String inputOutputName) {
        final ConfigurationManager manager = ConfigurationManager.getInstance();
        final Module module = ConfigurationManager.getModule("ddp-common");
        final ConfigurationProperty propertyRoot = manager.getProperty(module);
        _formatProperties = propertyRoot.getProperties(inputOutputName
                + "Formats.Format");

        // use a set to store the names to remove duplicates
        Set<String> names = new HashSet<String>();
        for (ConfigurationProperty formatProperty : _formatProperties) {
            ConfigurationProperty nameProperty = formatProperty.getProperty("Name");
            if(nameProperty == null) {
                MessageHandler.error("Configuration format does not have a name. Try deleting\n" +
                        "$HOME/KeplerData/modules/ddp-common/configuration/configuration.xml\n" +
                        "and restarting Kepler.");
                return;
            } else {
                names.add(nameProperty.getValue());
            }
        }

        // sort the names alphabetically
        String[] namesArray = names.toArray(new String[names.size()]);
        Arrays.sort(namesArray);
        for (String name : namesArray) {
            formatType.addChoice(name);
        }
    }
    
    /** Set the format, and add any associated parameters. This method also sets
     *  the port types for the keys and values specified in the configuration.
     * 
     * @param newFormatTypeStr the format name or a class name.
     */
    protected void _setFormat(String newFormatTypeStr) throws IllegalActionException {

        // see if the type has changed
        if (_formatTypeStr == null || !newFormatTypeStr.equals(_formatTypeStr)) {

            _formatTypeStr = newFormatTypeStr;

            java.util.Map<String, String> associatedParameterPairs = null;

            final ConfigurationProperty formatProperty = _getFormatProperty(null);

            if (formatProperty != null) {

                //formatProperty.prettyPrint();
                
                // see if it has associated parameters
                final ConfigurationProperty parametersProperty = 
                        formatProperty.getProperty("Parameters");
                if (parametersProperty != null) {
                    // get a map of names and values
                    associatedParameterPairs = ConfigurationUtilities
                            .getPairsMap(parametersProperty);
                }

                _setTypesFromConfiguration(formatProperty);
            }

            // see if we could not find the type in the configuration file
            // the type may be a class name, so try to instantiate it.
            if (formatProperty == null) {

                // first check for classes that were in 0.1 WordCount
                if (newFormatTypeStr.equals("eu.stratosphere.pact.example.wordcount.WordCount$LineInFormat")) {
                    System.out.println("class WordCount$LineInFormat no longer exists; using TextInputFormat");
                    newFormatTypeStr = "eu.stratosphere.pact.common.io.TextInputFormat";
                } else if (newFormatTypeStr.equals("eu.stratosphere.pact.example.wordcount.WordCount$WordCountOutFormat")) {
                    System.out.println("class WordCount$WordCountOutFormat no longer exists; using RecordOutputFormat");
                    newFormatTypeStr = "eu.stratosphere.pact.common.io.RecordOutputFormat";
                } else {
                    // try to load the class
                    try {
                        /*final Class<?> clazz =*/ Class.forName(newFormatTypeStr);
                    } catch (ClassNotFoundException e) {
                    	//the exception might be that the class is from outside jar, do not throw error here.
//                        throw new IllegalActionException(
//                                this,
//                                "Format type "
//                                        + newFormatTypeStr
//                                        + " was not found in the configuration file and class "
//                                        + newFormatTypeStr
//                                        + " was not found on the classpath.");
                    }
                }
            }

            // remove parameters associated with the old format type that
            // have different names than those associated with the new type
            for (Parameter parameter : attributeList(Parameter.class)) {
                if (parameter.getAttribute(FORMAT_TYPE_PARAMETER_NAME) != null
                        && (associatedParameterPairs == null || !associatedParameterPairs
                                .containsKey(parameter.getName()))) {
                    try {
                        // System.out.println("removing " +
                        // parameter.getName());
                        parameter.setContainer(null);
                    } catch (NameDuplicationException e) {
                        throw new IllegalActionException(this, e,
                                "Error removing parameter "
                                        + parameter.getName());
                    }
                }
            }

            // add any associated parameters
            if (associatedParameterPairs != null) {
                for (java.util.Map.Entry<String, String> entry : associatedParameterPairs.entrySet()) {
                    final String name = entry.getKey();

                    // see if the parameter is already there
                    if (getAttribute(name) == null) {
                        Parameter associatedParameter;
                        try {
                            // System.out.println("adding " + name);
                            // create a parameter for the name and value
                            associatedParameter = new Parameter(this, name);

                            // add a parameter inside the new parameter so we
                            // can remove it when
                            // the format type changes
                            final Parameter hiddenParameter = new Parameter(
                                    associatedParameter,
                                    FORMAT_TYPE_PARAMETER_NAME);
                            hiddenParameter.setVisibility(Settable.NONE);
                        } catch (NameDuplicationException e) {
                            throw new IllegalActionException(this, e,
                                    "Error adding parameter " + name);
                        }
                        associatedParameter.setExpression(entry.getValue());
                    }
                }
            }
        }

    }

    /** Set the key and value types from the types in the configuration property. */
    protected void _setTypesFromConfiguration(ConfigurationProperty formatProperty)
            throws IllegalActionException {

        // see if it has the Types properties
        final ConfigurationProperty typesProperty = formatProperty
                .getProperty("Types");

        // make sure the Types property was found
        if (typesProperty == null) {
            throw new IllegalActionException(
                    "No Types specified for format type " + _formatTypeStr);
        }

        // make sure the Key property under Types was found
        final ConfigurationProperty keyProperty = typesProperty
                .getProperty("Key");
        if (keyProperty == null) {
            throw new IllegalActionException(
                    "No Key type specified for format type " + _formatTypeStr);
        }
        // read the key type
        String typeStr = keyProperty.getValue();

        final Type keyType = Types.getTypeFromString(typeStr);
        if (keyType == null) {
            throw new IllegalActionException(this,
                    "Could not find Ptolemy type for key type " + typeStr
                            + " for format " + _formatTypeStr);
        }
        _keyType = keyType;
        //System.out.println(getName() + " change key type to " + _keyType);

        // make sure the Value property under Types was found
        final ConfigurationProperty valueProperty = typesProperty
                .getProperty("Value");
        if (valueProperty == null) {
            throw new IllegalActionException(
                    "No Value type specified for format type " + _formatTypeStr);
        }
        // read the value type
        typeStr = valueProperty.getValue();
        
        final Type valueType = Types.getTypeFromString(typeStr);
        
        if (valueType == null) {
            throw new IllegalActionException(this,
                    "Could not find Ptolemy type for value type " + typeStr
                            + " for format " + _formatTypeStr);
        }
        _valueType = valueType;
        //System.out.println(getName() + " change value type to " + _valueType);

        // update the port type
        _updateKeyValueTypes();
    }

    /** Update the key and value types. In this class, do nothing. */
    protected void _updateKeyValueTypes() {

    }
    
    ///////////////////////////////////////////////////////////////////
    ////                         protected fields                  ////

    /** The key type. */
    protected Type _keyType;

    /** The value type. */
    protected Type _valueType;

    /** The string value of the format type. */
    protected String _formatTypeStr;
    
    /** A list of properties of format types from the config file. */
    protected List<ConfigurationProperty> _formatProperties;

    /** The configuration property name for the format type. */
    protected String _FORMAT_TYPE_CATEGORY;
    
    ///////////////////////////////////////////////////////////////////
    ////                         private methods                   ////

    /** Get the configuration property with the same name as the format type.
     * 
     * @param engineName If not null, returns the property whose implementation
     * class is for the specified engine. Otherwise, returns the first property
     * with the same name as the format type.
     */
    private ConfigurationProperty _getFormatProperty(String engineName)
            throws IllegalActionException {

        // make sure it's not empty.
        if (_formatTypeStr.trim().isEmpty()) {
            throw new IllegalActionException(this, "Missing format name.");
        }

        // try to find the format name in the config properties

        final List<ConfigurationProperty> formatList = ConfigurationManager
                .getInstance()
                .getProperty(ConfigurationManager.getModule("ddp-common"))
                .getProperties(_FORMAT_TYPE_CATEGORY);

        if (formatList == null || formatList.isEmpty()) {
            throw new IllegalActionException(this,
                    "No formats found in configuration.xml for type "
                            + _FORMAT_TYPE_CATEGORY);
        }

        for (ConfigurationProperty format : formatList) {
            ConfigurationProperty nameProperty = format.getProperty("Name");
            if(nameProperty == null) {
                throw new IllegalActionException(this,
                        "Configuration format does not have a name. Try deleting\n" +
                        "$HOME/KeplerData/modules/ddp-common/configuration/configuration.xml\n" +
                        "and restarting Kepler.");
            } 
            
            if (nameProperty.getValue().equalsIgnoreCase(_formatTypeStr) &&
                    ((engineName != null &&
                    format.getProperty("ImplementationClass").getProperty(engineName) != null) ||
                    (engineName == null))) {
                return format;
            }
        }
        
        return null;
    }

    ///////////////////////////////////////////////////////////////////
    ////                         private fields                    ////

    /** Name of attribute contained in each parameter associated with the format
     * type.
     */
    private final static String FORMAT_TYPE_PARAMETER_NAME = "_associatedWithFormatType";
    
    /** The configuration property for the format type. */
    private ConfigurationProperty _formatProperty;

}
