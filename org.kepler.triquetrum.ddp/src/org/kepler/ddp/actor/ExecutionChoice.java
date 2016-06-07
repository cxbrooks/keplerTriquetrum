/* An actor with several sub-workflow choices for execution. 
 * 
 * Copyright (c) 2012 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2015-08-24 11:05:00 -0700 (Mon, 24 Aug 2015) $' 
 * '$Revision: 33621 $'
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
package org.kepler.ddp.actor;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.kepler.build.modules.Module;
import org.kepler.build.modules.ModuleTree;
//import org.kepler.ddp.gui.ExecutionChoiceEditorFactory;
import org.kepler.ddp.gui.ExecutionChoiceEditorPane;
import org.kepler.provenance.ProvenanceRecorder;
import org.kepler.reporting.ReportingListener;

import ptolemy.actor.IOPort;
import ptolemy.actor.IOPortEvent;
import ptolemy.actor.IOPortEventListener;
import ptolemy.actor.TypedIOPort;
import ptolemy.actor.gui.style.EditableChoiceStyle;
import ptolemy.actor.lib.hoc.Case;
import ptolemy.actor.lib.hoc.MultiCompositeActor;
import ptolemy.actor.lib.hoc.MultiCompositePort;
import ptolemy.actor.lib.hoc.Refinement;
import ptolemy.data.BooleanToken;
import ptolemy.data.StringToken;
import ptolemy.data.Token;
import ptolemy.data.expr.Parameter;
import ptolemy.data.expr.StringParameter;
import ptolemy.data.type.BaseType;
import ptolemy.gui.ExtensionFilenameFilter;
import ptolemy.kernel.ComponentEntity;
import ptolemy.kernel.ComponentPort;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.Port;
import ptolemy.kernel.Relation;
import ptolemy.kernel.util.Attribute;
import ptolemy.kernel.util.ChangeListener;
import ptolemy.kernel.util.ChangeRequest;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import ptolemy.kernel.util.NamedObj;
import ptolemy.kernel.util.Settable;
import ptolemy.kernel.util.SingletonAttribute;
import ptolemy.kernel.util.Workspace;
import ptolemy.moml.MoMLChangeRequest;
import ptolemy.moml.MoMLParser;
import ptolemy.util.CancelException;
import ptolemy.util.MessageHandler;
import ptolemy.vergil.basic.KeplerDocumentationAttribute;

/** An actor that supports multiple choices for execution. Each choice 
 *  is a sub-workflow that can either be loaded from a set of templates,
 *  or created by the user.
 *  
 *  Each file input port has an associated parameter. When an input token is read,
 *  the associated parameter's value is set to the be token.
 *  
 *  Each file output also has an associated parameter. When the execution choice
 *  finishes, the value of the parameter is written to the output port.
 * 
 *  @author Daniel Crawl
 *  @version $Id: ExecutionChoice.java 33621 2015-08-24 18:05:00Z crawl $
 *  
 */
public class ExecutionChoice extends Case implements ChangeListener, IOPortEventListener {

    /** Create a new ExecutionChoice in a container with the
     *  specified name.
     */
    public ExecutionChoice(CompositeEntity container, String name)
            throws IllegalActionException, NameDuplicationException {
        super(container, name);
       _init();
    }

    /** Create a new ExecutionChoice in a workspace. */
    public ExecutionChoice(Workspace workspace) throws IllegalActionException,
            NameDuplicationException {
        super(workspace);
        _init();
    }
        
    /** React to a change in an attribute. */
    @Override
    public void attributeChanged(Attribute attribute) throws IllegalActionException {
        
        if(attribute == checkOutputTimestamp) {
            
            _checkOutputTimestampVal = ((BooleanToken)checkOutputTimestamp.getToken()).booleanValue();
            
        } else if (attribute == control) {
            String newValue = ((StringToken)control.getToken()).stringValue();
            
            // see if we've added the default choice
            if(_default != null && !newValue.equals(DEFAULT_TEMPLATE_NAME)) {   

	            // verify that the refinement exists
	            boolean found = false;
	            List<Refinement> refinements = entityList(Refinement.class);
	            for(Refinement refinement : refinements) {
	                if(refinement.getName().equals(newValue)) {
	                    found = true;
	                    break;
	                }
	            }
	            if(!found) {
	                throw new IllegalActionException(this, "Execution Choice '" + newValue + "' not found.");
	            }
            }
        } else {
            super.attributeChanged(attribute);
        }
    }
    
    /** React to the fact that the change has been successfully executed
     *  by doing nothing.
     *  @param change The change that has been executed.
     */
    @Override
    public void changeExecuted(ChangeRequest change) {
        // Nothing to do.
    }

    /** React to the fact that the change has failed by reporting it.
     *  @param change The change that was attempted.
     *  @param exception The exception that resulted.
     */
    @Override
    public void changeFailed(ChangeRequest change, Exception exception) {
        // Ignore if this is not the originator.
        if ((change != null) && (change.getSource() != this)) {
            return;
        }

        if ((change != null) && !change.isErrorReported()) {
            change.setErrorReported(true);
            MessageHandler.error("Rename failed: ", exception);
            _changeRequestError = true;
        }
    }

    /** Override the base class to ensure that the member fields
     *  are initialized.
     *  @param workspace The workspace for the new object.
     *  @return A new ExecutionChoice.
     *  @exception CloneNotSupportedException If any of the attributes
     *   cannot be cloned.
     */
    @Override
    public Object clone(Workspace workspace) throws CloneNotSupportedException {
        ExecutionChoice newObject = (ExecutionChoice) super.clone(workspace);
        newObject._changeRequestError = false;
        newObject._checkOutputTimestampVal = true;
        newObject._choiceStyle = null;
        newObject._commandLineArguments = "$additionalOptions";
        newObject._refinementCommandLines = new HashMap<Refinement, String>();
        newObject._templateDir = null;
        return newObject;
    }
    
    /** Export an execution choice to a file.
     *  @param saveFile the file to write the execution choice as a MoML XML. 
     *  @param name the name of the execution choice to save.
     */
	public void exportExecutionChoice(File saveFile, String name) 
			throws IllegalActionException, NameDuplicationException {

		final ComponentEntity<?> refinement = getEntity(name);
		if(refinement == null) {
			throw new IllegalActionException(this, "Execution choice " +
					name + " does not exist.");
		}
		
		// create the container to be exported.
		// it is a MultiCompositeActor because MultiCompositePorts cannot
		// be contained by other actors.
		final MultiCompositeActor toExport = new MultiCompositeActor(_workspace);
		toExport.setName(name);
		
		try {
			
			// copy ports to the workflow
			for(Port port : (List<Port>)portList()) {
				if(port != control.getPort()) {
					Port clonePort = (Port) port.clone(_workspace);
					clonePort.setContainer(toExport);
					
					// clone the File parameters
					if(getPortIOType(port) == IOType.File) {
						Parameter parameter = (Parameter) getAttribute(port.getName());
						Parameter cloneParameter = (Parameter) parameter.clone(_workspace);
						cloneParameter.setContainer(toExport);
					}
				}
			}
			
			// copy parameters to the workflow
			for(String parameterName : getParameterNames()) {
				Parameter parameter = (Parameter) getAttribute(parameterName);
				Parameter cloneParameter = (Parameter) parameter.clone(_workspace);
				cloneParameter.setContainer(toExport);
			}
			
			// copy the refinement to the workflow
			Refinement cloneRefinement = (Refinement) refinement.clone(_workspace);
			cloneRefinement.setContainer(toExport);
		} catch(CloneNotSupportedException e) {
			throw new IllegalActionException(this, e, "Could not copy execution choice.");
		}
		
		// finally write out the workflow
        FileWriter writer = null;
        try {
            try {
                writer = new FileWriter(saveFile);
                toExport.exportMoML(writer);
            } finally {
                if(writer != null) {
                    writer.close();
                }
            }
        } catch(IOException e) {
            throw new IllegalActionException(this, e, "Error exporting to " + saveFile);
        }
    	
        // delete the exported workflow from memory
        toExport.setContainer(null);
	}

    @Override
    public void fire() throws IllegalActionException {
        
        // if we're checking the last modified timestamps of output
        // files, save the current last modified timestamps before we
        // execute the template.
        final Map<String,Long> lastModifiedTimes = new HashMap<String,Long>();
        if(_checkOutputTimestampVal) {
            for(String outputName : getOutputNames(false)) {
                StringParameter parameter = (StringParameter) getAttribute(outputName);
                String outputFileStr = parameter.stringValue();
                File outputFile = new File(outputFileStr);
                lastModifiedTimes.put(outputFile.getPath(), outputFile.lastModified());
            }
        }
        
        super.fire();
    
        if(_checkOutputTimestampVal) {
            // verify that each output file's last modified timestamp
            // has increased.
            for(String outputName : getOutputNames(false)) {
                StringParameter parameter = (StringParameter) getAttribute(outputName);
                String outputFileStr = parameter.stringValue();
                File outputFile = new File(outputFileStr);
                // do not check if the output is a directory
                if(!outputFile.isDirectory()) {
	                long lastModifiedTimeBeforeExec = lastModifiedTimes.get(outputFile.getPath());
	
	                // see if the file was not updated
	                if(lastModifiedTimeBeforeExec > 0 && 
	                        outputFile.lastModified() <= lastModifiedTimeBeforeExec) {
	                    throw new IllegalActionException(this, "Output " +
	                            outputName + " (" + outputFile + ")\n" +
	                            "does not appear to have been updated.");
	                } // see if the file did not exist before and after we executed the refinement
	                else if(outputFile.lastModified() == 0) {
	                    throw new IllegalActionException(this, "Output " +
	                            outputName + " (" + outputFile + ")\n" +
	                            " was not created.");
	                }
                }
            }            
        }

    }
    
    /** Get the command line argument for a parameter. 
     * @param name the name of the parameter
     * @return the command line argument if one was set, otherwise null 
     */
    public String getArgument(String name) throws IllegalActionException {
        return getArgument(this, name);
    }
    
    /** Get the command line argument for a parameter in a specific container
     * @param container the container 
     * @param name the name of the parameter
     * @return the command line argument if one was set, otherwise null 
     */
    public static String getArgument(NamedObj container, String name) throws IllegalActionException {       

    	Parameter argumentParameter = getArgumentParameter(container, name);
        if(argumentParameter != null) {
            return argumentParameter.getExpression();
        }
        return null;        
    }

    /** Get the parameter containing the command line argument for a parameter.
     *  @param name the name of the parameter
     *  @return the parameter containing the value of the argument
     */
    public StringParameter getArgumentParameter(String name) throws IllegalActionException {
    	return getArgumentParameter(this, name);
    }

    /** Get the parameter containing the command line argument for a parameter
     *  in a specific container.
     *  @param container the container
     *  @param name the name of the parameter
     *  @return the parameter containing the value of the argument
     */
    public static StringParameter getArgumentParameter(NamedObj container, String name)
    		throws IllegalActionException {
    
        Attribute attribute = container.getAttribute(name);
        if(attribute == null) {
            throw new IllegalActionException(container, "No component with name " + name);
        }
        
        return (StringParameter) attribute.getAttribute(ARGUMENT_NAME);
    }

    /** Get a list of execution choice names. */
    public List<String> getExecutionChoiceNames() {
        
        final List<Refinement> refinements = entityList(Refinement.class);
        final String[] namesArray = new String[refinements.size()];
        int i = 0;
        for(Refinement refinement : refinements) {
            namesArray[i++] = refinement.getName();
        }
        Arrays.sort(namesArray);
        return Arrays.asList(namesArray);
    }

    /** Get a list of file input names. */
    public List<String> getInputNames() throws IllegalActionException {
        return getInputNames(false);
    }
    
    /** Get a list of input port names. */
    public List<String> getInputNames(boolean includeData) throws IllegalActionException {
        return _getIONames(inputPortList(), includeData);
    }

    /** Get a list of file output names. */
    public List<String> getOutputNames() throws IllegalActionException {
        return getOutputNames(false);
    }
    
    /** Get a list of output port names. */
    public List<String> getOutputNames(boolean includeData) throws IllegalActionException {
        return _getIONames(outputPortList(), includeData);
    }    

    /** Get a list of parameter names. The returned list does not include
     *  parameters associated with inputs or outputs, fields of
     *  ExecutionChoice, visibility set to none, or names starting with "_". 
     */
    public List<String> getParameterNames() throws IllegalActionException {
        final List<String> inputs = getInputNames(false);
        final List<String> outputs = getOutputNames(false);
        
        final List<String> fields = new LinkedList<String>();
        for(Field field : getClass().getFields()) {
            fields.add(field.getName());
        }
        
        final List<String> parameterNames = new LinkedList<String>();
        for(Parameter parameter : attributeList(Parameter.class)) {
            String name = parameter.getName();
            if (!inputs.contains(name) && !outputs.contains(name)
                    && !fields.contains(name)
                    && !name.startsWith("_")
                    && parameter.getVisibility() != Settable.NONE) {
                parameterNames.add(name);
            }
        }
        
        return parameterNames;
    }

    /** Get the parameter type for a parameter. */
    public ParameterType getParameterType(String name) throws IllegalActionException {
    	return getParameterType(this, name);
    }
    
    /** Get the parameter type for a parameter in a container within this actor. */
    public static ParameterType getParameterType(NamedObj container, String name) throws IllegalActionException {
    	
    	Parameter parameter = (Parameter) container.getAttribute(name);
    	if(parameter == null) {
    		throw new IllegalActionException(container, "Parameter " + name +
    				" in " + container.getName() + " not found.");
    	}
    	
    	if(parameter instanceof StringParameter) {
    		return ParameterType.String;
    	} else {
    		return ParameterType.Numeric;
    	}
    }
    
    /** Get the IOType for a port. */
    public static IOType getPortIOType(Port port) throws IllegalActionException {
        Parameter typeParameter = (Parameter) port.getAttribute(IO_TYPE_NAME);
        if(typeParameter == null) {
            System.err.println("WARNING: missing IO type for " + port + ".");
            System.err.println("Assuming type is File.");            
            try {
                _setPortIOTypeParameter(port, IOType.File);
            } catch (NameDuplicationException e) {
                throw new IllegalActionException(port, e, "Error setting Port IO type.");
            }
            return IOType.File;
        }
        
        return  IOType.valueOf(typeParameter.getExpression());
    }

    /** Get the available template names. */
    public Set<String> getTemplateNames() throws IllegalActionException {

        String[] files = _templateDir.list(new ExtensionFilenameFilter("xml"));
        String[] names = new String[files.length + 1];
        // remove the extensions
        for(int i = 0; i < files.length; i++) {
            names[i] = files[i].substring(0, files[i].length() - 4);
        }
        // add choice for an empty refinement
        names[files.length] = EMPTY_TEMPLATE_NAME;
        Arrays.sort(names);
        return new LinkedHashSet<String>(Arrays.asList(names));
    }
    
    /** Returns true if there is an input with the given name. */
    public boolean hasInput(String name) {
    	return _hasInputOutput(name, true);
    }
    
    /** Returns true if there is an output with the given name. */
    public boolean hasOutput(String name) {
    	return _hasInputOutput(name, false);
    }
        
    /** Create a new execution choice from a template.
     *  @param templateName the name of the template. The
     *  template name must be contained in the set of names
     *  return by getTemplateNames(). 
     *  @param name the name to give the new refinement
     *  @return The refinement created for the new execution choice.
     */
    public Refinement newExecutionChoice(String templateName, String refinementName)
    	throws IllegalActionException {

        Refinement newRefinement = null;
        if(templateName.equals(EMPTY_TEMPLATE_NAME)) {
            try {
                newRefinement = newRefinement(refinementName);

                // add ports to the blank refinement
                _updatePortsAndInsideLinks();
                
                updateExecutionChoices();

                _addCommandLineParameter(newRefinement);

            } catch (NameDuplicationException e) {
                throw new IllegalActionException(this, e, "Error adding blank template.");
            }
        } else {
        
            String templateNameReplaced = templateName.replaceAll(" ", "");
            
            File templateFile = new File(_templateDir, templateNameReplaced + ".xml");
            if(!templateFile.exists()) {
                throw new IllegalActionException(this, "Could not find template " +
                        templateName + ": " + templateFile);
            }
            
            newRefinement = newExecutionChoice(templateFile, refinementName);
        }
        
        return newRefinement;
        
    }
    
    /** Create a new execution choice from a file.
     *  @param templateFile the containing the MoML of the execution choice.
     *  @param name the name to give the new refinement.
     *  @return The refinement created for the new execution choice.
     */
    public Refinement newExecutionChoice(File templateFile, String refinementName)
    		throws IllegalActionException {
    	
        // see if a refinement with the same name already exists
        Refinement existingRefinement = (Refinement) getEntity(refinementName);        
        if(existingRefinement != null) {
        	// in some cases, the default local execution refinement is saved
        	// in the model. if the existing refinement is the default one, 
        	// rename it.
        	if(refinementName.equals(DEFAULT_TEMPLATE_NAME)) {
        		try {
					existingRefinement.setName("OLD " + DEFAULT_TEMPLATE_NAME);
				} catch (NameDuplicationException e) {
					throw new IllegalActionException(this, e, "Unable to rename " +
							" old LocalExecution execution choice.");
				}
        	} else {
	        	throw new IllegalActionException(this, "An execution choice with the name " +
	    			refinementName + " has already been loaded.");
        	}
        }
            
        //final List<Refinement> existingRefinements = entityList(Refinement.class);

        Refinement newRefinement = null;

        ExecutionChoice container = null;
		try {
			container = new ExecutionChoice(_workspace);
			container.setName("imported container");
		} catch (NameDuplicationException e) {
			throw new IllegalActionException(this, e, "Error create new MultiCompositeActor.");
		}
		
		// add program and additionalOptions parameters to the container
		// since parameters in the template may reference them
		/*
		try {
			new StringParameter(container, "program");
		} catch (NameDuplicationException e) {
			throw new IllegalActionException(this, e, "Error creating program parameter.");
		}
		try {
			new StringParameter(container, "additionalOptions");
		} catch (NameDuplicationException e) {
			throw new IllegalActionException(this, e, "Error creating additionalOptions parameter.");
		}
		*/
		
		try {
			addDefaultInputsAndOutputs(container);
		} catch (NameDuplicationException e) {
			throw new IllegalActionException(this, e, "Error adding defaults to container.");
		}
		
		// call validateSettables() to validate program and additionalOptions
		//container.validateSettables();
		
		// load the template file into the container
        _loadExecutionChoice(templateFile, container);
        
        MultiCompositeActor exportedContainer;
        
        // see if the template was a refinement (old version)
        if(container.entityList(MultiCompositeActor.class).isEmpty()) {
        	System.out.println("WARNING: template is older version that does " +
        			"not contain ports and parameters.");  
        	
        	exportedContainer = container;
        } else {
        	
        	exportedContainer = container.entityList(MultiCompositeActor.class).get(0);
        	
        	// create ports
        	for(Port port : (List<Port>)exportedContainer.portList()) {

        		// see if we already have a port with this name
        		final String portName = port.getName();
        		if(getPort(portName) == null) {
        			
        			final IOType type = getPortIOType(port);
        			
        			// if it's a File, get the argument
        			String argument = null;
        			if(type == IOType.File) {
        				argument = getArgument(exportedContainer, portName);
        			}
        			
        			try {
        				if(((IOPort) port).isInput()) {
        					newInput(portName, type, argument);
        				} else {
        					newOutput(portName, type, argument);
        				}
        			} catch(NameDuplicationException e) {
        				throw new IllegalActionException(this, e, "Error creating port " + 
        						portName + " from template.");
        			}
        			
        			// if it's a File, set the parameter value
        			String value = ((Parameter) exportedContainer.getAttribute(portName)).getExpression();
        			((Parameter) getAttribute(portName)).setExpression(value);
        		}
        	}
        	
        	// create parameters
        	List<Parameter> parameterList = new LinkedList<Parameter>(exportedContainer.attributeList(Parameter.class));
        	for(Parameter parameter : parameterList) {
        		final String parameterName = parameter.getName();
        		// see if we already have a parameter with this name, or
        		// the parameter was created when we added ports above
        		if(getAttribute(parameterName) == null) {
        			try {
						parameter.setContainer(this);
					} catch (NameDuplicationException e) {
						throw new IllegalActionException(this, e, "Error adding port " +
								parameterName + " from template.");
					}
        		}
        	}
        }
        
    	// move the refinement
    	List<Refinement> refinements = exportedContainer.entityList(Refinement.class);
    	if(refinements.size() == 0) {
    		throw new IllegalActionException(this, "No execution choices found in template.");
    	} 
    	
    	newRefinement = refinements.get(0);
    	
    	if(refinements.size() > 1) {
    		System.out.println("WARNING: more than one executon choice found in template.");
    		System.out.println("Only " + newRefinement.getName() + " will be loaded.");
    	}
    	
    	try {        	
    		// set the name before moving the refinement to this actor
        	newRefinement.setName(refinementName);
			newRefinement.setContainer(this);
		} catch (NameDuplicationException e) {
			throw new IllegalActionException(this, e, "Error adding template into actor.");
		}

        try {
			container.setContainer(null);
		} catch (NameDuplicationException e) {
			throw new IllegalActionException(this, e, "Error deleting template container.");
		}
            
        
        // the template is the new refinement
        /*
        final List<Refinement> currentRefinements = entityList(Refinement.class);
        for(Refinement refinement : currentRefinements) {
        	if(!existingRefinements.contains(refinement)) {
        		newRefinement = refinement;
        		break;
        	}
        }

        if(newRefinement == null) {
        	throw new IllegalActionException(this, "Error loading refinement." +
        			" Perhaps it is not a Refinement?");
        }
        */
        
        /*
        if(!(newRefinement instanceof Refinement)) {
            throw new IllegalActionException(this, "Template is not a Refinement.");
        }
        */
        
        // we no longer have to clone the refinement into our workspace since
        // the template is loaded by incremental parsing.
        /*
        try {
            newRefinement = (Refinement) namedObj.clone(workspace());
        } catch (CloneNotSupportedException e) {
            throw new IllegalActionException(this, e, "Cloning error.");
        }
        */
                     
        // remove provenance recorder and reporting listener
        // if they are present
        List<Attribute> toRemove = new LinkedList<Attribute>(
        		newRefinement.attributeList(ProvenanceRecorder.class));
        toRemove.addAll(newRefinement.attributeList(ReportingListener.class));
        for(Attribute attribute : toRemove) {
        	try {
				attribute.setContainer(null);
			} catch (NameDuplicationException e) {
				throw new IllegalActionException(this, e,
					"Error removing " + attribute.getName());
			}
        }
        
        try {
            newRefinement.setContainer(this);
        } catch (NameDuplicationException e) {
            throw new IllegalActionException(this, e, "Error adding template.");
        }
        
        // NOTE: do not update _current, since that's done in CaseDirector.prefire().

        if(_default == null) {
            _default = newRefinement;
        }

        // the refinement loaded from the template and the containing
        // MultiCompositeActor (this class) may have a different set
        // of ports. we need to make sure they both have the same set
        // of ports and each outside-inside pair are linked.
        // FIXME is this still necessary?
        _updatePortsAndInsideLinks();
        
        updateExecutionChoices();

        _addCommandLineParameter(newRefinement);

        // repaint the canvas to show new ports
        MoMLChangeRequest change = 
                new MoMLChangeRequest(this, this, "<group></group>");
        change.setPersistent(false);
        requestChange(change);

        return newRefinement;
    }
           
    private void _loadExecutionChoice(File templateFile, NamedObj container)
    		throws IllegalActionException {
        
    	MoMLParser parser = new MoMLParser();
        // NOTE: the template may reference parameters not defined in the
        // template but defined in ExecutionChoice, e.g., outputPath or
        // inputPath, so we set the parser's context to this actor to
        // avoid exceptions when parsing the template.
        parser.setContext(container);
                
    	String templateStr = null;
    	try {
    		templateStr = FileUtils.readFileToString(templateFile);
    	} catch(IOException e) {
    		throw new IllegalActionException(this, e,
    				"Error reading template file " + templateFile.getAbsolutePath());
    	}

        try {
        	// use incremental parsing to load the template.
        	// this is necessary since all the MoMLParser.parse()
        	// methods return the top-level object, which is our
        	// top-level object since we called MoMLParser.setContext().
        	
        	// don't put in a <group></group> since the template MoML is
        	// a top-level object (contains <!DOCTYPE>
        	//StringBuilder moml = new StringBuilder("<group>");
        	//moml.append(templateStr);
        	//moml.append("</group>");
        	//parser.parse(moml.toString());
        	parser.parse(templateStr);
        } catch (Exception e) {
            throw new IllegalActionException(this, e, "Error parsing " + templateFile);
        }
		
	}

	/** Create a new file input. */
    public void newInput(String name, IOType type) throws NameDuplicationException, IllegalActionException {
        
        newInput(name, type, null);
    }

    /** Create a new file input. */
    public void newInput(String name, IOType type, String argument) throws NameDuplicationException, IllegalActionException {

        _newInputOrOutput(name, type, argument, true);

        // if the IO type is file, and the name is not the default
        // input directory name, set the default value of the
        // parameter to be the default input directory . the name
        if(type == IOType.File && !name.equals(DEFAULT_INPUT_DIR_NAME)) {
            Parameter parameter = (Parameter) getAttribute(name);
            if(getAttribute(DEFAULT_INPUT_DIR_NAME) != null) {
            	parameter.setExpression("$" + DEFAULT_INPUT_DIR_NAME + File.separator +
            			getName() + "." + name);
            } else {
            	parameter.setExpression(getName() + "." + name);
            }
        }
        
        /* NOTE: PortParameter ports are not mirrored for MultiCompositeActors
        PortParameter input = new PortParameter(this, name);
        input.setStringMode(true);
        input.setExpression(getName() + "." + name);
        
        TypedIOPort port = input.getPort();
        port.setTypeEquals(BaseType.STRING);
        new SingletonAttribute(port, "_showName");
        */
        
    }
    
    /** Create a new file output. */
    public void newOutput(String name, IOType type) throws IllegalActionException, NameDuplicationException {
        
        newOutput(name, type, null);
        
    }
    
    /** Create a new file output. */
    public void newOutput(String name, IOType type, String argument) throws IllegalActionException, NameDuplicationException {
    
        _newInputOrOutput(name, type, argument, false);
        
        // if the IO type is file, and the name is not the default
        // output directory name, set the default value of the
        // parameter to be the default output directory . the name
        if(type == IOType.File && !name.equals(DEFAULT_OUTPUT_DIR_NAME)) {
            Parameter parameter = (Parameter) getAttribute(name);
        	if(getAttribute(DEFAULT_OUTPUT_DIR_NAME) != null) {
        		parameter.setExpression("$" + DEFAULT_OUTPUT_DIR_NAME + File.separator +
        			getName() + "." + name);
        	} else {
        		parameter.setExpression(getName() + "." + name);
        	}
        }

    }
    
    /** Add a new parameter.
     * @param name the parameter name
     * @param argument the command line argument for this parameter. this can be null. 
     */
    public void newParameter(String name, String argument)
            throws IllegalActionException, NameDuplicationException {
        newParameter(name, argument, null);
    }
    
    /** Add a new parameter.
     * @param name the parameter name
     * @param argument the command line argument for this parameter. this can be null. 
     * @param value the default value for the paramete. this can be null.
     */
    public void newParameter(String name, String argument, String value)
            throws IllegalActionException, NameDuplicationException {

        newParameter(name, argument, value, null, ParameterType.String);
    }
    
    /** Add a new parameter.
    * @param name the parameter name
    * @param argument the command line argument for this parameter. this can be null. 
    * @param value the default value for the paramete. this can be null.
    * @param choice the refinement in which to add the parameter. if null or
    * set to ALL_CHOICES_NAME, the parameter is added to this actor.
    */
    public void newParameter(String name, String argument, String value, String choice,
            ParameterType type) throws IllegalActionException, NameDuplicationException {

        ComponentEntity<?> refinement = null;
        if(choice != null && !choice.equals(ALL_CHOICES_NAME)) {
            refinement = getEntity(choice);
        }

        NamedObj container;
        if(refinement == null) {
            container = this;
        } else {
            container = refinement;
        }
        
        Parameter parameter;
        if(type == ParameterType.Numeric) {
            parameter = new Parameter(container, name);
        } else {
            parameter = new StringParameter(container, name);
        }
        
        if(argument != null && !argument.isEmpty()) {
            setArgument(parameter, argument);
        }
        
        if(value != null) {
            parameter.setExpression(value);
        }
        
        
        if(refinement == null) {
            _addToCommandLines(name);
        }
        
    }
    
    /** Create a new execution choice. Override the parent class to
     *  add the execution choice name to the parameter list.
     */
    /*
    @Override
    public Refinement newRefinement(String name) throws IllegalActionException, NameDuplicationException {
        Refinement refinement = super.newRefinement(name);
        if(!name.equals("default")) {
            control.addChoice(name);
        }
        return refinement;
    }
    */
    
    /** Receive a port event. If the port is an input port and has an
     *  associated parameter, set the value of the parameter to be
     *  the token read by the port.
     */
    @Override
    public void portEvent(IOPortEvent event) throws IllegalActionException {

        //System.out.println(event);
        if(event.getEventType() == IOPortEvent.GET_END) {
            //System.out.println("got read: " + event);
            IOPort port = event.getPort();
            StringParameter parameter = (StringParameter) getAttribute(port.getName());
            if(parameter != null) {
                parameter.setToken(event.getToken());
                //System.out.println("set parameter " + parameter);
            }
        }
    }

    /** List to port events for File input ports so that when a token is read, we
     * can set the corresponding parameter. Also, make sure output Data file ports
     * that are connected outside are connected in the current refinement.
     * 
     * @see #portEvent()
     */
    @Override
    public void preinitialize() throws IllegalActionException {

        super.preinitialize();
        
        addDefaultExecutionChoice();

        for(Object object : portList()) {
            final TypedIOPort port = (TypedIOPort) object;

            // see if the IOType is file
            if(port != control.getPort() && getPortIOType(port) == IOType.File) {
                
                // for input ports, listen to port events so we can set
                // the corresponding parameter
                if(port.isInput()) {
                    port.addIOPortEventListener(this);
                }
                
                // set the type to string
                port.setTypeEquals(BaseType.STRING);
            }
            
            // make sure output data ports that are connected outside, are connected
            // inside the current refinement
            if(port.isOutput() && port.numberOfSinks() > 0) {
                final IOType type = getPortIOType(port);
                if(type == IOType.Data) {
                    
                    // make sure it's connected inside
                    String refinementName = ((StringToken)control.getToken()).stringValue();
                    Refinement refinement = (Refinement) getEntity(refinementName);
                    if(refinement == null) {
                        throw new IllegalActionException(this, "Execution choice not found: " + refinementName);
                    }
                    
                    IOPort refinementPort = (IOPort) refinement.getPort(port.getName());
                    if(!refinementPort.isInsideConnected()) {
                        throw new IllegalActionException(this, "Output Data port " + port.getName() +
                            " is not connected inside the execution choice " + refinementName); 
                    }
                }
            }
        }
        
        
        // create directories for File outputs
        for(String outputName : getOutputNames(false)) {
        	
        	// create directories for outputs with specific names
        	if(outputName.endsWith("Dir") ||
        			outputName.endsWith("_dir") ||
        			outputName.endsWith("_Dir")) {        			
	        	Parameter outputParameter = (Parameter) getAttribute(outputName);
	        	if(outputParameter != null) {
	        		Token token = outputParameter.getToken();
	        		if(token != null) {
	        			String outputString;
	                	if(token instanceof StringToken) {
	                		outputString = ((StringToken)token).stringValue();
	                	} else {
	                		outputString = token.toString();
	                	}
	                	
	                	// make sure the value is not empty
	                	if(!outputString.trim().isEmpty()) {
		                    File outputPathDir = new File(outputString);
		                    if(!outputPathDir.exists() && !outputPathDir.mkdirs()) {
		                        throw new IllegalActionException(this, "Could not create " +
		                                " directory: " + outputPathDir);
		                    }
	                	}
	        		}
	        	}
	        }
        }
                
    }
        
    /** Remove an execution choice. */
    public void removeExecutionChoice(String name) throws IllegalActionException, NameDuplicationException {
        
        ComponentEntity<?> refinement = getEntity(name);
        if(refinement == null) {
            throw new IllegalActionException(this, "Could not find execution choice " + name);
        }
        refinement.setContainer(null);  
        
        // update the choices in the control parameter
        updateExecutionChoices();
    }
    
    /** Remove a file input. */ 
    public void removeInput(String name) throws Exception {
    	_removeInputOrOutput(name, true);
    }

    /** Remove a file output. */
    public void removeOutput(String name) throws Exception {
    	_removeInputOrOutput(name, false);
    }
    
    /** Remove a parameter. */
    public void removeParameter(String name) throws Exception {
        
        Attribute attribute = getAttribute(name);
        if(attribute == null) {
            throw new IllegalActionException(this, "No parameter called " + name);
        }

        // remove first from command line since we know the argument, if any
        _removeFromCommandLines(name);
        
        // remove from any parameters that are referencing it
        _removeFromIOParameters(name);
        
        attribute.setContainer(null);
        
        _removeInDocumentation(name);
    }

    /** Rename an execution choice. */
    public void renameExecutionChoice(String oldName, String newName) {

        //System.out.println("rename choice " + oldName + " to " + newName);       
        _rename("entity", oldName, newName, null, this);
    }

    /** Rename an input. */
    public void renameInput(String oldName, String newName) {

    	_renameInputOrOutput(oldName, newName, true);
    }

    /** Rename a parameter. */
    public boolean renameParameter(String oldName, String newName) {
        return renameParameter(oldName, newName, this);
    }
    
    /** Rename a parameter in the specified container. */
    public boolean renameParameter(String oldName, String newName, NamedObj container) {

        //System.out.println("rename parameter " + oldName + " to " + newName);   

    	boolean retval = false;
    	
        if(container == this) {
            // save the command lines and clear them since rename is broken
            _saveAndClearCommandLines();
        }
        
        // do the rename
        boolean renamed = _rename("property", oldName, newName, null, container);
        
        if(container == this) {
        	// see if the renamed failed
	        if(!renamed) {
	        	// restore the command lines to their original values
	        	_restoreCommandLines();
	        } else {
	            // update the command lines with the parameter renamed
	            _renameInCommandLines(oldName, newName);
	            
	            // update the documentation
	            try {
					_renameInDocumentation(oldName, newName);
				} catch (Exception e) {
					try {
						MessageHandler.warning("Unable to update documentation.", e);
					} catch (CancelException e1) {
						// ignore
					}
				}
	            
	            retval = true;
	        }
        }
        
        return retval;
    }

    /** Rename an output. */
    public void renameOutput(String oldName, String newName) {
    	_renameInputOrOutput(oldName, newName, false);
    }

    /** Set the command line argument for a parameter. */
    public static void setArgument(Parameter parameter, String argument)
            throws IllegalActionException, NameDuplicationException {
    	StringParameter argumentParameter = (StringParameter) parameter.getAttribute(ARGUMENT_NAME);
        if(argumentParameter == null) {
            argumentParameter = new StringParameter(parameter, ARGUMENT_NAME);
        }
        argumentParameter.setExpression(argument);        
    }

    /** Set the ParameterType for a parameter contained by this actor. */
    public void setParameterType(String name, ParameterType type) 
    		throws IllegalActionException, NameDuplicationException {
    	setParameterType(this, name, type);
    }
    
    /** Set the ParameterType for a parameter in contained in a sub-workflow. */
    public static void setParameterType(NamedObj container, String name, ParameterType type) 
    		throws IllegalActionException, NameDuplicationException {
    	
    	Parameter parameter = (Parameter) container.getAttribute(name);
    	if(parameter == null) {
    		throw new IllegalActionException(container, "Parameter " + name +
    				" in " + container.getName() + " not found.");
    	}
    	
    	// save the current value and argument
    	final String value = parameter.getExpression();
    	final String argument = getArgument(container, name);
    	
    	// FIXME this can bring up the warning about dependencies
    	parameter.setContainer(null);
    	if(type == ParameterType.String) {
    		parameter = new StringParameter(container, name);
    	} else {
    		parameter = new Parameter(container, name);
    	}
    	
    	// restore the current value and argument
    	// XXX what happens if value is a string and the type is numeric?
    	parameter.setExpression(value);
    	if(argument != null) {
    		setArgument(parameter, argument);
    	}
    		
    }
    
    /** Set the PortType for a port. */
    public void setPortIOType(TypedIOPort port, IOType type) 
    		throws IllegalActionException, NameDuplicationException {
    	
    	final String portName = port.getName();
    	
        _setPortIOTypeParameter(port, type);
        
        if(type == IOType.File) {
        	
            port.setTypeEquals(BaseType.STRING);

            // create the associated parameter
            /*StringParameter newParameter =*/ new StringParameter(this, portName);
            
            _addToCommandLines(portName);
            
        } else { // type == IOType.Data
        	
        	port.setTypeEquals(BaseType.UNKNOWN);

        	// remove the parameter from the command lines.
        	// NOTE: this is done before removing the parameter since it
        	// accessed the parameter argument.
            _removeFromCommandLines(portName);

            // remove the name if referenced in any other parameter
            _removeFromIOParameters(portName);
            
        	// remove the associated parameter and any argument
        	StringParameter associatedParameter = (StringParameter) getAttribute(portName);
        	
        	// FIXME this can display the warning dialog about dependencies
        	associatedParameter.setContainer(null);
        	
        }

        _updatePortsAndInsideLinks();
    }
    
    /** Update the execution choices in the control parameter. */
    public void updateExecutionChoices() throws IllegalActionException {

        List<String> choices = new LinkedList<String>();
        
        List<StringParameter> existingChoices = _choiceStyle.attributeList(StringParameter.class);
        for(StringParameter parameter : existingChoices) {
            if(parameter.getName().startsWith("choice")) {
                String name = parameter.getExpression();
                // make sure refinement exists
                if(getEntity(name) == null) {
                    try {
                        // refinement no longer exists so remove the choice
                        parameter.setContainer(null);
                    } catch (NameDuplicationException e) {
                        throw new IllegalActionException(this, e, "Error removing choice " + name);
                    }
                } else {
                    choices.add(name);
                }
            }
        }
        
        for(Refinement refinement : entityList(Refinement.class)) {
            if(!choices.contains(refinement.getDisplayName())) {
                // add a new parameter contained by the choice style so that the
                // choice is saved to MoML.
                Parameter choiceParameter;
                try {
                    choiceParameter = new StringParameter(_choiceStyle, _choiceStyle.uniqueName("choice"));
                    choiceParameter.setExpression(refinement.getDisplayName());
                } catch (NameDuplicationException e) {
                    throw new IllegalActionException(this, e, "Error adding choice.");
                }
                choices.add(refinement.getDisplayName());
            }
        }

        // see if current choice was removed
        if(!choices.isEmpty() &&
        		!choices.contains(((StringToken)control.getToken()).stringValue())) {
            // set the control choice to the default
            control.setExpression(choices.get(0));
        }
    }

    /** Perform cleanup. Stop listening to input ports. */
    @Override
    public void wrapup() throws IllegalActionException {
        
        super.wrapup();
        
        for(Object object : inputPortList()) {
            IOPort port = (IOPort) object;
            if(getAttribute(port.getName()) != null) {
                port.removeIOPortEventListener(this);
            }
        }
        
    }
    

    /** The command line program to execute. */
    public StringParameter program;
    
    /** Additional command line options. */
    public StringParameter additionalOptions;
        
    /** If true, verify the last modification timestamp for each
     *  output file has increased after execution. If the timestamp
     *  has not increased, throw an error.
     */
    public Parameter checkOutputTimestamp;
    
    /** The name of the default template. */
    public final static String DEFAULT_TEMPLATE_NAME = "LocalExecution";

    /** The name of the default input directory. */
    public final static String DEFAULT_INPUT_DIR_NAME = "inputDir";

    /** The name of the default output directory. */
    public final static String DEFAULT_OUTPUT_DIR_NAME = "outputDir";

    /** The name of the default input file. */
    public final static String DEFAULT_INPUT_FILE_NAME = "inputFile";

    /** The name of the default output file. */
    public final static String DEFAULT_OUTPUT_FILE_NAME = "outputFile";
    
    /** String constant to denote all execution choices. **/
    public final static String ALL_CHOICES_NAME = "All Choices";
    
    /** The name of the (optional) attribute contained in parameters.
     *  The value of this attribute is the command-line argument for
     *  the parameter, e.g., "-e".
     */
    public final static String ARGUMENT_NAME = "Argument";
    
    /** The name of the command line parameter in each refinement. */
    public final static String COMMAND_LINE_NAME = "commandLine";

    /** The types of input/outputs. */
    public enum IOType {
        File,
        Data;        
    };
    
    /** The types of parameters. */
    public enum ParameterType {
        Numeric,
        String;
    }
    
    ///////////////////////////////////////////////////////////////////
    ////                       protected methods                   ////

    /** Create a director. This base class creates an instance of CaseDirector.
     *  @return The created director.
     *  @exception IllegalActionException If the director cannot be created.
     *  @exception NameDuplicationException If there is already an
     *  attribute with the name "_director".
     */
    @Override
    protected ExecutionChoiceDirector _createDirector() throws IllegalActionException,
            NameDuplicationException {
        return new ExecutionChoiceDirector(this, "_director");
    }

    /** Set the refinement to execute. */
    protected void _setCurrentRefinement(Refinement refinement) {
        _current = refinement;
    }

    ///////////////////////////////////////////////////////////////////
    ////                         private method                    ////

    /** Add a input/output/parameter to the command lines of each refinement. */
    private void _addToCommandLines(String name)
            throws IllegalActionException, NameDuplicationException {

        // construct the string to add
        String toAdd = _makeCommandLineArgument(name);
        
        // see if we should add to beginning or end
        String argument = getArgument(name);
        boolean append = (argument != null && argument.equals(">"));

        // update each refinement
        for(Refinement refinement : entityList(Refinement.class)) {
            Parameter commandLineParameter = (Parameter) refinement.getAttribute(COMMAND_LINE_NAME);
            if(commandLineParameter == null) {
                commandLineParameter = new StringParameter(refinement, COMMAND_LINE_NAME);
                //commandLineParameter.setLazy(true);
            }
            String expression = commandLineParameter.getExpression();
            if(append) {
                commandLineParameter.setExpression(expression + " " + toAdd);                
            } else {
                // add to the beginning of the command line, but after $program
                int index = expression.indexOf("$program");
                
                // make sure $program was found; otherwise do not add
                if(index > -1) {
                	
                	// if expression is only "$program", add a space and the argument to add
                	if(expression.equals("$program")) {
                		commandLineParameter.setExpression("$program " + toAdd + " ");
                	} else {                	
	                    StringBuilder value = new StringBuilder(expression);
	                    value.insert(index + "$program".length() + 1, toAdd + " ");
	                    commandLineParameter.setExpression(value.toString());
                	}
                }
            }
            
            //System.out.println("add: new command line for " + refinement.getName() + ":" +
                //commandLineParameter.getExpression());
        }

        // update the default command line
        if(append) {
            _commandLineArguments += " " + toAdd;
        } else {
            _commandLineArguments = toAdd + " " + _commandLineArguments;
        }
    }

    /** Add the commandLine parameter to a refinement if it is not there. */
    private void _addCommandLineParameter(Refinement refinement) throws IllegalActionException {
        
        // see if the commandLine parameter exists in this refinement
        Parameter commandLineParameter = (Parameter) refinement.getAttribute(COMMAND_LINE_NAME);
        if(commandLineParameter == null) {
            try {
                commandLineParameter = new StringParameter(refinement, COMMAND_LINE_NAME);
                //commandLineParameter.setLazy(true);
            } catch (NameDuplicationException e) {
                throw new IllegalActionException(this, e, "Error adding commandLine parameter.");
            }

            // set the default value
            commandLineParameter.setExpression("$program " + _commandLineArguments);
        }
    }

    /** Get a list of input/output names.
     *  @param ports the input or output ports from which to get names.
     *  @param includeData if true, include Data ports in the list of names.
     *  otherwise, just return a list of File ports.
     */
    private List<String> _getIONames(List<?> ports, boolean includeData) throws IllegalActionException {
        final List<String> retval = new LinkedList<String>();
        for(Object object : ports) {
            if(object != control.getPort()) {
                final TypedIOPort port = (TypedIOPort) object;
                if(includeData || getPortIOType(port) == IOType.File) {
                    retval.add(port.getDisplayName());
                }
                /*
                if(getAttribute(port.getDisplayName()) != null) {
                    retval.add(port.getDisplayName());
                }
                */
            }
        }
        return retval;
    }

    /** Returns true if there is an input/output with the given name.
     *  @param name The name to check.
     *  @param input If true, check inputs, otherwise check outputs.
     *  @return True if the input/output exists. 
     */
     private boolean _hasInputOutput(String name, boolean input) {
    	TypedIOPort port = (TypedIOPort) getPort(name);
    	if(port != null) {
    		if(input) {
    			return port.isInput();
    		} else {
    			return port.isOutput();
    		}
    	}
    	return false;
    }

    /** Initialize parameters and load the default template. */
    private void _init() throws NameDuplicationException, IllegalActionException {
        
        setClassName("org.kepler.ddp.actor.ExecutionChoice");

        // remove the default refinement created by the parent class
        _default.setContainer(null);
        _default = null;

        // hide the control port
        control.setStringMode(true);
        new SingletonAttribute(control.getPort(), "_hide");
        control.setDisplayName("Choice");
        control.setExpression(DEFAULT_TEMPLATE_NAME);
        control.removeAllChoices();
               
        program = new StringParameter(this, "program");
        program.setExpression("ls");
        
        additionalOptions = new StringParameter(this, "additionalOptions");
        
        //new ExecutionChoiceEditorFactory(this, "_editor");
        new ExecutionChoiceEditorPane.Factory(this, "_editorPane");
        
        _choiceStyle = new EditableChoiceStyle(control, "style");
        
        // parse the template
        ModuleTree tree = ModuleTree.instance();
        Module module = tree.getModuleByStemName("ddp-common");
        if(module == null) {
            throw new IllegalActionException(this, "Could not find ddp-common module in suite.");
        }
                
        String templateDirPathStr = module.getResourcesDir().getAbsolutePath() +
                File.separator + "templates" + File.separator +
                "ExecutionChoice";
        _templateDir = new File(templateDirPathStr);
        
        checkOutputTimestamp = new Parameter(this, "checkOutputTimestamp");
        checkOutputTimestamp.setTypeEquals(BaseType.BOOLEAN);
        checkOutputTimestamp.setToken(BooleanToken.TRUE);
    }
    
    public void addDefaults() throws IllegalActionException, NameDuplicationException {        
                    
        addDefaultInputsAndOutputs();
        addDefaultExecutionChoice();
    }
    
    /** Add default execution choice if none are present. */
    public void addDefaultExecutionChoice() throws IllegalActionException {
        
        // see if there are any execution choices
        if(getExecutionChoiceNames().isEmpty()) {
            // load the default
            newExecutionChoice(DEFAULT_TEMPLATE_NAME, DEFAULT_TEMPLATE_NAME);   
        }

    }
    
    /** Add default inputs and outputs if none are present. 
     *  @return true if inputs/outputs were created. 
     */
    public boolean addDefaultInputsAndOutputs() throws NameDuplicationException, IllegalActionException {
    	return addDefaultInputsAndOutputs(this);
    }
    
    /** Add default inputs and outputs if non are present for an ExecutionChoice.
     *  @return true if inputs/outputs were created.
     */
    public static boolean addDefaultInputsAndOutputs(ExecutionChoice choice) 
    		throws IllegalActionException, NameDuplicationException {
    	
        // see if there are any inputs or outputs
        if(choice.getInputNames(true).isEmpty() &&
        		choice.getOutputNames(true).isEmpty()) {
            
            System.out.println("Adding default inputs, outputs for " + choice.getName());
            
            choice.newInput(DEFAULT_INPUT_FILE_NAME, IOType.File, "-i");

            choice.newOutput(DEFAULT_OUTPUT_FILE_NAME, IOType.File, ">");

            choice.newInput(DEFAULT_INPUT_DIR_NAME, IOType.File, "-i");

            choice.newOutput(DEFAULT_OUTPUT_DIR_NAME, IOType.File, ">");

            // repaint the canvas to show new ports
            MoMLChangeRequest change = 
                    new MoMLChangeRequest(choice, choice, "<group></group>");
            change.setPersistent(false);
            choice.requestChange(change);
            
            return true;
        }
        
        return false;

    }

    /** Get the command line string for an input/output/parameter. */
    private String _makeCommandLineArgument(String name) throws IllegalActionException {
        
        // construct the string to add to the command lines
        StringBuilder str = new StringBuilder();
        
        // see if there's a argument
        String argument = getArgument(name);
        if(argument != null) {
            str.append("$(");
            str.append(name);
            str.append("::");
            str.append(ARGUMENT_NAME);
            str.append(") ");
        }
        
        // add the name
        str.append("$");
        str.append(name); 
        return str.toString();
    }

    /** Create a new port and parameter for a new input or output.
     * @param name the name of the input/output
     * @param type the IOType of the input/output 
     * @param argument the command line argument for the new input
     * or output. this is only valid for IOType.File inputs/outputs,
     * and can be null.
     * @param isInput if true, the port is an input port. if false, the
     * ports is an output port.
     */
    private void _newInputOrOutput(String name, IOType type, String argument, boolean isInput)
            throws IllegalActionException, NameDuplicationException {
    	
        if(type != IOType.File && argument != null && !argument.isEmpty()) {
            throw new IllegalActionException(this, "Command-line arguments can only be " +
                    "specified for File types.");
        }

        TypedIOPort port = (TypedIOPort) newPort(name);
        new SingletonAttribute(port, "_showName");
        
        // set the port to be input or output
        // NOTE: this must be done before _showOrHideInsidePort() is called
        // (in _updatePortsAndInsideLinks()) since it hides outputs ports.
        if(isInput) {
            port.setInput(true);
        } else {
            port.setOutput(true);
        }

        _setPortIOTypeParameter(port, type);
        
        if(type == IOType.File) {
            port.setTypeEquals(BaseType.STRING);

            // create the associated parameter
            StringParameter newOutputParameter = new StringParameter(this, name);

            if(argument != null && !argument.isEmpty()) {
                setArgument(newOutputParameter, argument);
            }
            
            _addToCommandLines(name);
        }

        _updatePortsAndInsideLinks();
    }

    /** Remove a input/output/parameter from the refinement command lines.*/
    private void _removeFromCommandLines(String name)
            throws IllegalActionException, NameDuplicationException {

        // construct the string to remove
        String toRemove = _makeCommandLineArgument(name);
        
        for(Refinement refinement : entityList(Refinement.class)) {
            Parameter commandLineParameter = (Parameter) refinement.getAttribute(COMMAND_LINE_NAME);
            if(commandLineParameter == null) {
                commandLineParameter = new StringParameter(refinement, COMMAND_LINE_NAME);
                //commandLineParameter.setLazy(true);
            }
            String expression = commandLineParameter.getExpression();
            int index = expression.indexOf(toRemove);
            if(index > -1) {
                String value = expression.substring(0, index) +
                        expression.substring(index + toRemove.length());
                commandLineParameter.setExpression(value.trim());
                commandLineParameter.validate();
            }
            
            //System.out.println("remove: new command line for " + refinement.getName() + ":" +
                //commandLineParameter.getExpression());

        }
        
        // remove form the default command line
        int index = _commandLineArguments.indexOf(toRemove);
        if(index > -1) {
            String value = _commandLineArguments.substring(0, index) +
                    _commandLineArguments.substring(index + toRemove.length());
            _commandLineArguments = value.trim();
        }
    }
    
    /** Remove a name that is referenced in any input/output parameters. */
    private void _removeFromIOParameters(String name) throws IllegalActionException {
        
        String toRemove = "$" + name;
        
        List<String> toCheck = new LinkedList<String>();
        toCheck.addAll(getInputNames(false));
        toCheck.addAll(getOutputNames(false));
        
        for(String toCheckName : toCheck) {
            boolean changed = false;
            Parameter parameter = (Parameter) getAttribute(toCheckName);
            String expression = parameter.getExpression();
            int index = expression.indexOf(toRemove);
            while(index > -1) {
                changed = true;
                expression = expression.substring(0, index) +
                        expression.substring(index + toRemove.length());
                index = expression.indexOf(toRemove);
            }
            if(changed) {
                parameter.setExpression(expression.trim());
                parameter.validate();
            }
        }   
    }
    
    /** Remove a port or parameter from the documentation. */
    private void _removeInDocumentation(String name) throws Exception {
    	
    	List<KeplerDocumentationAttribute> docList = attributeList(KeplerDocumentationAttribute.class);
    	for(KeplerDocumentationAttribute docAttribute : docList) {
    		
    		// see if the hash tables have been initialized
    		Hashtable<?,?> portHash = docAttribute.getPortHash();
    		if(portHash.isEmpty()) {
    			docAttribute.createInstanceFromExisting(docAttribute);
    		}

			docAttribute.removePort(name);
			docAttribute.removeProperty(name);
    	}
    }
    
    /** Remove a file input/output. */
    private void _removeInputOrOutput(String name, boolean isInput) throws Exception {
    	
        Port port = getPort(name);
        if(port == null) {
            throw new IllegalActionException(this, "Could not find " +
            		(isInput? "intput" : "output") + " port " + name);
        }
        
        final IOType type = getPortIOType(port);
        
        port.setContainer(null);

        if(type == IOType.File) {
            removeParameter(name);
        }
        
        _removeInDocumentation(name);
    }

    /** Rename a port and parameter in the documentation. */
    private void _renameInDocumentation(String oldName, String newName) throws Exception {
    	
    	List<KeplerDocumentationAttribute> docList = attributeList(KeplerDocumentationAttribute.class);
    	for(KeplerDocumentationAttribute docAttribute : docList) {
    		
    		// see if the hash tables have been initialized
    		Hashtable<?,?> portHash = docAttribute.getPortHash();
    		if(portHash.isEmpty()) {
    			docAttribute.createInstanceFromExisting(docAttribute);
    		}
    		
    		String portDocStr = docAttribute.getPort(oldName);
    		if(portDocStr != null) {
    			docAttribute.removePort(oldName);
    			docAttribute.addPort(newName, portDocStr);
    		}
    		
    		String parameterDocStr = docAttribute.getProperty(oldName);
    		if(parameterDocStr != null) {
    			docAttribute.removeProperty(oldName);
    			docAttribute.addProperty(newName, parameterDocStr);
    		}
    	}
    }
    
    /** Rename a parameter in each of the refinement command lines. */
    private void _renameInCommandLines(String oldName, String newName) {
        
        for(Refinement refinement : entityList(Refinement.class)) {
            Parameter commandLineParameter = (Parameter) refinement.getAttribute(COMMAND_LINE_NAME);
            // see if the command line exists in this refinement
            if(commandLineParameter != null) {
                
                // get the expression saved before the rename
                String expression = _refinementCommandLines.get(refinement);
                
                // do the rename in the expression
                String value = expression.replaceAll(Pattern.quote("$" + oldName),
                    Matcher.quoteReplacement("$" + newName));
                
                // rename the parameter name in references to the argument, e.g.,:
                // $(inputFile::argument)
                value = value.replaceAll(Pattern.quote("$(" + oldName + "::"),
                        Matcher.quoteReplacement("$(" + newName + "::"));
                
                // set the new value in the parameter
                commandLineParameter.setExpression(value);
                
                //System.out.println("rename: new command line for " + refinement.getName() + ":" +
                    //commandLineParameter.getExpression());

            }
        }
        
        // update the default command line
        String value = _commandLineArguments.replaceAll(Pattern.quote("$" + oldName),
                Matcher.quoteReplacement("$" + newName));
        value = value.replaceAll(Pattern.quote("$(" + oldName + "::"),
                Matcher.quoteReplacement("$(" + newName + "::"));
        _commandLineArguments = value;
        
    }

    /** Rename a component.
     * @param type the type of component to rename: property, port, or entity.
     * @param oldName the old name
     * @param newName the new name
     * @param newDisplayName the new display name. can be null
     */
    private boolean _rename(String type, String oldName, String newName,
            String newDisplayName, NamedObj container) {

        // parameters:
        //<property name="Parameter"><rename name="name2"/></property>
        
        // ports:
        // <port name="a"><rename name="b"/></port>

        // refinements:
        // <entity name="Ramp"><rename name="r2"/></entity>

        StringBuilder moml = new StringBuilder("<");
        moml.append(type);
        moml.append(" name=\"");
        moml.append(oldName);
        moml.append("\"><rename name=\"");
        moml.append(newName);
        moml.append("\"/>");
        if(newDisplayName != null) {
            moml.append("<display name=\"");
            moml.append(newDisplayName);
            moml.append("\"/>");
        }
        moml.append("</");
        moml.append(type);
        moml.append(">");
        
        MoMLChangeRequest request = new MoMLChangeRequest(this, container, moml.toString());
        request.addChangeListener(this);
        _changeRequestError = false;
        requestChange(request);
        executeChangeRequests();
        return !_changeRequestError;
        
    }
    
    /** Rename an input or output.
     *  
     *  @param oldName the old name
     *  @param newName the new name
     *  @param isInput if true, oldName refers to an input. 
     *  if false, oldName refers to an output.
     */
    private void _renameInputOrOutput(String oldName, String newName, boolean isInput) {

        //System.out.println("rename input/output " + oldName + " to " + newName);
        boolean renamedParameter = renameParameter(oldName, newName);
        
        if(renamedParameter) {
        	
        	// set the display name if we're renaming an output
        	String newDisplayName = null;
        	if(!isInput) {
        		newDisplayName = newName;
        	}
        	
        	boolean renamedPort = _rename("port", oldName, newName, newDisplayName, this);
        	
        	// if we failed to rename the port, rename the parameter back
        	// to the original name
        	if(!renamedPort) {
        		renameParameter(newName, oldName);
        	}
        }
    }

    /** Restore the command line without changing them. */
    private void _restoreCommandLines() {
    	
        for(Refinement refinement : entityList(Refinement.class)) {
            Parameter commandLineParameter = (Parameter) refinement.getAttribute(COMMAND_LINE_NAME);
            // see if the command line exists in this refinement
            if(commandLineParameter != null) {
                
                // get the expression saved before the rename
                String expression = _refinementCommandLines.get(refinement);
                                
                commandLineParameter.setExpression(expression);                
            }
        }        
    }
    
    /** Save command line parameters in each refinement and clear them. This must be done
     *  before a rename, since something is currently broken in MoMLParser for renames. 
     */
    private void _saveAndClearCommandLines() {

        _refinementCommandLines.clear();
        
        for(Refinement refinement : entityList(Refinement.class)) {
            Parameter commandLineParameter = (Parameter) refinement.getAttribute(COMMAND_LINE_NAME);
            // see if the command line exists in this refinement
            if(commandLineParameter != null) {
                
                // save the expression
                String expression = commandLineParameter.getExpression();
                _refinementCommandLines.put(refinement, expression);
                
                // clear the expression
                commandLineParameter.setExpression(" ");
            }
        }
    }
    
    /** Set the IOType parameter for a port. */
    private static void _setPortIOTypeParameter(Port port, IOType type) 
    		throws IllegalActionException, NameDuplicationException {
        
        Parameter typeParameter = (Parameter) port.getAttribute(IO_TYPE_NAME);
        if(typeParameter == null) {
            typeParameter = new StringParameter(port, IO_TYPE_NAME);
        }
        typeParameter.setExpression(type.toString());
    }

    /** Show or hide the mirrored ports connected to a port contained
     *  by this actor based on the IOType.
     */
    private void _showOrHideInsidePort(MultiCompositePort port) 
        throws NameDuplicationException, IllegalActionException {
        
        // only show or hide output ports
        if(port.isOutput()) {
            
            // get the type
            IOType type = getPortIOType(port);
            
            // hide the inside port
            
            // NOTE: do not use insideSourcePortList() since it does not return transparent ports.
            //List<IOPort> insidePorts = port.insideSourcePortList();
            List<IOPort> insidePorts = port.insidePortList();
            
            //System.out.println("checking inside ports for " + port.getName() + ":");
            
            for(IOPort insidePort : insidePorts) {
                
                //System.out.println("    " + insidePort.getFullName());
                
                switch(type) {
                case File:
                    new SingletonAttribute(insidePort, "_hideInside");
                    //System.out.println("        hiding inside file port " + port.getName());
                    break;
                case Data:
                    Attribute attribute = insidePort.getAttribute("_hideInside");
                    if(attribute != null) {
                        attribute.setContainer(null);
                    }
                    //System.out.println("        showing inside data port " + port.getName());
                    break;
                }
            }
        }
    }

    /** Make sure that each port in this actor has a corresponding port in 
     *  each refinement and that they are linked together.
     */
    private void _updatePortsAndInsideLinks() throws IllegalActionException {
            
        for(Refinement refinement : entityList(Refinement.class)) {
        
            // add ports present in the container, but not in the refinement
            Set<Port> portsToMirror = new HashSet<Port>();
            for(Object containerPortObject : portList()) {
                if(containerPortObject != control.getPort()) {
                    final Port containerPort = (Port) containerPortObject;
                    final String portName = containerPort.getName();
                    
                    // see if the refinement has the port
                    final Port refinementPort = refinement.getPort(portName);
                    if(refinementPort == null) {
                        //System.out.println("mirroring port found in container " + containerPort);
                        portsToMirror.add(containerPort);
                    } else {
    
                        // see if the container and refinement ports are linked
                        final String relationName = portName + "Relation";
                        Relation relation = getRelation(relationName);
                        if (relation == null) {
                            try {
                                relation = newRelation(relationName);
                                //System.out.println("creating relation " + relationName);
                            } catch (NameDuplicationException e) {
                                throw new IllegalActionException(this, e, "Error linking port " + portName);
                            }
                        }
                            
                        if(!((ComponentPort) containerPort).isInsideLinked(relation)) {
                            containerPort.link(relation);
                            //System.out.println("linking " + relationName + " to " + containerPort);
                        }
                        if(!refinementPort.isLinked(relation)) {
                            refinementPort.link(relation);
                            //System.out.println("linking " + relationName + " to " + refinementPort);
                        }
                    }
                }
            }
            
            try {
                MultiCompositeActor.mirrorContainerPortsInRefinement(refinement, portsToMirror);
            } catch (NameDuplicationException e) {
                throw new IllegalActionException(this, e, "Error adding ports to choice.");
            }
                
            // add ports present in the refinement, but not in the container, unless
            // they are the default input/output ports.
            portsToMirror.clear();
            portsToMirror.addAll(refinement.portList());
            for(Port port : portsToMirror) {
            	final String portName = port.getName();
                if(port != control.getPort() && getPort(portName) == null &&
                		!portName.equals(DEFAULT_INPUT_FILE_NAME) &&
                		!portName.equals(DEFAULT_INPUT_DIR_NAME) &&
                		!portName.equals(DEFAULT_OUTPUT_FILE_NAME) &&
                		!portName.equals(DEFAULT_OUTPUT_DIR_NAME)) {
                    try {
                        //System.out.println("mirroring port found in template " + port);
                        IOPort containerPort = (IOPort) newPort(portName);
                        if(((IOPort) port).isInput()) {
                            containerPort.setInput(true);
                        } else {
                            containerPort.setOutput(true);
                        }
                        new SingletonAttribute(port, "_showName");
                    } catch (NameDuplicationException e) {
                        throw new IllegalActionException(this, e, "Error adding ports to container.");
                    }
                }
            }
        }
        
        // show or hide all the inside ports
        for(Object port : portList()) {
            try {
                if(port != control.getPort()) {
                    _showOrHideInsidePort((MultiCompositePort) port);
                }
            } catch (NameDuplicationException e) {
                throw new IllegalActionException(this, e, "Error show/hiding inside port.");
            }
        }

    }

    
    /** If true, make sure last modification time for each output file
     *  increases after executing the template.
     */
    private boolean _checkOutputTimestampVal = true;
    
    /** Directory containing templates. */
    private File _templateDir;
    
    private EditableChoiceStyle _choiceStyle;

    /** The default command line used for new refinements. */
    private String _commandLineArguments = "$additionalOptions";
    
    /** The template name used to create an empty refinement. */
    private final static String EMPTY_TEMPLATE_NAME = "Blank";
    
    /** The name of the attribute contained in input/output File ports.
     *  The value of this attribute is the IOType.
     */
    private final static String IO_TYPE_NAME = "_ioType";
    
    /** A map to save all the refinement command lines before a rename occurs. */
    private Map<Refinement, String> _refinementCommandLines = new HashMap<Refinement, String>();

    /** If true, an error occurred during a change request originating in this actor. */
    private boolean _changeRequestError = false;

}
