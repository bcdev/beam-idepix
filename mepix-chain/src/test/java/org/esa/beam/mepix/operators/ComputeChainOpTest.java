package org.esa.beam.mepix.operators;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import junit.framework.TestCase;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.meris.brr.HelperFunctions;
import org.esa.beam.util.math.MathUtils;

import com.bc.jnn.Jnn;
import com.bc.jnn.JnnException;
import com.bc.jnn.JnnNet;

/**
 * Tests for class {@link ComputeChainOp}.
 *
 * @author Olaf Danne
 * @version $Revision: 1.1 $ $Date: 2008-10-09 16:43:53 $
 */
public class ComputeChainOpTest extends TestCase {
	private JnnNet neuralNet;

    public void testSomething() {
    }
    
    public void testSurfacePressureFub() {
    	
        final InputStream inputStream = SurfacePressureFubOp.class.getResourceAsStream("SP_FUB_trp.nna");
        final InputStreamReader reader = new InputStreamReader(inputStream);
        
        try {
            Jnn.setOptimizing(true);
            neuralNet = Jnn.readNna(reader);
        } catch (IOException e) {
			fail(e.getMessage());
		} catch (JnnException e) {
			fail(e.getMessage());
		} finally {
            try {
				reader.close();
			} catch (IOException e) {
				fail(e.getMessage());
			}
        }
        
        final double[][] nnIn = new double[5][7];
        final double[] nnOut = new double[1];
        final double[] result = new double[5];

        nnIn[0][1] = 0.29;
        nnIn[1][1] = 0.32;
        nnIn[2][1] = 0.34;
        nnIn[3][1] = 0.37;
        nnIn[4][1] = 0.4;
        
		for (int i=0; i<5; i++) {
			nnIn[i][0] = 0.15;
			nnIn[i][2] = 0.15;
			nnIn[i][3] = 0.866025;
			nnIn[i][4] = 0.866025;
			nnIn[i][5] = 0.5;
			nnIn[i][6] = 761.5;
				
			neuralNet.process(nnIn[i], nnOut);
			result[i] = nnOut[0];
		}
//			the resulting numbers should be
//			990.939      904.507      853.541      781.458      714.388
		
		assertEquals(990.939, result[0], 0.001);
		assertEquals(904.507, result[1], 0.001);
		assertEquals(853.541, result[2], 0.001);
		assertEquals(781.458, result[3], 0.001);
		assertEquals(714.388, result[4], 0.001);
    }
    
    public void testSurfacePressureNewLise() {
    	
    	// not needed any more - now computed within LisePressureOp
    	
//    	SurfacePressureNewOp op = new SurfacePressureNewOp();
//    	try {
//			op.readO2AtmTransmittances();
//		} catch (IOException e) {
//			fail();
//		}
//    	
//    	final float szaDeg = 12.6f;
//		final float vzaDeg = 36.5f;
//		final double w0 = 761.75d;
//		final double ratio = 0.3265d;
//		
//		final int gaussIndexS = op.getNearestGaussIndex(szaDeg);
//		final int gaussIndexV = op.getNearestGaussIndex(vzaDeg);
//		
//		final int filterIndex = op.getNearestFilterIndex(w0);
//		
//		final double pressure = op.getPressure(w0, szaDeg, vzaDeg, filterIndex, gaussIndexS, gaussIndexV, ratio);
//		
//		assertEquals(878.33, pressure, 0.1);
    }
    
    public void testPressuresLise() {
    	
    	LisePressureOp op = new LisePressureOp();
    	try {
    		op.readLiseAuxdata();
		} catch (IOException e) {
			fail();
		}
    	
		// first dataset:
		// 2.841 2.841 90. 824 1013.25 0.14586 0.07864 0.14586
		
		float thetas = 2.841f;
		float thetav = 2.841f;
		float phi = 90.0f;
		int   idetector = 824;
		double rho_meris753 = 0.14586d;
		double rho_meris761 = 0.07864d;
		double rho_meris778 = 0.14586d;
		
		float altitude = 0.0f;
		float ecmwfPressure = 1013.25f;
		double pressureScaleHeight = 8340.0d;  // taken from auxdata
		double airMass = HelperFunctions.calculateAirMass(thetav, thetas);
		
		final double w0 = 761.68572998; // auxData.central_wavelength[BB760][idetector]
		
		double csza = Math.cos(thetas * MathUtils.DTOR);
		double cvza = Math.cos(thetav * MathUtils.DTOR);
		double ssza = Math.sin(thetas * MathUtils.DTOR);
		double svza = Math.sin(thetav * MathUtils.DTOR);
		double azimDiff = MathUtils.DTOR * (phi);
		
		// test PScatt:
		double pScatt = op.computeLisePressures(3, thetas, thetav, csza, cvza, ssza, svza, azimDiff, 
				rho_meris753, rho_meris761, rho_meris778, w0, altitude, ecmwfPressure, pressureScaleHeight, airMass);
				
//		the resulting number should be
//		362.50
		assertEquals(362.50, pScatt, 1.0);
		
		// test P2:
		double p2 = op.computeLisePressures(2, thetas, thetav, csza, cvza, ssza, svza, azimDiff, 
				rho_meris753, rho_meris761, rho_meris778, w0, altitude, ecmwfPressure, pressureScaleHeight, airMass);
		assertEquals(504.2, p2, 1.0);
		
		// test PSurf:
		double pSurf = op.computeLisePressures(1, thetas, thetav, csza, cvza, ssza, svza, azimDiff, 
				rho_meris753, rho_meris761, rho_meris778, w0, altitude, ecmwfPressure, pressureScaleHeight, airMass);
		assertEquals(479.2, pSurf, 1.0);
		
		// test P1:
		double p1 = op.computeLisePressures(0, thetas, thetav, csza, cvza, ssza, svza, azimDiff, 
				rho_meris753, rho_meris761, rho_meris778, w0, altitude, ecmwfPressure, pressureScaleHeight, airMass);
		assertEquals(500.8, p1, 1.0);
		
		//  another test for PScatt with second dataset:
		// 6.521 2.841 90. 824 1013.25 0.14673 0.07913 0.14673
		
		thetas = 6.521f;
		thetav = 2.841f;
		phi = 90.0f;
		idetector = 824;
		rho_meris753 = 0.14673f;
		rho_meris761 = 0.07913f;
		rho_meris778 = 0.14673f;
		
		csza = Math.cos(thetas * MathUtils.DTOR);
		cvza = Math.cos(thetav * MathUtils.DTOR);
		ssza = Math.sin(thetas * MathUtils.DTOR);
		svza = Math.sin(thetav * MathUtils.DTOR);
		azimDiff = MathUtils.DTOR * (phi);
		
		pScatt = op.computeLisePressures(3, thetas, thetav, csza, cvza, ssza, svza, azimDiff, 
				rho_meris753, rho_meris761, rho_meris778, w0, altitude, ecmwfPressure, pressureScaleHeight, airMass);
		
//		the resulting number should be
//		379.09
		assertEquals(379.09, pScatt, 1.0);
    }
}
