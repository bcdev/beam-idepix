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
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.idepix.operators.ComputeChainOp;
import org.esa.beam.idepix.operators.IdepixConstants;
import org.esa.beam.visat.actions.AbstractVisatAction;

/**
 * Action for computing an operator chain.
 *
 * @author Olaf Danne
 * @version $Revision: 6676 $ $Date: 2009-10-27 16:57:46 +0100 (Di, 27 Okt 2009) $
 * @since BEAM 4.2
 */
public class ComputeChainAction extends AbstractVisatAction {
    @Override
    public void actionPerformed(CommandEvent commandEvent) {
//        JAI.getDefaultInstance().getTileScheduler().setParallelism(1); // test!!
        final IdepixDialog dialog =
            new IdepixDialog(OperatorSpi.getOperatorAlias(ComputeChainOp.class),
                    getAppContext(),
                    "IDEPIX Pixel Identification Tool - " + IdepixConstants.IDEPIX_VERSION,
                    "idepixChain","");
        dialog.show();
    }

}