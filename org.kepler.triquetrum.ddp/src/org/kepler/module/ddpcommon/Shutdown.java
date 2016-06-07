/*
 * Copyright (c) 2015 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2015-09-18 17:17:48 -0700 (Fri, 18 Sep 2015) $' 
 * '$Revision: 33923 $'
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
package org.kepler.module.ddpcommon;

import org.kepler.ddp.director.DDPEngine;
import org.kepler.module.ModuleShutdownable;

import ptolemy.kernel.util.IllegalActionException;
import ptolemy.util.MessageHandler;

/** Perform cleanup for the ddp-common module.
 * 
 * @author Daniel Crawl
 * @version $Id: Shutdown.java 33923 2015-09-19 00:17:48Z crawl $
 */
public class Shutdown implements ModuleShutdownable
{
    /** Perform any module-specific cleanup. */
    @Override
    public void shutdownModule()
    {        
        // close any effigies opened for UI actors in pattern actors.
        try {
            DDPEngine.closeAllEffigies();
        } catch (IllegalActionException e) {
            MessageHandler.error("Error closing effigies from replayed workflows.", e);
        }
    }
}