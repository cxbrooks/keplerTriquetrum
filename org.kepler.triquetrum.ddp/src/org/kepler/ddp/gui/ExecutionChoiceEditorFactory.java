///* An editor factory for execution choice.
// * 
// * Copyright (c) 2012 The Regents of the University of California.
// * All rights reserved.
// *
// * '$Author: crawl $'
// * '$Date: 2015-09-04 13:53:26 -0700 (Fri, 04 Sep 2015) $' 
// * '$Revision: 33870 $'
// * 
// * Permission is hereby granted, without written agreement and without
// * license or royalty fees, to use, copy, modify, and distribute this
// * software and its documentation for any purpose, provided that the above
// * copyright notice and the following two paragraphs appear in all copies
// * of this software.
// *
// * IN NO EVENT SHALL THE UNIVERSITY OF CALIFORNIA BE LIABLE TO ANY PARTY
// * FOR DIRECT, INDIRECT, SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES
// * ARISING OUT OF THE USE OF THIS SOFTWARE AND ITS DOCUMENTATION, EVEN IF
// * THE UNIVERSITY OF CALIFORNIA HAS BEEN ADVISED OF THE POSSIBILITY OF
// * SUCH DAMAGE.
// *
// * THE UNIVERSITY OF CALIFORNIA SPECIFICALLY DISCLAIMS ANY WARRANTIES,
// * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
// * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE. THE SOFTWARE
// * PROVIDED HEREUNDER IS ON AN "AS IS" BASIS, AND THE UNIVERSITY OF
// * CALIFORNIA HAS NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES,
// * ENHANCEMENTS, OR MODIFICATIONS.
// *
// */
//package org.kepler.ddp.gui;
//
//import java.awt.Color;
//import java.awt.Frame;
//import java.awt.GridBagConstraints;
//import java.awt.GridBagLayout;
//import java.awt.event.ActionEvent;
//import java.awt.event.ActionListener;
//import java.io.File;
//import java.util.Arrays;
//import java.util.HashMap;
//import java.util.HashSet;
//import java.util.LinkedList;
//import java.util.List;
//import java.util.Map;
//import java.util.Set;
//
//import javax.swing.ButtonGroup;
//import javax.swing.JComboBox;
//import javax.swing.JFileChooser;
//import javax.swing.JLabel;
//import javax.swing.JPanel;
//import javax.swing.JRadioButton;
//import javax.swing.JTabbedPane;
//import javax.swing.JTextField;
//import javax.swing.SwingConstants;
//
//import org.apache.commons.io.FilenameUtils;
//import org.kepler.ddp.actor.ExecutionChoice;
//import org.kepler.gui.KeplerGraphFrame;
//import org.kepler.gui.ModelToFrameManager;
//import org.kepler.gui.frame.TabbedKeplerGraphFrame;
//
//import ptolemy.actor.TypedIOPort;
//import ptolemy.actor.gui.Configurer;
//import ptolemy.actor.gui.EditorFactory;
//import ptolemy.actor.lib.hoc.Refinement;
//import ptolemy.data.expr.Parameter;
//import ptolemy.gui.ComponentDialog;
//import ptolemy.gui.ExtensionFilenameFilter;
//import ptolemy.gui.JFileChooserBugFix;
//import ptolemy.gui.PtFileChooser;
//import ptolemy.gui.PtGUIUtilities;
//import ptolemy.gui.Query;
//import ptolemy.kernel.ComponentEntity;
//import ptolemy.kernel.CompositeEntity;
//import ptolemy.kernel.Port;
//import ptolemy.kernel.util.Attribute;
//import ptolemy.kernel.util.IllegalActionException;
//import ptolemy.kernel.util.NameDuplicationException;
//import ptolemy.kernel.util.NamedObj;
//import ptolemy.moml.MoMLChangeRequest;
//import ptolemy.util.MessageHandler;
//
///** An editor factory to create an editor for ExecutionChoice. An
// *  ExecutioChoiceEditorPane is used to configure the actor. This
// *  class handles button presses and can display panes for adding,
// *  removing, and renaming components, and exporting the execution
// *  choices.
// * 
// *  @author Daniel Crawl
// *  @version $Id: ExecutionChoiceEditorFactory.java 33870 2015-09-04 20:53:26Z crawl $
// */
//public class ExecutionChoiceEditorFactory extends EditorFactory {
//
//    public ExecutionChoiceEditorFactory(NamedObj container, String name)
//            throws IllegalActionException, NameDuplicationException {
//        super(container, name);            
//    }
//
//    @Override
//    public void createEditor(NamedObj object, Frame parent) {
//
//        if(!(object instanceof ExecutionChoice)) {
//            MessageHandler.error("Object to edit must be ExecutionChoice.");
//            return;
//        }
//        
//        ExecutionChoice choice = (ExecutionChoice)object;
//        
//        try {
//            choice.addDefaults();
//        } catch (Exception e) {
//            MessageHandler.error("Error adding default input/outputs.", e);
//        }
//        
//        boolean done = false;
//        
//        Set<String> addedInputs = new HashSet<String>();
//        Set<String> addedOutputs = new HashSet<String>();
//        Set<String> addedParameters = new HashSet<String>();
//        Set<String> addedChoices = new HashSet<String>();
//        
//        // a set of Configure objects for the execution choice actor and
//        // each sub-workflow. if the user hits Cancel, escape, or the
//        // close window button (top-left or top-right of dialog), the
//        // Configure objects are used to restore the parameter values in
//        // the actor and for each sub-workflow.
//        Set<Configurer> configurers = new HashSet<Configurer>();
//
//        while(!done) {
//            final Configurer actorConfigurer = new Configurer(object);
//
//            configurers.clear();
//            configurers.add(actorConfigurer);
//            
//            for(Refinement refinement : choice.entityList(Refinement.class)) {
//            	configurers.add(new Configurer(refinement));
//            }
//            
//            final ComponentDialog dialog = new ComponentDialog(parent, 
//                object.getFullName(), 
//                actorConfigurer,
//                new String[] {"Commit", "Types", "Rename", "Remove",
//                "Import", "Export", "Arguments", "Add", "Cancel"});
//            final String buttonPressed = dialog.buttonPressed();
//            
//            // if user pushed escape button, buttonPressed is empty.
//            if(buttonPressed.equals("Commit")) {
//                done = true;
//            } else if(buttonPressed.equals("Cancel")  || buttonPressed.isEmpty()) {
//            	
//            	// NOTE: Cancel only restores parameter values.
//            	
//            	for(Configurer configurer : configurers) {
//            		configurer.restore();
//            	}
//                
//                // remove all added items
//                /*
//                for(String addedInput : addedInputs) {
//                    try {
//                        choice.removeInput(addedInput);
//                    } catch (Exception e) {
//                        MessageHandler.error("Error removing input " + addedInput, e);
//                    }
//                }
//                for(String addedOutput : addedOutputs) {
//                    try {
//                        choice.removeOutput(addedOutput);
//                    } catch (Exception e) {
//                        MessageHandler.error("Error removing output " + addedOutput, e);
//                    }
//                }
//                for(String addedParameter : addedParameters) {
//                    try {
//                        // FIXME parameter may have been added to refinement
//                        choice.removeParameter(addedParameter);
//                    } catch (Exception e) {
//                        MessageHandler.error("Error removing parameter " + addedParameter, e);
//                    }
//                }
//                for(String addedChoice : addedChoices) {
//                    try {
//                        choice.removeExecutionChoice(addedChoice);
//                    } catch (Exception e) {
//                        MessageHandler.error("Error removing execution choice " + addedChoice, e);
//                    }
//                }
//                */
//                
//                // TODO restore all removed items
//                
//                // TODO restore all renamed items
//                
//                // TODO restore all argument changes
//                
//                // TODO remove parameters add to refinements
//                
//                // TODO restore parameters removed from refinements
//                
//                // TODO restore parameters renamed in refinements
//                
//                // repaint canvas to remove any added ports
//                //_repaintCanvas(choice);
//
//                done = true;
//                
//            } else if(buttonPressed.equals("Add")) {
//                boolean doneAdding = false;
//                String addStr = null;
//                String newName = null;
//                String typeStr = null;
//                String argumentStr = null;
//                String choiceStr = null;
//                while(!doneAdding) {
//                    final AddPane addPane = new AddPane(choice);
//                    addPane.setAdd(addStr);
//                    addPane.setTextName(newName);
//                    addPane.setType(typeStr);
//                    addPane.setArgument(argumentStr);
//                    addPane.setChoice(choiceStr);
//                    final ComponentDialog addDialog = new ComponentDialog(parent,
//                            buttonPressed, addPane,
//                            new String[] {"Add", "Add & Continue", "Cancel"});
//                    String pressed = addDialog.buttonPressed();
//                    if(pressed.equals("Add") || pressed.equals("Add & Continue")) {
//                        try {
//                            addStr = addPane.getAdd();
//                            newName = addPane.getTextName();
//                            typeStr = addPane.getType();
//                            argumentStr = addPane.getArgument();
//                            choiceStr = addPane.getChoice();
//                            
//                            boolean chooseNewName = false;
//                            
//                            if(newName.isEmpty()) {
//                                MessageHandler.error("Please specify a name.");
//                            } else {
//                                if(addStr.equals("Input")) {
//                                    if(choice.getInputNames(true).contains(newName)) {
//                                        chooseNewName = true;
//                                    } else { 
//                                        choice.newInput(newName,
//                                            ExecutionChoice.IOType.valueOf(typeStr),
//                                            argumentStr);
//                                        addedInputs.add(newName);
//                                    }
//                                } else if(addStr.equals("Output")) {
//                                    if(choice.getOutputNames(true).contains(newName)) {
//                                        chooseNewName = true;
//                                    } else {
//                                        choice.newOutput(newName,
//                                            ExecutionChoice.IOType.valueOf(typeStr),
//                                            argumentStr);
//                                        addedOutputs.add(newName);
//                                    }
//                                } else if(addStr.equals("Parameter")) {
//                                    // use getAttribute() instead of getParameterNames()
//                                    // since the latter does not include all the parameters
//                                    if(choice.getAttribute(newName) != null) {
//                                        chooseNewName = true;
//                                    } else {
//                                        choice.newParameter(newName, argumentStr, null, choiceStr,
//                                                ExecutionChoice.ParameterType.valueOf(typeStr));
//                                        addedParameters.add(newName);
//                                    }
//                                } else if(addStr.equals("Execution Choice")) {
//                                	if(choice.getEntity(newName) != null) {
//                                		chooseNewName = true;
//                                	} else {
//                                		choice.newExecutionChoice(typeStr, newName);
//                                		addedChoices.add(newName);
//                                		
//                                		// if the choices are open in a frame, add a new tab for the new choice
//                                		KeplerGraphFrame frame = ModelToFrameManager.getInstance().getFrame(choice);
//                                		if(frame != null && (frame instanceof TabbedKeplerGraphFrame)) {
//                                		    ((TabbedKeplerGraphFrame)frame).
//                                		        addSubComposite((CompositeEntity)choice.getEntity(newName));
//                                		}
//                                	}
//                                }               
//                                
//                                if(chooseNewName) {
//                                    MessageHandler.error(addStr + " " + newName +
//                                            " is already used. Please choose a different name.");
//                                } else {
//                                    // execute an empty change request so that the new port
//                                    // appears (if we added a new port)
//                                    _repaintCanvas(choice);
//                                    
//                                    if(pressed.equals("Add")) {
//                                        doneAdding = true;
//                                    }
//                                }
//                            }
//                        } catch(IllegalActionException e) {
//                            MessageHandler.error("Error", e);
//                        } catch(NameDuplicationException e) {
//                            MessageHandler.error(addPane.getTextName() +
//                                    " already exists. Please choose a different name.", e);
//                        }
//                    } else if(addDialog.buttonPressed().equals("Cancel") ||
//                            addDialog.buttonPressed().isEmpty()) {
//                        doneAdding = true;
//                    }
//                }
//            } else if(buttonPressed.equals("Rename")) {
//                try {
//                    boolean doneRenaming = false;
//                    while(!doneRenaming) {
//                        RenamePane renamePane;
//                        try {
//                            renamePane = new RenamePane(choice);
//                        } catch (IllegalActionException e) {
//                            MessageHandler.error("Error creating Rename dialog.", e);
//                            break;
//                        }
//                        final ComponentDialog renameDialog = new ComponentDialog(parent,
//                                buttonPressed, renamePane);
//                        if(renameDialog.buttonPressed().equals("OK")) {
//                            Set<RenameInfo> renames = renamePane.getRenames();
//                            
//                            // first check all the renames to make sure that
//                            // there are no name conflicts.
//                            String newName = null;
//                            String type = null;
//                            NamedObj container = null;
//                            boolean chooseNewName = false;
//                            for(RenameInfo rename : renames) {
//                                type = rename._type;
//                                newName = rename._newName;
//                                container = rename._container;
//                                if(type.equals("Execution Choice")) {
//                                    if(choice.getEntity(newName) != null) {
//                                        chooseNewName = true;
//                                        break;
//                                    }
//                                } else if(type.equals("Input")) {
//                                    // TODO what if output name exists?
//                                    if(choice.getInputNames(true).contains(newName)) {
//                                        chooseNewName = true;
//                                        break;
//                                    }
//                                } else if(type.equals("Output")) {
//                                    if(choice.getOutputNames(true).contains(newName)) {
//                                        chooseNewName = true;
//                                        break;
//                                    }
//                                } else if(type.equals("Parameter")) {
//                                    if(container.getAttribute(newName) != null) {
//                                        chooseNewName = true;
//                                        break;
//                                    }
//                                }
//                            }
//                            
//                            if(chooseNewName) {
//                                MessageHandler.error(type + " " + newName +
//                                        " is already used. Please choose a different name.");
//                            } else {
//                                
//                                // perform each rename
//                                for(RenameInfo renameInfo : renamePane.getRenames()) {
//                                    //try {
//                                        if(renameInfo._type.equals("Execution Choice")) {
//                                            choice.renameExecutionChoice(renameInfo._oldName,
//                                                    renameInfo._newName);
//                                            
//                                            // if the choices are open in a frame, remove the tab for the choice
//                                            // that was removed.
//                                            KeplerGraphFrame frame = ModelToFrameManager.getInstance().getFrame(choice);
//                                            if(frame != null && (frame instanceof TabbedKeplerGraphFrame)) {
//                                                ((TabbedKeplerGraphFrame)frame).updateTabsForComposite(choice);
//                                            }
//
//                                        } else if(renameInfo._type.equals("Input")) {
//                                            choice.renameInput(renameInfo._oldName,
//                                                    renameInfo._newName);
//                                        } else if(renameInfo._type.equals("Output")) {
//                                            choice.renameOutput(renameInfo._oldName,
//                                                    renameInfo._newName);
//                                        } else if(renameInfo._type.equals("Parameter")) {
//                                            choice.renameParameter(renameInfo._oldName,
//                                                    renameInfo._newName, renameInfo._container);
//                                        }
//                                    //} catch(IllegalActionException e) {
//                                        //MessageHandler.error("Error renaming " + renameInfo._oldName, e);
//                                    //}
//                                }
//                                // execute an empty change request so that the new port
//                                // names appear (if we changed any port names)
//                                _repaintCanvas(choice);
//                                doneRenaming = true;
//                            }
//                        } else if(renameDialog.buttonPressed().equals("Cancel") ||
//                                renameDialog.buttonPressed().isEmpty()) {
//                            doneRenaming = true;
//                        }
//                    }
//                    
//                } catch(Exception e) {
//                    MessageHandler.error("Error renaming.", e);
//                }
//            } else if(buttonPressed.equals("Remove")) {
//                boolean doneRemoving = false;
//                while(!doneRemoving) {
//                    RemovePane removePane = null;
//                    try {
//                        removePane = new RemovePane(choice);
//                    } catch (IllegalActionException e) {
//                        MessageHandler.error("Error creating Remove dialog.", e);
//                    }
//                    
//                    if(removePane != null) {
//                        final ComponentDialog removeDialog = new ComponentDialog(parent,
//                                buttonPressed, removePane,
//                                new String[] {"Remove", "Remove & Continue", "Cancel"});
//                        String pressed = removeDialog.buttonPressed();
//                        
//                        doneRemoving = true;
//                        if(pressed.equals("Remove & Continue")) {
//                            doneRemoving = false;
//                        }
//                        
//                        if(pressed.equals("Remove") || pressed.equals("Remove & Continue")) {
//                            String removeStr = removePane.getRemove();
//                            String name = removePane.getTextName();
//                            if(name != null) {
//                                boolean removed = false;
//                                try {
//                                    if(removeStr.equals("Input")) {
//                                        if(MessageHandler.yesNoQuestion("Remove input " + name + "?")) {
//                                            choice.removeInput(name);
//                                            removed = true;
//                                            addedInputs.remove(name);
//                                        }
//                                    } else if(removeStr.equals("Output")) {
//                                        if(MessageHandler.yesNoQuestion("Remove output " + name + "?")) {
//                                            choice.removeOutput(name);
//                                            removed = true;
//                                            addedOutputs.remove(name);
//                                        }
//                                    } else if(removeStr.equals("Parameter")) {
//                                        if(MessageHandler.yesNoQuestion("Remove parameter " + name + "?")) {
//                                            final NamedObj container = removePane.getRemoveContainer();
//                                            if(container == choice) {
//                                                choice.removeParameter(name);
//                                                removed = true;
//                                                addedParameters.remove(name);
//                                            } else {
//                                                Attribute parameter = container.getAttribute(name);
//                                                parameter.setContainer(null);
//                                                removed = true;
//                                                // TODO undo stuff
//                                            }   
//                                                
//                                        }
//                                    } else if(removeStr.equals("Execution Choice")) {
//                                        if(MessageHandler.yesNoQuestion("Remove execution choice " +
//                                                name + " and all of its components?")) {
//                                            choice.removeExecutionChoice(name);
//                                            removed = true;
//                                            addedChoices.remove(name);
//                                            
//                                            // if the choices are open in a frame, remove the tab for the choice
//                                            // that was removed.
//                                            KeplerGraphFrame frame = ModelToFrameManager.getInstance().getFrame(choice);
//                                            if(frame != null && (frame instanceof TabbedKeplerGraphFrame)) {
//                                                ((TabbedKeplerGraphFrame)frame).updateTabsForComposite(choice);
//                                            }
//                                        }
//                                    }
//        
//                                    if(removed) {
//                                        // execute an empty change request so that removed port
//                                        // no longer shows on the canvas
//                                        _repaintCanvas(choice);
//                                    }                      
//                                } catch(Exception e) {
//                                    MessageHandler.error("Error removing " + name, e);
//                                }
//                            }
//                        }
//                    }
//                }
//            } else if(buttonPressed.equals("Export")) {
//                final ExportPane exportPane = new ExportPane(choice);
//                final ComponentDialog exportDialog = new ComponentDialog(parent,
//                        buttonPressed, exportPane);
//                if(exportDialog.buttonPressed().equals("OK")) {
//                    String name = exportPane.getChoiceName();
//                    if(name != null) {
//                        
//                        // Avoid white boxes in file chooser, see
//                        // http://bugzilla.ecoinformatics.org/show_bug.cgi?id=3801
//                        JFileChooserBugFix jFileChooserBugFix = new JFileChooserBugFix();
//                        Color background = null;
//                        PtFileChooser chooser = null;
//
//                        try {
//                            background = jFileChooserBugFix.saveBackground();
//                            chooser = new PtFileChooser(parent, "Export",
//                                JFileChooser.SAVE_DIALOG);
//
//                            String defaultFileName = name.replaceAll("\\s+", "") + ".xml";
//                            chooser.setSelectedFile(new File(defaultFileName));
//                            
//                            int returnVal = chooser.showDialog(parent, "Export");
//                            if (returnVal == JFileChooser.APPROVE_OPTION) {
//                                // process the given file
//                                File saveFile = chooser.getSelectedFile();
//                                
//                                if(saveFile.exists() && !PtGUIUtilities.useFileDialog()) {
//                                    if (!MessageHandler.yesNoQuestion("Overwrite \""
//                                            + saveFile.getName() + "\"?")) {
//                                        saveFile = null;
//                                    }
//                                }
//                                
//                                try {
//									choice.exportExecutionChoice(saveFile, name);
//								} catch (Exception e) {
//									MessageHandler.error("Error exporting execution choice.", e);
//								}
//                                
//                            }                   
//
//                        } finally {
//                            jFileChooserBugFix.restoreBackground(background);
//                        }
//
//                    }
//                }
//            } else if(buttonPressed.equals("Import")) {
//            	
//                // Avoid white boxes in file chooser, see
//                // http://bugzilla.ecoinformatics.org/show_bug.cgi?id=3801
//                JFileChooserBugFix jFileChooserBugFix = new JFileChooserBugFix();
//                Color background = null;
//                PtFileChooser chooser = null;
//
//                try {
//                    background = jFileChooserBugFix.saveBackground();
//                    chooser = new PtFileChooser(parent, "Import",
//                        JFileChooser.OPEN_DIALOG);
//                    chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
//                    chooser.addChoosableFileFilter(new ExtensionFilenameFilter("xml"));
//                    
//                    
//                    int returnVal = chooser.showDialog(parent, "Import");
//                    if(returnVal == JFileChooser.APPROVE_OPTION) {
//                    	
//                    	// load the file as a new execution choice
//                        File loadFile = chooser.getSelectedFile();
//                        
//                        // ask the user for the name of the new execution choice
//
//                    	String refinementName = null;
//                        boolean doneAskingName = false;
//                        
//                        while(!doneAskingName) {
//                        
//	                        // set the default name to be the file name
//	                        final ImportPane importPane = 
//	                        		new ImportPane(FilenameUtils.removeExtension(loadFile.getName()));
//	                        final ComponentDialog importDialog = new ComponentDialog(parent,
//	                                buttonPressed, importPane);
//	                        if(importDialog.buttonPressed().equals("OK")) {
//	                        	
//	                        	// see if the name has already been chosen
//	                        	refinementName = importPane.getChoiceName();
//	                        	if(choice.getEntity(refinementName) != null) {
//	                        		MessageHandler.error("The name " + refinementName +
//	                        				" is already used. Please choose a different name.");
//	                        	} else {
//	                        		doneAskingName = true;
//	                        	}
//	        
//	                        } else if(importDialog.buttonPressed().equals("Cancel")) {
//	                        	refinementName = null;
//	                        	doneAskingName = true;
//	                        }
//                        }
//                        
//                        // load the execution choice if a unique name was selected.
//                        if(refinementName != null) {
//	                        try {
//	                        	choice.newExecutionChoice(loadFile, refinementName);
//	                        } catch(IllegalActionException e) {
//	                        	MessageHandler.error("Error importing execution choice.", e);
//	                        }
//                        }
//                        
//                    }
//                    
//                } finally {
//                    jFileChooserBugFix.restoreBackground(background);
//                }
//
//            } else if(buttonPressed.equals("Arguments")) {
//                ArgumentsPane argumentsPane = null;
//                try {
//                    argumentsPane = new ArgumentsPane(choice);
//                } catch (IllegalActionException e) {
//                    MessageHandler.error("Error creating Arguments dialog.", e);
//                }
//                
//                if(argumentsPane != null) {
//                    final ComponentDialog argumentsDialog = new ComponentDialog(parent,
//                            buttonPressed, argumentsPane);
//                    if(argumentsDialog.buttonPressed().equals("OK")) {
//                        try {
//                            argumentsPane.applyChanges();
//                        } catch(Exception e) {
//                            MessageHandler.error("Error changing arguments.", e);
//                        }
//                    }
//                }
//            } else if(buttonPressed.equals("Types")) {
//                TypesPane typesPane = null;
//                try {
//                    typesPane = new TypesPane(choice);
//                } catch (IllegalActionException e) {
//                    MessageHandler.error("Error creating Types dialog.", e);
//                }
//                
//                if(typesPane != null) {
//                    final ComponentDialog typesDialog = new ComponentDialog(parent,
//                            buttonPressed, typesPane);
//                    if(typesDialog.buttonPressed().equals("OK")) {
//                        try {
//                            typesPane.applyChanges();
//                        } catch(Exception e) {
//                            MessageHandler.error("Error changing types.", e);
//                        }
//                    }
//                }                
//            }
//        }        
//    }
//    
//    /** Repaint the canvas by executing an empty change request. */
//    private void _repaintCanvas(ExecutionChoice choice) {
//        MoMLChangeRequest change = 
//                new MoMLChangeRequest(this, this, "<group></group>");
//        change.setPersistent(false);
//        choice.requestChange(change);        
//    }
//
//    /** A utility pane for adding/removing. */
//    private static abstract class UtilPane extends JPanel implements ActionListener {
//        
//        public UtilPane(ExecutionChoice choice) {
//            _choice = choice;
//        }
//              
//        /** Get the type of the component added, e.g., if the component is
//         *  an execution choice, the type is the name of one of the templates.
//         */
//        public String getType() {
//            Object item = _typeComboBox.getSelectedItem();
//            if(item == null) {
//                return "";
//            }
//            // return toString() since item may not be a String, 
//            // e.g., it could be an ExecutionChoice.IOType
//            return item.toString();
//        }
//
//        /** Set the type of the component to be added. */
//        public void setType(String typeStr) {
//            if(typeStr != null) {
//                _typeComboBox.setSelectedItem(typeStr);
//            }
//        }
//        
//        /** Get the selected action component type,
//         *  e.g, input, output, parameter, execution choice.
//         */
//        protected String _getActionButtonSelected() {
//            for(JRadioButton button : _buttonMap.values()) {
//                if(button.isSelected()) {
//                    return button.getText();
//                }
//            }
//            return null;
//        }
//        
//        /** Set the selected action component. */
//        public void setActionButtonSelected(String buttonText) {
//            if(buttonText != null) {
//                for(JRadioButton button : _buttonMap.values()) {
//                    if(button.getText().equals(buttonText)) {
//                        button.setSelected(true);
//                    } else {
//                        button.setSelected(false);
//                    }
//                }
//            }
//        }        
//
//        /** Layout the top part of the pane with the radio buttons. */
//        protected void _doTopLayout(String actionStr, GridBagConstraints c) {
//            
//            c.fill = GridBagConstraints.HORIZONTAL;
//            c.anchor = GridBagConstraints.LINE_START;
//
//            c.gridx = 0;
//            c.gridy = 0;
//            add(new JLabel(actionStr + ":"), c);
//            
//            final ButtonGroup buttonGroup = new ButtonGroup();
//            JRadioButton inputButton = new JRadioButton("Input");
//            inputButton.setSelected(true);
//            inputButton.addActionListener(this);
//            buttonGroup.add(inputButton);
//            _buttonMap.put("Input", inputButton);
//            c.gridx = 1;
//            c.gridy++;
//            add(inputButton, c);
//            
//            JRadioButton outputButton = new JRadioButton("Output");
//            outputButton.addActionListener(this);
//            buttonGroup.add(outputButton);
//            _buttonMap.put("Output", outputButton);
//            c.gridy++;
//            add(outputButton, c);
//            
//            JRadioButton parameterButton = new JRadioButton("Parameter");
//            parameterButton.addActionListener(this);
//            buttonGroup.add(parameterButton);
//            _buttonMap.put("Parameter", parameterButton);
//            c.gridy++;
//            add(parameterButton, c);
//            
//            JRadioButton choiceButton = new JRadioButton("Execution Choice");
//            choiceButton.addActionListener(this);
//            buttonGroup.add(choiceButton);
//            _buttonMap.put("Execution Choice", choiceButton);
//            c.gridy++;
//            add(choiceButton, c);
//            
//            c.gridx = 0;
//            c.gridy++;
//            add(new JLabel("Name:"), c);            
//        }
//        
//        protected Map<String,JRadioButton> _buttonMap = new HashMap<String,JRadioButton>();
//        protected JComboBox<Object> _typeComboBox;
//        protected ExecutionChoice _choice;
//
//        /** The label for choice entry. */
//        protected JLabel _choiceLabel;
//        
//        /** The label for the type combobox. */
//        protected JLabel _typeLabel;
//        
//        /** The combobox to hold the names of execution choices. */
//        protected JComboBox<String> _choiceComboBox;
//
//    }
//    
//    /** A pane to add an input, output, or execution choice. */
//    private static class AddPane extends UtilPane {
//        
//        public AddPane(ExecutionChoice choice) {
//            
//            super(choice);
//            
//            GridBagLayout layout = new GridBagLayout();
//            setLayout(layout);
//                        
//            GridBagConstraints c = new GridBagConstraints();
//
//            _doTopLayout("Add", c);
//
//            _nameTextField = new JTextField("", 20);
//            c.gridx = 1;
//            add(_nameTextField, c);
//
//            c.gridx = 0;
//            c.gridy++;
//            _argumentLabel = new JLabel(ExecutionChoice.ARGUMENT_NAME + ":");
//            add(_argumentLabel, c);
//            
//            _argumentTextField = new JTextField("", 20);
//            c.gridx = 1;
//            add(_argumentTextField, c);
//            
//            c.gridx = 0;
//            c.gridy++;
//            _typeLabel = new JLabel("Type:");
//            add(_typeLabel, c);
//            
//            _typeComboBox = new JComboBox<Object>(ExecutionChoice.IOType.values());
//            _typeComboBox.addActionListener(this);
//            c.gridx = 1;
//            add(_typeComboBox, c);
//            
//            c.gridx = 0;
//            c.gridy++;
//            _choiceLabel = new JLabel("Choice:");
//            add(_choiceLabel, c);
//            _choiceLabel.setEnabled(false);
//            
//            _choiceComboBox = new JComboBox<String>();
//            _choiceComboBox.addItem(ExecutionChoice.ALL_CHOICES_NAME);
//        	for(String name : _choice.getExecutionChoiceNames()) {
//        		_choiceComboBox.addItem(name);
//        	}            
//        	_choiceComboBox.addActionListener(this);
//            c.gridx = 1;
//            add(_choiceComboBox, c);
//            _choiceComboBox.setEnabled(false);
//        }
//
//        @Override
//        public void actionPerformed(ActionEvent event) {
//            
//            Object source = event.getSource();
//            // see if the type of component to add was changed
//            if(source instanceof JRadioButton) {
//                final String buttonText = (String) ((JRadioButton)source).getText();
//                _updateTypeComboBox(buttonText);
//            }
//            
//            _updateEnabledFields();
//        }
//        
//        /** Get the component added, i.e., input, output, execution choice. */
//        public String getAdd() {
//            return _getActionButtonSelected();
//        }
//        
//        /** Set the component to be added. */
//        public void setAdd(String addStr) {
//            if(addStr != null) {
//                setActionButtonSelected(addStr);
//                _updateTypeComboBox(addStr);
//                _updateEnabledFields();
//            }
//        }        
//       
//        /** Get the argument value. */
//        public String getArgument() {
//            return _argumentTextField.getText();
//        }
//
//        /** Set the argument. */
//        public void setArgument(String argumentStr) {
//            if(argumentStr != null) {
//                _argumentTextField.setText(argumentStr);
//            }
//        }
//
//        /** Get the name of the selected execution choice. */
//        public String getChoice() {
//            return (String) _choiceComboBox.getSelectedItem();
//        }
//
//        /** Set the name of the seleced execution choice. */
//        public void setChoice(String choiceStr) {
//            if(choiceStr != null) {
//                _choiceComboBox.setSelectedItem(choiceStr);
//            }            
//        }
//
//        /** Get the user-specified name of the component added. */
//        public String getTextName() {
//            return _nameTextField.getText();
//        }
//        
//        /** Set the name of the component to be added. */
//        public void setTextName(String newName) {
//            if(newName != null) {
//                _nameTextField.setText(newName);
//            }
//        }
//
//        /** Refill the type combo box based on the add type. */
//        protected void _updateTypeComboBox(String addStr) {
//            // remove all the types
//            _typeComboBox.removeAllItems();
//            // see if the add was set to input or output
//            if(addStr.equals("Input") || addStr.equals("Output")) {
//                for(ExecutionChoice.IOType type : ExecutionChoice.IOType.values()) {
//                    _typeComboBox.addItem(type);
//                }
//            } else if(addStr.equals("Parameter")) {
//                for(ExecutionChoice.ParameterType type : ExecutionChoice.ParameterType.values()) {
//                    _typeComboBox.addItem(type);
//                }    
//        	} else {
//        		// add was set to execution choice, so load the template
//        		// names in the type combobox
//        		try {
//        			for(String name : _choice.getTemplateNames()) {
//        				_typeComboBox.addItem(name);
//        			}
//        		} catch(IllegalActionException e) {
//        			MessageHandler.error("Error loading template names.", e);
//                }	
//           	}
//        }
//
//        /** Enable or disable fields based on the selected radio button. */
//        private void _updateEnabledFields() {
//        	
//        	// get the type of component to add
//        	String buttonText = _getActionButtonSelected();
//        	
//            if(buttonText.equals("Execution Choice") || getType().equals("Data")) {
//                _argumentLabel.setEnabled(false);
//                _argumentTextField.setEnabled(false);
//            } else {                
//                _argumentLabel.setEnabled(true);
//                _argumentTextField.setEnabled(true);                                        
//            }
//            
//            // the choice combobox selects which choice to add the parameter to
//            if(buttonText.equals("Parameter")) {
//                _choiceLabel.setEnabled(true);
//                _choiceComboBox.setEnabled(true);
//            } else {
//                _choiceLabel.setEnabled(false);
//                _choiceComboBox.setEnabled(false);
//            }            
//        }
//
//        /** The name of the component. */
//        private JTextField _nameTextField;
//
//        /** The (optional) argument used with the component. */
//        private JTextField _argumentTextField;
//        
//        /** The label for argument entry. */
//        private JLabel _argumentLabel;
//        
//    }
//
//    /** A pane to remove an input, output, or execution choice. */
//    private static class RemovePane extends UtilPane {
//        
//        public RemovePane(ExecutionChoice choice) throws IllegalActionException {
//
//            super(choice);
//            
//            GridBagLayout layout = new GridBagLayout();
//            setLayout(layout);
//                        
//            GridBagConstraints c = new GridBagConstraints();
//
//            _doTopLayout("Remove", c);
//
//            _nameComboBox = new JComboBox<String>();
//            List<String> inputNames = _choice.getInputNames(true);
//            for(String inputName : inputNames) {
//                _nameComboBox.addItem(inputName);
//            } 
//            if(_nameComboBox.getItemCount() == 0) {
//                _nameComboBox.addItem(NONE_TO_REMOVE);
//                _nameComboBox.setEnabled(false);
//            }
//            c.gridx = 1;
//            add(_nameComboBox, c);
//
//            c.gridx = 0;
//            c.gridy++;
//            _choiceLabel = new JLabel("Choice:");
//            add(_choiceLabel, c);
//            _choiceLabel.setEnabled(false);
//            
//            _choiceComboBox = new JComboBox<String>();
//            _choiceComboBox.addItem(ExecutionChoice.ALL_CHOICES_NAME);
//            for(String name : choice.getExecutionChoiceNames()) {
//                _choiceComboBox.addItem(name);
//            }
//            _choiceComboBox.addActionListener(this);
//            c.gridx = 1;
//            add(_choiceComboBox, c);
//            _choiceComboBox.setEnabled(false);            
//        }
//
//        @Override
//        public void actionPerformed(ActionEvent event) {
//            
//            Object source = event.getSource();
//            
//            try {
//            
//                // see if the add combobox was changed
//                if(source instanceof JRadioButton) {
//                    _nameComboBox.removeAllItems();
//                    String typeStr = (String) ((JRadioButton)source).getText();
//                    if(typeStr.equals("Input")) {
//                        for(String name : _choice.getInputNames(true)) {
//                            _nameComboBox.addItem(name);
//                        }
//                    } else if(typeStr.equals("Output")) {
//                        for(String name : _choice.getOutputNames(true)) {
//                            _nameComboBox.addItem(name);
//                        }
//                    } else if(typeStr.equals("Parameter")) {
//                        for(String name : _choice.getParameterNames()) {
//                            _nameComboBox.addItem(name);
//                        }
//                    } else {
//                        // add was set to execution choice, so load the template
//                        // names in the type combobox
//                        for(Refinement refinement : _choice.entityList(Refinement.class)) {
//                            _nameComboBox.addItem(refinement.getDisplayName());
//                        }
//                    }
//                                   
//                    // the choice combobox denotes which execution choice 
//                    // contains the parameter to remove.
//                    if(typeStr.equals("Parameter")) {
//                        _choiceLabel.setEnabled(true);
//                        _choiceComboBox.setEnabled(true);
//                    } else {
//                        _choiceLabel.setEnabled(false);
//                        _choiceComboBox.setEnabled(false);
//                    }
//                                
//                } else if(source == _choiceComboBox) {
//                    
//                    _nameComboBox.removeAllItems();
//                    String refinementName = (String) _choiceComboBox.getSelectedItem();
//                    if(refinementName.equals(ExecutionChoice.ALL_CHOICES_NAME)) {
//                        for(String name : _choice.getParameterNames()) {
//                            _nameComboBox.addItem(name);
//                        }
//                    } else {
//                        ComponentEntity<?> refinement = _choice.getEntity(refinementName);
//                        for(Parameter parameter : refinement.attributeList(Parameter.class)) {
//                            String name = parameter.getName();
//                            // can't delete the commandLine parameter
//                            if(!name.equals(ExecutionChoice.COMMAND_LINE_NAME) &&
//                                    !name.startsWith("_")) {
//                                _nameComboBox.addItem(name);
//                            }
//                        }
//                    }
//                }
//                
//                if(_nameComboBox.getItemCount() == 0) {
//                    _nameComboBox.addItem(NONE_TO_REMOVE);
//                    _nameComboBox.setEnabled(false);
//                } else {
//                    _nameComboBox.setEnabled(true);
//                }
//            } catch(IllegalActionException e) {
//                MessageHandler.error("Error performing action.", e);
//            }
//        }
//        
//        /** Get the container of the parameter to remove. */
//        public NamedObj getRemoveContainer() {
//            String refinementName = (String) _choiceComboBox.getSelectedItem();
//            if(refinementName.equals(ExecutionChoice.ALL_CHOICES_NAME)) {
//                return _choice;
//            } else {
//                return _choice.getEntity(refinementName);
//            }
//        }
//
//        /** Get the user-specified name of the component removed. */
//        public String getTextName() {
//            String name = (String) _nameComboBox.getSelectedItem();
//            if(!name.equals(NONE_TO_REMOVE)) {
//                return name;
//            }
//            return null;
//        }
//        
//        /** Get the type of component to be removed, 
//         *  e.g., input, output, parameter, execution choice.
//         */
//        public String getRemove() {
//            return _getActionButtonSelected();
//        }
//                
//        private JComboBox<String> _nameComboBox;
//        
//        private static final String NONE_TO_REMOVE = "None to remove."; 
//    }
//    
//    /** A utility class to hold rename information. */
//    private static class RenameInfo {
//        
//        public RenameInfo(String newName, String oldName, String type, NamedObj container) {
//            _newName = newName;
//            _oldName = oldName;
//            _type = type;
//            _container = container;
//        }
//        
//        private String _oldName;
//        private String _newName;
//        private String _type;
//        private NamedObj _container;
//    }
//  
//    /** A pane to rename inputs, outputs, or execution choices. */
//    private static class RenamePane extends FieldsPane {
//        
//        public RenamePane(ExecutionChoice choice) throws IllegalActionException {   
//            super(choice, true, true);            
//        }
//       
//        /** Get rename information. */
//        public Set<RenameInfo> getRenames() throws IllegalActionException {
//            
//            final Set<RenameInfo> renames = new HashSet<RenameInfo>();
//            
//            Query query = _queries.get(_choice);
//            
//            for(String name : _choice.getInputNames(true)) {
//                String newName = query.getStringValue(name);
//                if(!name.equals(newName)) {
//                    renames.add(new RenameInfo(newName, name, "Input", _choice));
//                }
//            }
//
//            for(String name : _choice.getOutputNames(true)) {
//                String newName = query.getStringValue(name);
//                if(!name.equals(newName)) {
//                    renames.add(new RenameInfo(newName, name, "Output", _choice));
//                }
//            }
//
//            for(String name : _choice.getParameterNames()) {
//                String newName = query.getStringValue(name);
//                if(!name.equals(newName)) {
//                    renames.add(new RenameInfo(newName, name, "Parameter", _choice));
//                }
//            }
//
//            for(String name : _choice.getExecutionChoiceNames()) {
//                String newName = query.getStringValue(name);
//                if(!name.equals(newName)) {
//                    renames.add(new RenameInfo(newName, name, "Execution Choice", _choice));
//                }
//            }
//            
//            // get the renames for parameters in refinements
//            for(String executionName : _choice.getExecutionChoiceNames()) {
//                ComponentEntity<?> container = _choice.getEntity(executionName);
//                query = _queries.get(container);
//                
//                for(Parameter parameter : container.attributeList(Parameter.class)) {
//                    String name = parameter.getName();
//                    if(!name.equals(ExecutionChoice.COMMAND_LINE_NAME) &&
//                            !name.startsWith("_")) {
//                        String newName = query.getStringValue(name);
//                        if(!name.equals(newName)) {
//                            renames.add(new RenameInfo(newName, name, "Parameter", container));
//                        }
//                    }
//                }
//            }
//
//            return renames;
//        }
//        
//        /** Add a set of renames to a query.
//         * @param names the list of field names
//         * @param choice the ExecutionChoice actor
//         * @param container the container of the fields. this may be the same as choice.
//         * @param query the query dialog to add the gui widgets to
//         * @param noneMessage the text to display in the query if no fields are added.
//         */
//        @Override
//        protected void _addFields(List<String> names, ExecutionChoice choice, 
//                NamedObj container, Query query, String noneMessage) {
//             
//            if(!names.isEmpty()) {
//                String[] namesArray = names.toArray(new String[names.size()]);
//                Arrays.sort(namesArray);
//                for(String name : names) {
//                    query.addTextArea(name, name, name, _STRING_MODE_BACKGROUND_COLOR,
//                            Color.BLACK, 1, Query.DEFAULT_ENTRY_WIDTH);
//                }               
//            } else {
//                query.addText(noneMessage, Color.BLACK, SwingConstants.LEFT);
//            }
//        }
//        
//    }
//    
//    /** A pane to enter the name of an execution choice. */
//    private static class ImportPane extends JPanel {
//    	
//    	/** Create a new ImportPane with the default name for the
//    	 *  imported execution choice.
//    	 */
//    	public ImportPane(String defaultName) {
//    		
//    		add(new JLabel("Execution Choice Name:"));
//    		
//    		_nameTextField = new JTextField(defaultName);
//    		add(_nameTextField);
//    	}
//    	
//    	/** Get the name of the execution choice. */
//    	public String getChoiceName() {
//    		return _nameTextField.getText();
//    	}
//    	
//    	/** A text field to enter the name. */
//    	private JTextField _nameTextField;
//    	
//    }
//    
//    /** A pane to export an execution choice. */
//    private static class ExportPane extends JPanel {
//
//        public ExportPane(ExecutionChoice choice) {
//            
//            add(new JLabel("Execution Choice:"));
//            
//            _choiceComboBox = new JComboBox<String>(choice.getExecutionChoiceNames().toArray(new String[0]));
//            add(_choiceComboBox);
//        }
//
//        /** Get the name of the selected execution choice. */
//        public String getChoiceName() {
//            return (String) _choiceComboBox.getSelectedItem();
//        }
//
//        private JComboBox<String> _choiceComboBox;
//    }
//    
//    private static class ArgumentsPane extends FieldsPane {
//        
//        public ArgumentsPane(ExecutionChoice choice) throws IllegalActionException {
//            super(choice, false, false);            
//        }
//
//        /** Update the argument parameters with new values from the dialog. */
//        public void applyChanges() throws IllegalActionException, NameDuplicationException {
//            
//            for(Map.Entry<NamedObj, Query> entry : _queries.entrySet()) {
//                final NamedObj container = entry.getKey();
//                final Query query = entry.getValue();
//
//                Set<String> names = new HashSet<String>();
//                if(container == _choice) {
//                    // add inputs for file types
//                    for(String inputName : _choice.getInputNames(false)) {
//                        if(ExecutionChoice.getPortIOType(_choice.getPort(inputName))
//                                == ExecutionChoice.IOType.File) {
//                            names.add(inputName);
//                        }
//                    }
//                    
//                    // add output for file types
//                    for(String outputName : _choice.getOutputNames(false)) {
//                        if(ExecutionChoice.getPortIOType(_choice.getPort(outputName))
//                                == ExecutionChoice.IOType.File) {
//                            names.add(outputName);
//                        }
//                    }
//                    
//                    // add parameters
//                    names.addAll(_choice.getParameterNames());
//                } else {
//                    // add the parameter names in the refinement
//                    List<Parameter> parameters = container.attributeList(Parameter.class);
//                    for(Parameter parameter : parameters) {
//                        String name = parameter.getName();
//                        if(!name.equals(ExecutionChoice.COMMAND_LINE_NAME) &&
//                                !name.startsWith("_")) {
//                            names.add(name);
//                        }
//                    }
//                }
//                
//                // see if any values in the query were changed
//                for(String name : names) {
//                    String argument = ExecutionChoice.getArgument(container, name);
//                    String newArgument = query.getStringValue(name);
//                    if((argument == null && !newArgument.trim().isEmpty()) ||
//                            (argument != null && !argument.equals(newArgument))) {
//                        Parameter parameter = (Parameter) container.getAttribute(name);
//                        ExecutionChoice.setArgument(parameter, newArgument);
//                        //System.out.println(name + " changed " + argument + " to " + newArgument);
//                    }
//                }
//            }
//        }
//        
//        /** Add a set of arguments to a query.
//         *  @param names the names of the components containing the arguments
//         *  @param choice the ExecutionChoice containing the components with arguments.
//         *  @param query the query in which to add the fields
//         *  @param noneMessage the text to add to the query if names is empty.
//         * 
//         */
//        @Override
//        protected void _addFields(List<String> names, ExecutionChoice choice,
//                NamedObj container, Query query, String noneMessage) throws IllegalActionException {
//        	 
//            if(!names.isEmpty()) {
//	            String[] namesArray = names.toArray(new String[names.size()]);
//	            Arrays.sort(namesArray);
//	            for(String name : names) {
//	                String argument = ExecutionChoice.getArgument(container, name);
//	                query.addTextArea(name,
//	                        name,
//	                        argument,
//	                        _STRING_MODE_BACKGROUND_COLOR,
//	                        Color.black,
//	                        1,
//	                        Query.DEFAULT_ENTRY_WIDTH);
//	            }	            
//            } else {
//            	query.addText(noneMessage, Color.BLACK, SwingConstants.LEFT);
//            }
//        }
//                
//    }
//    
//    /** A dialog for changing the types of inputs, outputs, and parameters. */
//    private static class TypesPane extends FieldsPane {
//        
//        public TypesPane(ExecutionChoice choice) throws IllegalActionException {
//            super(choice, false, true);
//        }
//        
//        /** Change the types. */
//        public void applyChanges() throws IllegalActionException, NameDuplicationException {
//            
//            Query query = _queries.get(_choice);
//            
//            List<Port> ports = new LinkedList<Port>(_choice.portList());
//            
//            for(Port port : ports) {
//            	if(port != _choice.control.getPort()) {
//	                String newTypeStr = query.getStringValue(port.getName());
//	                ExecutionChoice.IOType newType = ExecutionChoice.IOType.valueOf(newTypeStr);
//	                // see if the types are different
//	                if(ExecutionChoice.getPortIOType(port) != newType) {
//	                	_choice.setPortIOType((TypedIOPort)port, newType);
//	                }
//            	}
//            }
//
//            for(String name : _choice.getParameterNames()) {
//                String newTypeStr = query.getStringValue(name);
//                ExecutionChoice.ParameterType newType = ExecutionChoice.ParameterType.valueOf(newTypeStr);
//                // see if the types are different
//                if(_choice.getParameterType(name) != newType) {
//                	_choice.setParameterType(name, newType);
//                }
//            }
//            
//            // get the type changes for parameters in refinements
//            for(String executionName : _choice.getExecutionChoiceNames()) {
//                ComponentEntity<?> container = _choice.getEntity(executionName);
//                query = _queries.get(container);
//                
//                for(Parameter parameter : container.attributeList(Parameter.class)) {
//                    String name = parameter.getName();
//                    if(!name.equals(ExecutionChoice.COMMAND_LINE_NAME) &&
//                            !name.startsWith("_")) {
//                        String newTypeStr = query.getStringValue(name);
//                        ExecutionChoice.ParameterType newType = ExecutionChoice.ParameterType.valueOf(newTypeStr);
//                        // see if the types are different
//                        if(ExecutionChoice.getParameterType(container, name) != newType) {
//                        	ExecutionChoice.setParameterType(container, name, newType);
//                        }
//                    }
//                }
//            }
//        }
//        
//        /** Add a set of fields and their types to a query.
//         *  @param names the names of the fields
//         *  @param choice the ExecutionChoice containing the fields.
//         *  @param container the container of the fields. this may be the same as choice.
//         *  @param query the query in which to add the fields
//         *  @param noneMessage the text to add to the query if names is empty.
//         */
//        @Override
//        protected void _addFields(List<String> names, ExecutionChoice choice,
//                NamedObj container, Query query, String noneMessage) throws IllegalActionException {
//             
//            if(!names.isEmpty()) {
//                String[] namesArray = names.toArray(new String[names.size()]);
//                Arrays.sort(namesArray);
//                for(String name : names) {
//                    
//                	boolean added = false;
//                	if(container == choice) {
//                		Port port = choice.getPort(name);
//                		// see if the name belongs to a port or a parameter.
//                		if(port != null) {
//                			ExecutionChoice.IOType type = ExecutionChoice.getPortIOType(port);
//                			query.addChoice(name, name, ExecutionChoice.IOType.values(), type);
//                			added = true;
//                		}
//                	}
//                	
//                    if(!added) {
//                        ExecutionChoice.ParameterType type = ExecutionChoice.getParameterType(container, name);
//                        query.addChoice(name, name, ExecutionChoice.ParameterType.values(), type);
//                    }
//                }               
//            } else {
//                query.addText(noneMessage, Color.BLACK, SwingConstants.LEFT);
//            }
//        }
//    }
//    
//    /** A base class to display a pane for the inputs, outputs, parameters,
//     *  and sub-workflows.
//     */
//    private static abstract class FieldsPane extends JPanel {
//        
//        /** Create a new pane listing the fields for an ExecutionChoice actor.
//         * @param choice the ExecutionChoice actor 
//         * @param includeChoices if true, include the list of refinements
//         * @param includeDataPorts if true, the list of inputs and outputs
//         * are the data and file ports. otherwise, the list is the file ports.
//         */
//        private FieldsPane(ExecutionChoice choice, boolean includeChoices, boolean includeDataPorts)
//                throws IllegalActionException {
//            
//            _choice = choice;
//            
//            Query query = new Query();
//            _queries.put(choice, query);
//                              
//            // NOTE: use empty spaces here to make the dialog wider otherwise the tabs
//            // for refinements are stacked (linux) or do not appear (mac).
//            
//            // add inputs
//            query.addText("File Input Parameters                                                              ",
//            		Color.BLACK, SwingConstants.LEFT);
//            _addFields(choice.getInputNames(includeDataPorts), choice, choice, query, "None");
//            
//            // add outputs
//            query.addSeparator();
//            query.addText("File Output Parameters                                                              ",
//            		Color.BLACK, SwingConstants.LEFT);
//            _addFields(choice.getOutputNames(includeDataPorts), choice, choice, query, "None");
//            
//            // add parameters
//            query.addSeparator();
//            query.addText("Additional Parameters                                                              ",
//            		Color.BLACK, SwingConstants.LEFT);
//            _addFields(choice.getParameterNames(), choice, choice, query, "None");
//            
//            // add execution choices
//            if(includeChoices) {
//                query.addSeparator();
//                query.addText("Execution Choices", Color.BLACK, SwingConstants.LEFT);
//                _addFields(choice.getExecutionChoiceNames(), choice, choice, query, "None");
//            }
//            
//            JTabbedPane tabbedPane = new JTabbedPane();
//            tabbedPane.addTab("Shared Options", query);
//
//            // add a tab for each refinement
//            
//            for(String executionName : choice.getExecutionChoiceNames()) {
//                query = new Query();
//                final ComponentEntity<?> container = choice.getEntity(executionName);
//                _queries.put(container, query);
//                
//                // get the names and sort them
//                final List<String> names = new LinkedList<String>();
//                final List<Parameter> parameters = container.attributeList(Parameter.class);
//                for(Parameter parameter : parameters) {
//                    String name = parameter.getName();
//                    if(!name.equals(ExecutionChoice.COMMAND_LINE_NAME) &&
//                            !name.startsWith("_")) {
//                        names.add(parameter.getName());
//                    }
//                }
//                _addFields(names, choice, container, query, "No parameters.");
//                
//                tabbedPane.addTab(executionName, query);
//            }
//            
//            add(tabbedPane);    
//
//        }
//        
//        /** Add the fields for a set of names.
//         * @param names the list of field names
//         * @param choice the ExecutionChoice actor
//         * @param container the container of the fields. this may be the same as choice.
//         * @param query the query dialog to add the gui widgets to
//         * @param noneMessage the text to display in the query if no fields are added.
//         */
//        protected abstract void _addFields(List<String> names, ExecutionChoice choice,
//                NamedObj container, Query query, String noneMessage) throws IllegalActionException;
//        
//        protected ExecutionChoice _choice;
//        protected Map<NamedObj,Query> _queries = new HashMap<NamedObj,Query>(); 
//
//    }
//    
//    /** Background color for string mode edit boxes. */
//    // FIXME copied from PtolemyQuery since private
//    private static Color _STRING_MODE_BACKGROUND_COLOR = new Color(230, 255,
//            255, 255);
//
//}
