/* An actor for the CoGroup DDP pattern.
 * 
 * Copyright (c) 2011-2012 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2012-04-13 10:45:44 -0700 (Fri, 13 Apr 2012) $' 
 * '$Revision: 29710 $'
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

import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import ptolemy.kernel.util.Workspace;

/** An actor for the CoGroup DDP pattern. This actor reads two
 *  input data sets of key-value pairs. The sub-workflow in 
 *  this actor needs to be completed by reading data 
 *  from the output ports of the CoGroupInput actor and sending
 *  key-value pairs (an array of <key, value> records) to the 
 *  CoGroupOutput actor. For each key that is present in either 
 *  input data set (including when the key is present in only one
 *  input), the sub-workflow is executed once with the key and
 *  associated values. 
 * 
 *  @author Daniel Crawl
 *  @version $Id: CoGroup.java 29710 2012-04-13 17:45:44Z crawl $
 */
public class CoGroup extends DualInputPatternActor {

    /** Construct a new CoGroup in a workspace. */
    public CoGroup(Workspace workspace) {
        super(workspace);
    }

    /** Construct a new CoGroup in a container with a given name. */
    public CoGroup(CompositeEntity container, String name)
            throws IllegalActionException, NameDuplicationException {
        super(container, name);
    }

}
