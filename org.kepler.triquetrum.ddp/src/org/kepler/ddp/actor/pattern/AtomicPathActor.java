/* An atomic DDP pattern actor with a path parameter.
 * 
 * Copyright (c) 2014 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2014-10-08 14:02:15 -0700 (Wed, 08 Oct 2014) $' 
 * '$Revision: 32992 $'
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

import java.io.File;
import java.net.URI;

import ptolemy.actor.parameters.FilePortParameter;
import ptolemy.data.StringToken;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import ptolemy.kernel.util.SingletonAttribute;

/** An atomic DDP pattern actor with a path parameter.
 * 
 *  @author Daniel Crawl
 *  @version $Id: AtomicPathActor.java 32992 2014-10-08 21:02:15Z crawl $
 */
public class AtomicPathActor extends AtomicPatternActor {

    /** Create a new AtomicPathActor in the speficied container with the
     *  specified name.
     */
    public AtomicPathActor(CompositeEntity container, String name)
            throws IllegalActionException, NameDuplicationException {
        
        super(container, name);

        path = new FilePortParameter(this, "path");
        new SingletonAttribute(path.getPort(), "_showName");

    }

    /** Get the path as a URI string. */
    public URI getPathAsURI() throws IllegalActionException {
     
        String pathStr = ((StringToken)path.getToken()).stringValue();
        if(pathStr.isEmpty()) {
            throw new IllegalActionException(this, "Must specify data input file name.");
        }
        
        if(pathStr.startsWith("hdfs://") || pathStr.startsWith("file://")) {
            return URI.create(pathStr);
        } else {
            File file = path.asFile();
            if(file == null) {
                throw new IllegalActionException(this, "Must specify data input file name.");
            }
            return file.toURI();
        }
    }

    /** Update the path parameter. */
    @Override
    public boolean prefire() throws IllegalActionException {
        
        boolean rc = super.prefire();
        path.update();
        
        return rc;
    }
    
    /** The path to read/write the data. */
    public FilePortParameter path;

}
