/* 
 * Copyright (C) 2002-2008 by Brockmann Consult
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation. This program is distributed in the hope it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.esa.beam.idepix.ui;

import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.ui.DefaultSingleTargetProductDialog;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.idepix.algorithms.globalbedo.GlobAlbedoOp;
import org.esa.beam.idepix.IdepixConstants;
import org.esa.beam.visat.actions.AbstractVisatAction;

/**
 * Idepix action for GlobAlbedo algorithm.
 *
 * @author Olaf Danne
 */
public class IdepixGlobAlbedoAction extends AbstractVisatAction {
    @Override
    public void actionPerformed(CommandEvent commandEvent) {
        final IdepixDialog dialog =
            new IdepixDialog(OperatorSpi.getOperatorAlias(GlobAlbedoOp.class),
                    getAppContext(),
                    "IDEPIX Pixel Identification Tool - GlobAlbedo Algorithm -  " + IdepixConstants.IDEPIX_VERSION,
                    "idepixChain","");

//        final DefaultSingleTargetProductDialog dialog =
//                new DefaultSingleTargetProductDialog(OperatorSpi.getOperatorAlias(GlobAlbedoOp.class),
//                                 getAppContext(),
//                                 "IDEPIX Pixel Identification Tool - GlobAlbedo Algorithm -  " + IdepixConstants.IDEPIX_VERSION,
//                                 "idepixChain");
        dialog.show();
    }

}
