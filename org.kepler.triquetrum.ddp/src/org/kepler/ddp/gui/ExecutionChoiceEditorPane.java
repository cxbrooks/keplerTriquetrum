/* A pane to configure an execution choice actor.
 * 
 * Copyright (c) 2012 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2013-07-18 17:06:40 -0700 (Thu, 18 Jul 2013) $' 
 * '$Revision: 32229 $'
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
package org.kepler.ddp.gui;

import java.awt.Color;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;

import org.kepler.ddp.actor.ExecutionChoice;

import ptolemy.actor.gui.EditorPaneFactory;
import ptolemy.actor.gui.PtolemyQuery;
import ptolemy.actor.lib.hoc.Refinement;
import ptolemy.data.expr.Parameter;
import ptolemy.data.expr.StringParameter;
import ptolemy.gui.Query;
import ptolemy.kernel.util.Attribute;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import ptolemy.kernel.util.NamedObj;
import ptolemy.kernel.util.Settable;
import ptolemy.util.MessageHandler;
import ptolemy.vergil.basic.KeplerDocumentationAttribute;

/** A pane to configure an execution choice actor.
 * 
 *  @author Daniel Crawl
 *  @version $Id: ExecutionChoiceEditorPane.java 32229 2013-07-19 00:06:40Z crawl $
 */
public class ExecutionChoiceEditorPane extends JPanel {

    public ExecutionChoiceEditorPane(ExecutionChoice choice) {
        
        try {
            
        	KeplerDocumentationAttribute docAttribute = null;
        	List<KeplerDocumentationAttribute> docList = choice.attributeList(KeplerDocumentationAttribute.class);
        	if(!docList.isEmpty()) {
        		docAttribute = docList.get(0);
        		// see if docAttribute has been initialized
        		Hashtable<?,?> hashTable = docAttribute.getPortHash();
        		if(hashTable.isEmpty()) {
        			docAttribute.createInstanceFromExisting(docAttribute);
        		}
        	}
        	
            PtolemyQuery query = new PtolemyQuery(choice);
            
            // add program and arguments
            query.addSeparator();
            query.addStyledEntry(choice.program);
    
            // add inputs
            query.addSeparator();
            query.addText("Input File Parameters", Color.BLACK, SwingConstants.LEFT);
            List<String> names = choice.getInputNames(false);
            if(names.isEmpty()) {
                query.addText("None", Color.BLACK, SwingConstants.LEFT);
            } else {
                for(String name : names) {
                	
                	_addDocumentation(name, query, docAttribute);
                	
                    StringParameter parameter = (StringParameter) choice.getAttribute(name);
                    String argument = choice.getArgument(name);
                    if(argument != null && !argument.trim().isEmpty()) {
                        query.addTextArea(name,
                                name + " (" + argument + ")",
                                parameter.getExpression(),
                                PtolemyQuery.preferredBackgroundColor(parameter),
                                PtolemyQuery.preferredForegroundColor(parameter),
                                1, Query.DEFAULT_ENTRY_WIDTH);
                        query.attachParameter(parameter, name);
                    } else {
                        query.addStyledEntry(parameter);
                    }
                    
                }
            }
    
            // add outputs
            query.addSeparator();
            query.addText("Output File Parameters", Color.BLACK, SwingConstants.LEFT);
            query.addStyledEntry(choice.checkOutputTimestamp);
            names = choice.getOutputNames(false);
            if(names.isEmpty()) {
                query.addText("None", Color.BLACK, SwingConstants.LEFT);
            } else {
                for(String name : names) {

                	boolean addedDocs = _addDocumentation(name, query, docAttribute);

                    StringParameter parameter = (StringParameter) choice.getAttribute(name);
                    String argument = choice.getArgument(name);
                    if(argument != null && !argument.trim().isEmpty()) {
                        query.addTextArea(name,
                                name + " (" + argument + ")",
                                parameter.getExpression(),
                                PtolemyQuery.preferredBackgroundColor(parameter),
                                PtolemyQuery.preferredForegroundColor(parameter),
                                1, Query.DEFAULT_ENTRY_WIDTH);
                        query.attachParameter(parameter, name);
                    } else {
                        query.addStyledEntry(parameter);
                    }
                    
                    if(addedDocs) {
                    	//query.addText("", Color.BLACK, SwingConstants.LEFT);
                    	//query.addText("", Color.BLACK, SwingConstants.LEFT);
                    }
                }
            }
            
            // add additional parameters
            query.addSeparator();
            query.addText("Parameters", Color.BLACK, SwingConstants.LEFT);
    
            // add additional parameters
            query.addStyledEntry(choice.additionalOptions);
            
            final List<String> parameterNames = choice.getParameterNames();        
            final String[] namesArray = parameterNames.toArray(new String[parameterNames.size()]);
            Arrays.sort(namesArray);
            for(String name : namesArray) {
            	
            	_addDocumentation(name, query, docAttribute);

                Parameter parameter = (Parameter) choice.getAttribute(name);
                String argument = choice.getArgument(name);
                if(argument != null && !argument.trim().isEmpty()) {
                    query.addTextArea(name,
                            name + " (" + argument + ")",
                            parameter.getExpression(),
                            PtolemyQuery.preferredBackgroundColor(parameter),
                            PtolemyQuery.preferredForegroundColor(parameter),
                            1, Query.DEFAULT_ENTRY_WIDTH);
                    query.attachParameter(parameter, name);
                } else {
                    query.addStyledEntry(parameter);
                }
            }
                    
            // add execution choice combo box
            query.addSeparator();
            
            // make sure at least one choice has been loaded
            /*
            if(choice.entityList(Refinement.class).isEmpty()) {
                try {
                    choice.newExecutionChoice(ExecutionChoice.DEFAULT_TEMPLATE_NAME,
                        ExecutionChoice.DEFAULT_TEMPLATE_NAME);
                } catch (IllegalActionException e) {
                    MessageHandler.error("Error adding default execution choice.", e);
                }
            }
            */
            
            choice.updateExecutionChoices();
            _addDocumentation(choice.control.getName(), query, docAttribute);
            query.addStyledEntry(choice.control);
    
                    
            JTabbedPane tabbedPane = new JTabbedPane();        
            tabbedPane.addTab("Shared Options", query);
            
            _addTabsForRefinements(tabbedPane, choice);
            
            add(tabbedPane);
            
        } catch(IllegalActionException e) {
            MessageHandler.error("Error creating pane.", e);
        }
    }
    
    /** Add documentation to the configuration dialog for a specific field. 
     *  @param name the name of the field
     *  @param query the configuration dialog
     *  @param docAttribute the attribute containing the port and parameter documentation 
     *  @return True if the documentation for the field is found and non-empty, otherwise false.
     */
    private boolean _addDocumentation(String name, Query query, KeplerDocumentationAttribute docAttribute) {
    	boolean retval = false;
    	
    	/* turned off until can do better alignment
        if(docAttribute != null) {
        	String docText = docAttribute.getProperty(name);
        	if(docText != null && !docText.trim().isEmpty()) {
        		query.addText(docText, Color.BLACK, SwingConstants.LEFT);
        		//query.addTextArea(name + "docs", docText, "", Color.BLACK, Color.BLACK, -1, -1);
        		
        		//query.addText("", Color.BLACK, SwingConstants.LEFT);
        		//query.addText("", Color.BLACK, SwingConstants.LEFT);
        		retval = true;
        	}
        }
        */
    	
        return retval;
	}

	/** Add a tab for each refinement. */
    private void _addTabsForRefinements(JTabbedPane tabbedPane, ExecutionChoice choice) {
    	
        for(String refinementName : choice.getExecutionChoiceNames()) {
            
            Refinement refinement = (Refinement) choice.getEntity(refinementName);
            final PtolemyQuery query = new PtolemyQuery(refinement);
            
            // get all the parameter names in this refinement
            final ArrayList<String> names = new ArrayList<String>();
            for(Parameter parameter : refinement.attributeList(Parameter.class)) {
                final String parameterName = parameter.getName();
                if(!parameterName.startsWith("_")) {
                    names.add(parameterName);
                    Attribute attribute = parameter.getAttribute("class");
                    if(attribute != null && (attribute instanceof Settable)) {
                        ((Settable)attribute).setVisibility(Settable.NONE);
                    }
                }
            }
            
            // see if there are any parameters
            if(names.isEmpty()) {
                query.addText("No Parameters", Color.BLACK, SwingConstants.LEFT);
            } else {
                // sort names alphabetically and add to query
                final String[] namesArray = names.toArray(new String[names.size()]);
                Arrays.sort(namesArray);
                for(String parameterName : namesArray) {
                    
                    try {
                        Settable parameter = (Settable) refinement.getAttribute(parameterName);
                        String argument = choice.getArgument(refinement, parameterName);
                        if(argument != null) {
                            query.addTextArea(parameterName,
                                    parameterName + " (" + argument + ")",
                                    parameter.getExpression(),
                                    PtolemyQuery.preferredBackgroundColor(parameter),
                                    PtolemyQuery.preferredForegroundColor(parameter),
                                    1, Query.DEFAULT_ENTRY_WIDTH);
                            query.attachParameter(parameter, parameterName);
                        } else {
                            query.addStyledEntry(parameter);
                        }
                    } catch(IllegalActionException e) {
                        MessageHandler.error("Error accessing argument for " +
                                parameterName + " in " + refinementName, e);
                    }
                }
            }
                        
            // add query to tab
            tabbedPane.addTab(refinement.getDisplayName(), query);
        }
    }
    
    /** An editor pane factory for execution choice. */
    public static class Factory extends EditorPaneFactory {

        public Factory(NamedObj container, String name)
                throws IllegalActionException, NameDuplicationException {
            super(container, name);
            if(container instanceof ExecutionChoice) {
                _target = (ExecutionChoice)container;
            }
        }
        
        @Override
        public Component createEditorPane() {
            if(_target!= null) {
                return new ExecutionChoiceEditorPane(_target);
            }
            return null;
        }
        
        private ExecutionChoice _target;
    }
}
