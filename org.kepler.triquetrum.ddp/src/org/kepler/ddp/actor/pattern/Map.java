/* An actor for the Map DDP pattern.
 * 
 * Copyright (c) 2011-2012 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: jianwu $'
 * '$Date: 2013-02-15 13:11:35 -0800 (Fri, 15 Feb 2013) $' 
 * '$Revision: 31450 $'
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

import ptolemy.data.expr.StringParameter;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import ptolemy.kernel.util.Workspace;

/** An actor for the Map DDP pattern. This actor reads one
 *  input data set of key-value pairs. The sub-workflow in 
 *  this actor needs to be completed by reading data 
 *  from the output ports of the MapInput actor and sending key-
 *  value pairs (an array of <key, value> records) to the 
 *  MapOutput actor. For each key-value pair that is
 *  present in the input data set, the sub-workflow is executed
 *  once with that key and value.
 * 
 *  @author Daniel Crawl
 *  @version $Id: Map.java 31450 2013-02-15 21:11:35Z jianwu $
 */
public class Map extends SingleInputPatternActor {

    /** Construct a new Map in a container with a given name. */
    public Map(CompositeEntity container, String name)
            throws IllegalActionException, NameDuplicationException {
        super(container, name);
        partitionerClass = new StringParameter(this, "partitionerClass");
    }
    
    /** Construct a new Map in a workspace. */
    public Map(Workspace workspace) {
        super(workspace);
    }

    /** The name of the PartitionerClass, used only in hadoop for partioning map outputs. */
    public StringParameter partitionerClass;
}
