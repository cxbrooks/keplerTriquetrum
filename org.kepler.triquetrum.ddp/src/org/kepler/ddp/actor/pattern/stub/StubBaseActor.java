/* A base class for DDP pattern actor stubs.
 * 
 * Copyright (c) 2011-2012 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2014-04-11 17:09:39 -0700 (Fri, 11 Apr 2014) $' 
 * '$Revision: 32657 $'
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
package org.kepler.ddp.actor.pattern.stub;

import ptolemy.actor.TypedAtomicActor;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;

/** A base class for DDP pattern actor stubs. A stub actor transfers
 *  data between the Kepler workflow and the underlying execution engine
 *  (e.g., Stratosphere or Hadoop).
 * 
 *  @author Daniel Crawl
 *  @version $Id: StubBaseActor.java 32657 2014-04-12 00:09:39Z crawl $
 */
public class StubBaseActor extends TypedAtomicActor {

    /** Construct a new StubBaseActor in a container with a given name. */
    public StubBaseActor(CompositeEntity container, String name)
            throws IllegalActionException, NameDuplicationException {
        super(container, name);
    }

    /** Set if the full the full lifecycle of the sub-workflow will be executed
     * for each input. If false, only a single interation occurs for each input.
     */
    public void setRunWorkflowLifecyclePerInput(boolean runWorkflowLifecyclePerInput) {
        _runWorkflowLifecyclePerInput = runWorkflowLifecyclePerInput;
    }

    /** If true, the full the full lifecycle of the sub-workflow will be executed for each input. */
    protected boolean _runWorkflowLifecyclePerInput = false;

}
