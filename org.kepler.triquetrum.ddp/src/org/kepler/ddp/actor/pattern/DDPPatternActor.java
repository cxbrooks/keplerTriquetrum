/* An interface for DDP pattern actors.
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

import java.io.IOException;
import java.io.Writer;
import java.util.List;

import ptolemy.actor.Actor;
import ptolemy.kernel.Port;
import ptolemy.kernel.util.Attribute;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.Workspace;

/** An interface for all DDP pattern actors.
 * 
 *  @author Daniel Crawl
 *  @version $Id: DDPPatternActor.java 32714 2014-05-09 21:10:24Z crawl $
 */
public interface DDPPatternActor extends Actor {

    /** Get a list of ports. */
    public List portList();

    /** Returns true if the actor is atomic, or is composite and contains a director. */
    public boolean isOpaque();

    /** Get the port with the given name. */
    public Port getPort(String string);

    /** Write a MoML description of this object using the specified Writer. */
    public void exportMoML(Writer writer) throws IOException;

    /** Clone the actor into a workspace. */
    public Object clone(Workspace workspace) throws CloneNotSupportedException;

    /** Get the attribute with the given name. */
    public Attribute getAttribute(String string);

    /** Get the number of parallel instances to execute. */
    public int getDegreeOfParallelism() throws IllegalActionException;
    
    /** Get the dir to redirect display related actors. */
    public String getDisplayRedirectDir() throws IllegalActionException;
    
    /** Get a set of name-value pairs of input/output format parameters for the execution engine. */
    public java.util.Map<String,String> getParameters() throws IllegalActionException;
    
    /** Get a set of (kepler name, implementation name) pairs of input/output format parameters for the execution engine. */
    public java.util.Map<String,String> getParaImplNames(String engineName) throws IllegalActionException;
    
    /** Get the name of the execution class. If no execution class is set,
     *  e.g., for composite pattern actors this means the sub-workflow is
     *  to be executed, returns the empty string.
     */
    public String getExecutionClassName() throws IllegalActionException;

    /** Get a comma-separated list of jars to use with this actor. */
    public String getJars() throws IllegalActionException;
    
    /** Get whether print execution summary when running workflow inside of Hadoop/Stratosphere job. */
    public boolean getPrintExeInfo() throws IllegalActionException;
    
    /** Get the execution code type. Returns null if not set. */
    public String getExecutionCodeType() throws IllegalActionException;
    
    /** Get the execution code. Returns null if not set. */
    public String getExecutionCode() throws IllegalActionException;
    
    /** Some common key value types for inputs and outputs. */
    public final static String[] _commonKeyValueTypes = 
        { "int string", "long string", "string string", "string int" };
    
}
