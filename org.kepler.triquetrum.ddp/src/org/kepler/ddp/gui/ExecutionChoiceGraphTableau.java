///* A tableau to create execution choice frames. 
// * 
// * Copyright (c) 2012 The Regents of the University of California.
// * All rights reserved.
// *
// * '$Author: crawl $'
// * '$Date: 2015-08-24 15:45:41 -0700 (Mon, 24 Aug 2015) $' 
// * '$Revision: 33631 $'
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
//import org.kepler.ddp.actor.ExecutionChoice;
//import org.kepler.gui.frame.MultiCompositeTableau;
//
//import ptolemy.actor.gui.Effigy;
//import ptolemy.actor.gui.PtolemyEffigy;
//import ptolemy.actor.gui.Tableau;
//import ptolemy.actor.gui.TableauFactory;
//import ptolemy.kernel.CompositeEntity;
//import ptolemy.kernel.util.IllegalActionException;
//import ptolemy.kernel.util.NameDuplicationException;
//import ptolemy.kernel.util.NamedObj;
//import ptolemy.moml.LibraryAttribute;
//import ptolemy.util.MessageHandler;
//
///** An editor tableau for execution choice constructs.
// *
// *  @author Daniel Crawl
// *  @version $Id: ExecutionChoiceGraphTableau.java 33631 2015-08-24 22:45:41Z crawl $
// *
// */
//public class ExecutionChoiceGraphTableau extends MultiCompositeTableau {
//    /** Create a new execution choice editor tableau with the specified container
//     *  and name.
//     *  @param container The container.
//     *  @param name The name.
//     *  @exception IllegalActionException If the model associated with
//     *   the container effigy is not an instance of ExecutionChoice.
//     *  @exception NameDuplicationException If the container already
//     *   contains an object with the specified name.
//     */
//    public ExecutionChoiceGraphTableau(PtolemyEffigy container, String name)
//            throws IllegalActionException, NameDuplicationException {
//        this(container, name, null);
//    }
//
//    /** Create a new execution choice editor tableau with the specified container,
//     *  name, and default library.
//     *  @param container The container.
//     *  @param name The name.
//     *  @param defaultLibrary The default library, or null to not specify one.
//     *  @exception IllegalActionException If the model associated with
//     *   the container effigy is not an instance of ExecutionChoice.
//     *  @exception NameDuplicationException If the container already
//     *   contains an object with the specified name.
//     */
//    public ExecutionChoiceGraphTableau(PtolemyEffigy container, String name,
//            LibraryAttribute defaultLibrary) throws IllegalActionException,
//            NameDuplicationException {
//        super(container, name);
//    }
//
//    /** Add default choice sub-workflows to the ExecutionChoice before
//     *  opening it.
//     */
//    @Override
//    public void createGraphFrame(CompositeEntity model, LibraryAttribute defaultLibrary) {
//
//        // set the selected tab to the model for this tableau.
//        // if the model is not one of the tabs, then setSelectedTab() does nothing.
//        final PtolemyEffigy effigy = (PtolemyEffigy)getContainer();
//        final ExecutionChoice choice = (ExecutionChoice) effigy.getModel();
//        
//        try {
//            choice.addDefaults();
//        } catch(IllegalActionException | NameDuplicationException e) {
//            MessageHandler.error("Error adding default choices.", e);
//            return;
//        }
//        
//        super.createGraphFrame(model, defaultLibrary);
//
//    }
//    
//    ///////////////////////////////////////////////////////////////////
//    ////                     public inner classes                  ////
//
//    /** A factory that creates graph editing tableaux for Ptolemy models.
//     */
//    public static class Factory extends TableauFactory {
//        /** Create an factory with the given name and container.
//         *  @param container The container.
//         *  @param name The name of the entity.
//         *  @exception IllegalActionException If the container is incompatible
//         *   with this attribute.
//         *  @exception NameDuplicationException If the name coincides with
//         *   an attribute already in the container.
//         */
//        public Factory(NamedObj container, String name)
//                throws IllegalActionException, NameDuplicationException {
//            super(container, name);
//        }
//
//        /** Create an instance of ExecutionChoiceGraphTableau for the specified effigy,
//         *  if it is an effigy for an instance of ExecutionChoice.
//         *  @param effigy The effigy for a ExecutionChoice.
//         *  @return A new ExecutionChoiceGraphTableau, if the effigy is a PtolemyEffigy
//         *   that references a ExecutionChoice, or null otherwise.
//         *  @exception Exception If an exception occurs when creating the
//         *   tableau.
//         */
//        @Override
//        public Tableau createTableau(Effigy effigy) throws Exception {
//            if (!(effigy instanceof PtolemyEffigy)) {
//                return null;
//            }
//
//            NamedObj model = ((PtolemyEffigy) effigy).getModel();
//
//            if (model instanceof ExecutionChoice) {
//                // Check to see whether this factory contains a
//                // default library.
//                LibraryAttribute library = (LibraryAttribute) getAttribute(
//                        "_library", LibraryAttribute.class);
//
//                ExecutionChoiceGraphTableau tableau = new ExecutionChoiceGraphTableau(
//                        (PtolemyEffigy) effigy, effigy.uniqueName("tableau"),
//                        library);
//                return tableau;
//            } else {
//                return null;
//            }
//        }
//    }
//}