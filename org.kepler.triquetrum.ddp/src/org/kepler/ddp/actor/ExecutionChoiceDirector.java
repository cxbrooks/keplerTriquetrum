/* A director to control the execution of ExecutionChoice actors. 
 * 
 * Copyright (c) 2012 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2014-02-19 10:21:33 -0800 (Wed, 19 Feb 2014) $' 
 * '$Revision: 32608 $'
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

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import ptolemy.actor.Director;
import ptolemy.actor.FiringEvent;
import ptolemy.actor.IOPort;
import ptolemy.actor.IOPortEvent;
import ptolemy.actor.Receiver;
import ptolemy.actor.lib.hoc.Case;
import ptolemy.actor.lib.hoc.CaseDirector;
import ptolemy.actor.lib.hoc.Refinement;
import ptolemy.actor.parameters.ParameterPort;
import ptolemy.actor.parameters.PortParameter;
import ptolemy.data.BooleanToken;
import ptolemy.data.StringToken;
import ptolemy.data.Token;
import ptolemy.data.expr.Parameter;
import ptolemy.data.expr.StringParameter;
import ptolemy.domains.ddf.kernel.DDFDirector;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import ptolemy.kernel.util.NamedObj;
import ptolemy.kernel.util.Workspace;

/**
 * A director to control the execution of execution choice actors.
 * 
 * @author Daniel Crawl
 * @version $Id: ExecutionChoiceDirector.java 32608 2014-02-19 18:21:33Z crawl $
 */
public class ExecutionChoiceDirector extends CaseDirector {

    /** Create a new ExecutionChoiceDirector in a container
     *  with a specific name.
     */
    public ExecutionChoiceDirector(CompositeEntity container, String name)
            throws IllegalActionException, NameDuplicationException {
        super(container, name);
        
        // make sure the container in ExecutionChoice
        // XXX do not do this check since container may be CompositeClassEntity
        /*
        NamedObj namedObj = getContainer();
        if (!(namedObj instanceof ExecutionChoice)) {
            throw new IllegalActionException(
                    "ExecutionChoiceDirector may only be used with the ExecutionChoice actor.");
        }
        */
    }

    /** Clone this director into the specified workspace. */
    @Override
    public Object clone(Workspace workspace) throws CloneNotSupportedException {
    	ExecutionChoiceDirector newObject = (ExecutionChoiceDirector) super.clone(workspace);
    	newObject._addedDirectors = new LinkedList<Director>();
    	return newObject;
    }
    
    /** Read the control token input, transfer input tokens,
     *  and invoke prefire() of the selected refinement.
     *
     *  @exception IllegalActionException If there is no director,
     *   or if the director's prefire() method throws it, or if this actor
     *   is not opaque.
     */
    @Override
    public boolean prefire() throws IllegalActionException {
        if (_debugging) {
            _debug("Calling prefire()");
        }

        try {
            _workspace.getReadAccess();
            //super.prefire();

            Case container = (Case) getContainer();
            // Read from port parameters, including the control port.
            Iterator<PortParameter> portParameters = container.attributeList(
                    PortParameter.class).iterator();
            while (portParameters.hasNext()) {
                PortParameter portParameter = (PortParameter) portParameters
                        .next();
                portParameter.update();
            }

            String controlValue;
            Token controlToken = container.control.getToken();
            // If it's a string, use stringValue() otherwise there
            // are quotes around the string.
            if (controlToken instanceof StringToken) {
                controlValue = ((StringToken) controlToken).stringValue();
            } else {
                controlValue = controlToken.toString();
            }

            ExecutionChoice choice = (ExecutionChoice)container;
            
            Refinement refinement = (Refinement) container.getEntity(controlValue);
            
            // make sure we found the refinement.
            if(refinement == null) {
            	throw new IllegalActionException(choice, "Could not find execution choice called \"" +
            			controlValue + "\"");
            }
            
            // NOTE: the control value in ExecutionChoice is prevented from being
            // set to something other than a refinement, so this check is not necessary.
            /*
            if (!(refinement instanceof Refinement)) {
                refinement = choice._getDefaultRefinement();
            }
            */
            choice._setCurrentRefinement(refinement);

            // Transfer input tokens.
            for (IOPort port : (List<IOPort>)choice.inputPortList()) {
                
                if(_stopRequested) {
                    break;
                }
                
                if (!(port instanceof ParameterPort)) {
                    Receiver[][] insideReceivers = port.deepGetReceivers();
                    for (int i = 0; i < insideReceivers.length; i++) {
                        Token token = null;
                        if(port.numberOfSources() > 0) {
                            if (i < port.getWidth() && port.hasToken(i)) {
                                token = port.get(i);
                            }
                        } else if(choice.getPortIOType(port) == ExecutionChoice.IOType.File){
                            token = ((Parameter)container.getAttribute(port.getName())).getToken();
                        }
                        if(token != null) {
                            if (insideReceivers[i] != null) {
                                for (int j = 0; j < insideReceivers[i].length; j++) {
                                    if (insideReceivers[i][j].getContainer()
                                            .getContainer() == refinement) {

                                        if (_debugging) {
                                            _debug(new IOPortEvent(port,
                                                    insideReceivers[i][j]
                                                            .getContainer(),
                                                    true, i, false, token));
                                        }

                                        insideReceivers[i][j].put(token);

                                        if (_debugging) {
                                            _debug(new IOPortEvent(port,
                                                    insideReceivers[i][j]
                                                            .getContainer(),
                                                    false, i, false, token));
                                            _debug(getFullName(),
                                                    "transferring input from "
                                                            + port.getFullName()
                                                            + " to "
                                                            + (insideReceivers[i][j])
                                                                    .getContainer()
                                                                    .getFullName());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (_stopRequested) {
                return false;
            }
            if (_debugging) {
                _debug(new FiringEvent(this, refinement,
                        FiringEvent.BEFORE_PREFIRE));
            }
            boolean result = refinement.prefire();
            if (_debugging) {
                _debug(new FiringEvent(this, refinement,
                        FiringEvent.AFTER_PREFIRE));
            }
            return result;
        } finally {
            _workspace.doneReading();
        }
    }
    
    /** Add DDF director to any refinements that have no directors. */
    @Override
    public void preinitialize() throws IllegalActionException {
        	
        Case container = (Case) getContainer();
    	final List<Refinement> refinements = container.entityList(Refinement.class);
    	for(Refinement refinement : refinements) {
    		if(!refinement.isOpaque()) {
    			//System.out.println("adding ddf to " + refinement.getName());
    			DDFDirector director;
				try {
					director = new DDFDirector(refinement, "DDF Director");
				} catch (NameDuplicationException e) {
					throw new IllegalActionException(refinement, e,
							"Error adding DDF director in choice " + refinement.getName());
				}
				director.setPersistent(false);
    			director.runUntilDeadlockInOneIteration.setToken(BooleanToken.TRUE);
    			_addedDirectors.add(director);
    		}
    	}
        	
        super.preinitialize();
    }
    
    /** Remove all DDF directors that were added to refinements in preinitialize(). */
    @Override
    public void wrapup() throws IllegalActionException {

    	super.wrapup();
    	
    	for(Director director : _addedDirectors) {
    		try {
				//System.out.println("removing ddf from " + director.getContainer().getName());
    			director.setContainer(null);
			} catch (NameDuplicationException e) {
				throw new IllegalActionException(director.getContainer(), e,
						"Error removing DDF director in choice " +
						director.getContainer().getName());
			}
    	}
    	_addedDirectors.clear();

    }

    /** Transfer data from output ports in an ExecutionChoice actor. The port
     * is a MultiComposite port that is connected (mirrored) to output ports
     * inside each of the refinements in the ExecutionChoice actor. If the
     * inside port has inputs, then transfer the tokens as usual. Otherwise,
     * if the IOType of the port is File, transfer the value of the parameter
     * with the same name as the port.
     *
     * @exception IllegalActionException If the port is not an opaque output port.
     * @param port The port to transfer tokens from.
     * @return True if the port has an inside token that was successfully
     * transferred. Otherwise return false (or throw an exception).
     */
    @Override
    protected boolean _transferOutputs(IOPort port) throws IllegalActionException {
       
        // see if the mirrored port in the refinement has inputs

        // TODO check only current refinement?
        boolean isConnected = false;
        List<IOPort> insidePorts = port.insideSourcePortList();
        for(IOPort insidePort : insidePorts) {
            // see if the RefinementPort is connected to something in the
            // refinement
            if(!insidePort.insideSourcePortList().isEmpty()) {
                isConnected = true;
                break;
            }
        }
       
        // if there are inputs, transfer as usual
        if (isConnected) {
            return super._transferOutputs(port);
        } else if(((ExecutionChoice) port.getContainer()).getPortIOType(port) == ExecutionChoice.IOType.File) {

            // true once we write a token to the port
            boolean result = false;
    
            // get the container's parameter with the same name
            // as the port's display name.
            final NamedObj namedObj = getContainer();
            final String portName = port.getDisplayName();
            final StringParameter parameter = (StringParameter) namedObj
                    .getAttribute(portName);
            // make sure we found it
            if (parameter == null) {
                throw new IllegalActionException("Missing parameter for output "
                        + portName);
            }
            
            // write the token in the parameter to the port
            final Token token = parameter.getToken();
            for (int i = 0; i < port.getWidth(); i++) {
                port.send(i, token);
                result = true;
            }
    
            return result;
        }
        
        return false;
    }
    
    /** A list of directors that were added to refinements in preinitialize(). */
    private List<Director> _addedDirectors = new LinkedList<Director>();

}
