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
package org.esa.beam.idepix.ui.actions;

import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.ui.DefaultSingleTargetProductDialog;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.idepix.algorithms.cawa.CawaOp;
import org.esa.beam.idepix.algorithms.landsat8.Landsat8Op;
import org.esa.beam.idepix.ui.IdepixLandsat8Dialog;
import org.esa.beam.visat.actions.AbstractVisatAction;

/**
 * Idepix action for CoastColour algorithm.
 *
 * @author Olaf Danne
 */
public class IdepixLandsat8Action extends AbstractVisatAction {
    @Override
    public void actionPerformed(CommandEvent commandEvent) {
//        final DefaultSingleTargetProductDialog dialog =
//            new DefaultSingleTargetProductDialog(OperatorSpi.getOperatorAlias(Landsat8Op.class),
//                    getAppContext(),
//                    "Idepix - Pixel Identification and Classification (Landsat8 mode)",
//                    "IdepixPlugIn");

        final IdepixLandsat8Dialog dialog =
                new IdepixLandsat8Dialog(OperatorSpi.getOperatorAlias(Landsat8Op.class),
                                                     getAppContext(),
                                                     "Idepix - Pixel Identification and Classification (Landsat8 mode)",
                                                     "IdepixPlugIn");
        System.setProperty("gpfMode", "GUI");
        dialog.setTargetProductNameSuffix("_IDEPIX");
        dialog.show();
    }

}
