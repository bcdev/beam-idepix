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
package org.esa.beam.idepix.operators;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.jnn.Jnn;
import com.bc.jnn.JnnException;
import com.bc.jnn.JnnNet;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.gpf.operators.meris.MerisBasisOp;
import org.esa.beam.gpf.operators.standard.BandMathsOp;
import org.esa.beam.meris.l2auxdata.L2AuxData;
import org.esa.beam.meris.l2auxdata.L2AuxdataProvider;
import org.esa.beam.util.math.MathUtils;

import java.awt.Rectangle;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Operator for computing surface pressure with optional straylight correction (FUB NN algorithm).
 *
 * @author Olaf Danne
 * @version $Revision: 6676 $ $Date: 2009-10-27 16:57:46 +0100 (Di, 27 Okt 2009) $
 */
@OperatorMetadata(alias = "idepix.SurfacePressureFub",
        version = "1.0",
        authors = "Olaf Danne",
        copyright = "(c) 2008 by Brockmann Consult",
        description = "This operator computes surface pressure with FUB NN algorithm.")
public class SurfacePressureFubOp extends MerisBasisOp {
	@SourceProduct(alias="l1b", description = "The source product.")
    Product sourceProduct;
	@SourceProduct(alias="cloud")
    private Product cloudProduct;
    @TargetProduct(description = "The target product.")
    Product targetProduct;
    @Parameter(description="If 'true' the algorithm will apply straylight correction.", defaultValue="false")
    public boolean straylightCorr = false;
    @Parameter(description="If 'true' the algorithm will apply Tropical instead of USS atmosphere model.", defaultValue="false")
    public boolean tropicalAtmosphere = false;
    
//    private static final String INVALID_EXPRESSION = "l1_flags.INVALID or not l1_flags.LAND_OCEAN";
    private static final String INVALID_EXPRESSION = "l1_flags.INVALID";
    
    private static final String NEURAL_NET_TRP_FILE_NAME = "SP_FUB_trp.nna";
    private static final String NEURAL_NET_USS_FILE_NAME = "SP_FUB_uss.nna";  // changed to US standard atm., 18/03/2009
    private static final String STRAYLIGHT_COEFF_FILE_NAME = "stray_ratio.d";
    private static final String STRAYLIGHT_CORR_WAVELENGTH_FILE_NAME = "lambda.d";
    
    private static final int BB760 = 10;
    private static final int DETECTOR_LENGTH_RR = 925;
    
    private float[] straylightCoefficients = new float[DETECTOR_LENGTH_RR]; // reduced resolution only!
    private float[] straylightCorrWavelengths = new float[DETECTOR_LENGTH_RR];

    private L2AuxData auxData;
    private Band invalidBand;


    @Override
    public void initialize() throws OperatorException {
        try {
			initAuxData();
			readStraylightCoeff();
			readStraylightCorrWavelengths();
		} catch (Exception e) {
			throw new OperatorException("Failed to load aux data:\n" + e.getMessage());
		}
        createTargetProduct();
    }
    
    private JnnNet loadNeuralNet() throws IOException, JnnException {

        InputStream inputStream;
        JnnNet neuralNet = null;
        if (tropicalAtmosphere) {
            inputStream = SurfacePressureFubOp.class.getResourceAsStream(NEURAL_NET_TRP_FILE_NAME);
        } else {
            inputStream = SurfacePressureFubOp.class.getResourceAsStream(NEURAL_NET_USS_FILE_NAME);
        }
        final InputStreamReader reader = new InputStreamReader(inputStream);

        try {
            Jnn.setOptimizing(true);
            neuralNet = Jnn.readNna(reader);
        } finally {
            reader.close();
        }
        return neuralNet;
    }

    private void createTargetProduct() throws OperatorException {
        targetProduct = createCompatibleProduct(sourceProduct, "MER_PSURF_NN", "MER_L2");
        targetProduct.addBand("surface_press_fub", ProductData.TYPE_FLOAT32);

        BandMathsOp bandArithmeticOp =
            BandMathsOp.createBooleanExpressionBand(INVALID_EXPRESSION, sourceProduct);
        invalidBand = bandArithmeticOp.getTargetProduct().getBandAt(0);
    }
    
    private void initAuxData() throws Exception {
        try {
            L2AuxdataProvider auxdataProvider = L2AuxdataProvider.getInstance();
            auxData = auxdataProvider.getAuxdata(sourceProduct);
        } catch (Exception e) {
            throw new OperatorException("Failed to load L2AuxData:\n" + e.getMessage(), e);
        }

    }
    
    /**
     * This method reads the straylight correction coefficients (RR only!)
     * 
     * @throws IOException
     */
    private void readStraylightCoeff() throws IOException {
        
        final InputStream inputStream = SurfacePressureFubOp.class.getResourceAsStream(STRAYLIGHT_COEFF_FILE_NAME);
        
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        for (int i = 0; i < straylightCoefficients.length; i++) {
            String line = bufferedReader.readLine();
            line = line.trim();
            straylightCoefficients[i] = Float.parseFloat(line);
        }
        inputStream.close();
    }
    
    /**
     * This method reads the straylight correction wavelengths (RR only!)
     * 
     * @throws IOException
     */
    private void readStraylightCorrWavelengths() throws IOException {
        
        final InputStream inputStream = SurfacePressureFubOp.class.getResourceAsStream(STRAYLIGHT_CORR_WAVELENGTH_FILE_NAME);
        
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        for (int i = 0; i < straylightCorrWavelengths.length; i++) {
            String line = bufferedReader.readLine();
            line = line.trim();
            straylightCorrWavelengths[i] = Float.parseFloat(line);
        }
        inputStream.close();
    }

//    @Override
    public void computeTile(Band band, Tile targetTile, ProgressMonitor pm) throws OperatorException {
    	
    	Rectangle rectangle = targetTile.getRectangle();
        pm.beginTask("Processing frame...", rectangle.height);

        try {
            // declare this locally since class JnnNet is not thread safe!!
            JnnNet neuralNet;
            try {
                neuralNet = loadNeuralNet();
            } catch (Exception e) {
                if (tropicalAtmosphere) {
                    throw new OperatorException("Failed to load neural net SP_FUB_trp.nna:\n" + e.getMessage());
                } else {
                    throw new OperatorException("Failed to load neural net SP_FUB_uss.nna:\n" + e.getMessage());
                }
            }

        	Tile detector = getSourceTile(sourceProduct.getBand(EnvisatConstants.MERIS_DETECTOR_INDEX_DS_NAME), rectangle, pm);
        	Tile sza = getSourceTile(sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME), rectangle, pm);
			Tile saa = getSourceTile(sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_AZIMUTH_DS_NAME), rectangle, pm);
			Tile vza = getSourceTile(sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_ZENITH_DS_NAME), rectangle, pm);
			Tile vaa = getSourceTile(sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_AZIMUTH_DS_NAME), rectangle, pm);
			
			Band band10 = sourceProduct.getBand("radiance_10");
			Tile toar10 = getSourceTile(band10, rectangle, pm);
			Band band11 = sourceProduct.getBand("radiance_11");
			Tile toar11 = getSourceTile(band11, rectangle, pm);
			Band band12 = sourceProduct.getBand("radiance_12");
			Tile toar12 = getSourceTile(band12, rectangle, pm);
			
			Tile isInvalid = getSourceTile(invalidBand, rectangle, pm);
			
			Tile cloudFlags = getSourceTile(cloudProduct.getBand(IdepixCloudClassificationOp.CLOUD_FLAGS), rectangle, pm);

            final double[] nnIn = new double[7];
            final double[] nnOut = new double[1];

            int i = 0;
			for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
				for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
					if (pm.isCanceled()) {
						break;
					}
					final int detectorXY = detector.getSampleInt(x, y);
					if (isInvalid.getSampleBoolean(x, y)) {
						targetTile.setSample(x, y, 0);
					} else {
						final float szaDeg = sza.getSampleFloat(x, y);
						final double szaRad = szaDeg * MathUtils.DTOR;
						final float vzaDeg = vza.getSampleFloat(x, y);
						final double vzaRad = vzaDeg * MathUtils.DTOR;
						
						double lambda = auxData.central_wavelength[BB760][detectorXY];
						final double fraction = (lambda - 753.75)/(778.0 - 753.75);
						final double toar10XY = toar10.getSampleDouble(x, y)/band10.getSolarFlux();
						final double toar11XY = toar11.getSampleDouble(x, y)/band11.getSolarFlux();
						final double toar12XY = toar12.getSampleDouble(x, y)/band12.getSolarFlux();
						final double toar11XY_na = (1.0 - fraction)*toar10XY + fraction*toar12XY;
						
						double stray = 0.0;
						if (straylightCorr) {
							// apply FUB straylight correction...
							stray = straylightCoefficients[detectorXY] * toar10XY;
							lambda = straylightCorrWavelengths[detectorXY];
						}
						
						final double toar11XY_corrected = toar11XY + stray;
						
						// apply FUB NN...
						nnIn[0] = toar10XY;
						nnIn[1] = toar11XY_corrected / toar11XY_na;
						nnIn[2] = 0.15; // AOT
						nnIn[3] = Math.cos(szaRad);
						nnIn[4] = Math.cos(vzaRad);
						final float vaaDegXY = vaa.getSampleFloat(x, y);
						final float saaDegXY = saa.getSampleFloat(x, y);
						nnIn[5] = Math.sin(vzaRad)
								* Math.cos(MathUtils.DTOR * (vaaDegXY - saaDegXY));
						nnIn[6] = lambda;

						neuralNet.process(nnIn, nnOut);
						targetTile.setSample(x, y, nnOut[0]);
					}
					i++;
				}
				pm.worked(1);
			}
        } catch (Exception e) {
        	throw new OperatorException("Failed to process Surface Pressure FUB:\n" + e.getMessage(), e);
		} finally {
            pm.done();
        }
    }
    
    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {
        public Spi() {
            super(SurfacePressureFubOp.class, "Meris.SurfacePressureFub");
        }
    }
}
