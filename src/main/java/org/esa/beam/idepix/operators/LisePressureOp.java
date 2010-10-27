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
import org.esa.beam.gpf.operators.standard.BandMathsOp;
import org.esa.beam.meris.brr.HelperFunctions;
import org.esa.beam.meris.brr.Rad2ReflOp;
import org.esa.beam.meris.brr.RayleighCorrection;
import org.esa.beam.meris.l2auxdata.L2AuxData;
import org.esa.beam.meris.l2auxdata.L2AuxdataProvider;
import org.esa.beam.util.math.FractIndex;
import org.esa.beam.util.math.Interp;
import org.esa.beam.util.math.LUT;
import org.esa.beam.util.math.MathUtils;

import java.awt.Rectangle;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.StringTokenizer;

/**
 * Operator for computing aerosol apparent pressure (LISE algorithm).
 * todo: cleanup!!! --> e.g  move I/O methods to an auxdata class
 *
 * @author Olaf Danne
 * @version $Revision: 6824 $ $Date: 2009-11-03 16:02:02 +0100 (Di, 03 Nov 2009) $
 */
@OperatorMetadata(alias = "idepix.LisePressure",
        version = "1.0",
        authors = "Olaf Danne",
        copyright = "(c) 2008 by Brockmann Consult",
        description = "This operator computes aerosol apparent pressure with LISE algorithm.")
public class LisePressureOp extends BasisOp {
	@SourceProduct(alias="l1b", description = "The source product.")
    Product sourceProduct;
	@SourceProduct(alias="rhotoa")
    private Product rhoToaProduct;
    @TargetProduct(description = "The target product.")
    Product targetProduct;
    
    @Parameter(description="If 'true' the algorithm will apply straylight correction.", defaultValue="false")
    public boolean straylightCorr = false;
    @Parameter(description="If 'true' the algorithm will compute LISE P1.", defaultValue="true")
    public boolean outputP1 = true;
    @Parameter(description="If 'true' the algorithm will compute LISE surface pressure.", defaultValue="true")
    public boolean outputPressureSurface = true;
    @Parameter(description="If 'true' the algorithm will compute LISE P2.", defaultValue="true")
    public boolean outputP2 = true;
    @Parameter(description="If 'true' the algorithm will compute LISE PScatt.", defaultValue="true")
    public boolean outputPScatt = true;
    @Parameter(description="If 'true' the algorithm will compute LISE PScatt.", defaultValue="true")
    public boolean l2CloudDetection = true;
    
    public static final String PRESSURE_LISE_P1 = "p1_lise";
    public static final String PRESSURE_LISE_PSURF = "surface_press_lise";
    public static final String PRESSURE_LISE_P2 = "p2_lise";
    public static final String PRESSURE_LISE_PSCATT = "pscatt_lise";
    
//    private static final String INVALID_EXPRESSION = "l1_flags.INVALID or l1_flags.LAND_OCEAN";
    private static final String INVALID_EXPRESSION = "l1_flags.INVALID";
    private static final String INVALID_EXPRESSION_OCEAN = "l1_flags.INVALID or l1_flags.LAND_OCEAN";
    private static final String INVALID_EXPRESSION_LAND = "l1_flags.INVALID or not l1_flags.LAND_OCEAN";
    
    private static final String O2_RAYLEIGH_TRANSMITTANCES_FILE_NAME = "transmittances_O2_Ray_OCEAN_21f.d";
    private static final String O2_ATM_TRANSMITTANCES_FILE_NAME = "transmittances_O2_RSf_OCEAN_21f.d";
    private static final String O2_FRESNEL_TRANSMITTANCES_FILE_NAME = "transmittances_O2_fresnel_OCEAN_21f.d";
    private static final String O2_ATM_AEROSOL_TRANSMITTANCES_FILE_NAME = "transmittances_O2_atm_aer_OCEAN_21f.d";
    private static final String SPECTRAL_COEFFICIENTS_FILE_NAME = "meris_band_o2.d";
    private static final String FRESNEL_COEFFICIENTS_FILE_NAME = "fresnel_coeff.d";
    private static final String C_COEFFICIENTS_FILE_NAME = "c_coeff_lise.d";
    private static final String AIRMASSES_LISE_FILE_NAME = "airmasses_lise.d";
    private static final String RHO_TOA_LISE_FILE_NAME = "rho_toa_lise.d";
    private static final String APF_JUNGE_FILE_NAME = "apf_junge_10.d";
    private static final String STRAYLIGHT_COEFF_FILE_NAME = "stray_ratio.d";
    private static final String STRAYLIGHT_CORR_WAVELENGTH_FILE_NAME = "lambda.d";
    
    private static final int BB760 = 10;
    private static final int NFILTER = 21;
    private static final int NLAYER = 21;
    
    private static final int C_NUM_M = 6;
    private static final int C_NUM_RHO = 6;
    
    /* Number of bands in L1b */
    private static final int L1_BAND_NUM = 15;
    
    private static final int NPIXEL = 4625;
    private double[] spectralCoefficients = new double[NPIXEL];
    
    private static final int NFRESNEL = 91;
    private double[] fresnelCoefficients = new double[NFRESNEL];
    
    private static final int NJUNGE = 181;
    private double[] apfJunge = new double[NJUNGE];
    
    private static final int DETECTOR_LENGTH_RR = 925;
    private float[] straylightCoefficients = new float[DETECTOR_LENGTH_RR]; // reduced resolution only!
    private float[] straylightCorrWavelengths = new float[DETECTOR_LENGTH_RR];
    
    /**
     * Rayleigh Scattering Coeff series, number of coefficient Order
     */
    final int RAYSCATT_NUM_ORD = 4;
    /**
     * Rayleigh Scattering Coeff series, number of Series
     */
    final int RAYSCATT_NUM_SER = 3;
    
    private static final double standardSeaSurfacePressure = 1013.25;
    
    // central wavelength for each detector at 761 nm
    private static final double[] o2FilterWavelengths = {
    	760.7d, 760.8d, 760.9d, 761.0d, 761.1d, 761.2d, 761.3d, 
    	761.4d, 761.5d, 761.6d, 761.7d, 761.8d, 761.9d, 762.0d, 
    	762.1d, 762.2d, 762.3d, 762.4d, 762.5d, 762.6d, 762.7d
    };
    
    // gaussian angle grid point for the Rayleigh TO2
    private static final double[] gaussianAngles = {
    	2.84d, 6.52d, 10.22d, 13.93d, 17.64d, 21.35d, 25.06d, 28.77d, 
    	32.48d, 36.19d, 39.90d, 43.61d, 47.32d, 51.03d, 54.74d, 58.46d, 
    	62.17d, 65.88d, 69.59d, 73.30d, 77.01d, 80.72d, 84.43d, 88.14d
    };
    
    private double[][][] to2Ray = new double[NFILTER][24][24];           // Rayleigh TO2
    private double[][][][] to2Atm = new double[NFILTER][NLAYER][24][24]; // Atmospheric TO2 at 21 pressure levels
    private double[][][] to2Fresnel = new double[NFILTER][24][24];       // Fresnel TO2
    private double[][][] to2AtmAerosol = new double[NFILTER][24][24];    // Aerosol atmospheric TO2
    private double[] pressureLevels = new double[NLAYER];                // pressure standard levels
    private double[] cCoeff = new double[NFILTER*6*6];             		 // C coefficients (LISE)
    private double[] airMassesLise = new double[6];                      // air masses (LISE)
    private double[] rhoToaLise = new double[6];                         // rhoToa (LISE)
    
//    protected RayleighCorrection rayleighCorrection;
    
    // index 0: W0 wavelength for band 11
    // indices 1,2:  bracketted wavemengths for W0
//    private double[] band11CentralWavelengths = new double[3];


    L2AuxData auxData;
    private Band invalidBand;
    private Band invalidBandOcean;
    private Band invalidBandLand;
	private LUT coeffLUT;
    
    
    @Override
    public void initialize() throws OperatorException {
    	if (sourceProduct != null) {
    		sourceProduct.setPreferredTileSize(64, 64);
    		createTargetProduct();
    	}
    		
        try {
        	initL2AuxData();
			readLiseAuxdata();
			if (straylightCorr) {
				readStraylightCoeff();
				readStraylightCorrWavelengths();
			}
		} catch (Exception e) {
			throw new OperatorException("Failed to load aux data:\n" + e.getMessage());
		}
    }
    
    /**
     * This method initialises the L2 auxdata.
     * 
     * @throws OperatorException
     */
    public void initL2AuxData() throws OperatorException {
        try {
            L2AuxdataProvider auxdataProvider = L2AuxdataProvider.getInstance();
            auxData = auxdataProvider.getAuxdata(sourceProduct);
//            rayleighCorrection = new RayleighCorrection(auxData);
        } catch (Exception e) {
            throw new OperatorException("Failed to load L2AuxData:\n" + e.getMessage(), e);
        }

    }
    
    /**
     * This method reads additional auxdata provided by LISE.
     * 
     * @throws IOException
     */
    public void readLiseAuxdata() throws IOException {
		readSpectralCoefficients();
		readFresnelCoefficients();
		readCCoefficients();
		readApfJunge();
		readO2RayleighTransmittances();
		readO2AtmTransmittances();
		readO2FresnelTransmittances();
		readO2AtmAerosolTransmittances();
	}
    
    /**
     * This method reads the spectral characterization coefficients
     * 
     * @throws IOException
     */
    private void readSpectralCoefficients() throws IOException {
        final InputStream inputStream = LisePressureOp.class.getResourceAsStream(SPECTRAL_COEFFICIENTS_FILE_NAME);
        
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        try {
			for (int i = 0; i < spectralCoefficients.length/4; i++) {
				double coeffAverage = 0.0;
				for (int j=0; j<4; j++) {
			        String line = bufferedReader.readLine();
			        line = line.trim();
			        coeffAverage += Float.parseFloat(line);
				}
				spectralCoefficients[i] = coeffAverage/4.0;
			}
		} catch (IOException e) {
			throw new OperatorException("Failed to load Spectral Coefficients:\n" + e.getMessage(), e);
		} catch (NumberFormatException e) {
			throw new OperatorException("Failed to load Spectral Coefficients:\n" + e.getMessage(), e);
		} finally {
			inputStream.close();
		}
    }
    
    /**
     * This method reads the Fresnel coefficients
     * 
     * @throws IOException
     */
    private void readFresnelCoefficients() throws IOException {
        final InputStream inputStream = LisePressureOp.class.getResourceAsStream(FRESNEL_COEFFICIENTS_FILE_NAME);
        
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        try {
	        for (int i = 0; i < fresnelCoefficients.length; i++) {
	            String line = bufferedReader.readLine();
	            line = line.trim();
	            fresnelCoefficients[i] = Float.parseFloat(line);
	        }
        } catch (IOException e) {
			throw new OperatorException("Failed to load Fresnel Coefficients:\n" + e.getMessage(), e);
		} catch (NumberFormatException e) {
			throw new OperatorException("Failed to load Fresnel Coefficients:\n" + e.getMessage(), e);
		} finally {
			inputStream.close();
		}
    }
    
    /**
     * This method reads the LISE air masses
     * 
     * @throws IOException
     */
    private void readAirMassesLise() throws IOException {
    	final InputStream inputStream = LisePressureOp.class.getResourceAsStream(AIRMASSES_LISE_FILE_NAME);
        
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        try {
	        for (int i = 0; i < airMassesLise.length; i++) {
	            String line = bufferedReader.readLine();
	            line = line.trim();
	            airMassesLise[i] = Float.parseFloat(line);
	        }
        } catch (IOException e) {
			throw new OperatorException("Failed to load LISE air masses:\n" + e.getMessage(), e);
		} catch (NumberFormatException e) {
			throw new OperatorException("Failed to load LISE air masses:\n" + e.getMessage(), e);
		} finally {
			inputStream.close();
		}
        inputStream.close();
    }
    
    /**
     * This method reads the LISE rho toa values
     * 
     * @throws IOException
     */
    private void readRhoToaLise() throws IOException {
    	final InputStream inputStream = LisePressureOp.class.getResourceAsStream(RHO_TOA_LISE_FILE_NAME);
        
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        try {
	        for (int i = 0; i < rhoToaLise.length; i++) {
	            String line = bufferedReader.readLine();
	            line = line.trim();
	            rhoToaLise[i] = Float.parseFloat(line);
	        }
        } catch (IOException e) {
			throw new OperatorException("Failed to load LISE rho toa:\n" + e.getMessage(), e);
		} catch (NumberFormatException e) {
			throw new OperatorException("Failed to load LISE rho toa:\n" + e.getMessage(), e);
		} finally {
			inputStream.close();
		}
        inputStream.close();
        inputStream.close();
    }
    
    /**
     * This method reads the C coefficients
     * 
     * @throws IOException
     */
    private void readCCoefficients() throws IOException {
        final InputStream inputStream = LisePressureOp.class.getResourceAsStream(C_COEFFICIENTS_FILE_NAME);
        
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        StringTokenizer st;
        String token;
        
        readAirMassesLise();
        readRhoToaLise();
        
        try {
        	int index = 0;
			for (int i = 0; i < NFILTER; i++) {
			    bufferedReader.readLine();
		    	for (int k = 0; k < 6; k++) {
		    		String line = bufferedReader.readLine();
		            line = line.trim();
		    		st = new StringTokenizer(line, " ", false);
		    		int mIndex = 0;
		    		while (st.hasMoreTokens() && mIndex < 7) {
		                token = st.nextToken();
		                if (mIndex > 0) {
							int cCoeffInt = Integer.parseInt(token);
							if (cCoeffInt == -999)
								cCoeffInt = 1000;
							cCoeff[index] = 0.001*cCoeffInt;
							index++;
						}
		                mIndex++;
		            }
		    	}
			}
			final int[] cCoeffSizes = new int[]{NFILTER, C_NUM_M, C_NUM_RHO};
			coeffLUT = new LUT(cCoeffSizes, cCoeff);
			coeffLUT.setTab(0, null); // no tabulated values needed for 1st dimension
			coeffLUT.setTab(2, airMassesLise);
			coeffLUT.setTab(1, rhoToaLise);
			
		} catch (IOException e) {
			throw new OperatorException("Failed to load C Coefficients:\n" + e.getMessage(), e);
		} catch (NumberFormatException e) {
			throw new OperatorException("Failed to load C Coefficients:\n" + e.getMessage(), e);
		} finally {
			inputStream.close();
		}
		
    }
    
    /**
     * This method reads the APF of the Junge aerosol model nb 10
     * 
     * @throws IOException
     */
    private void readApfJunge() throws IOException {
        final InputStream inputStream = LisePressureOp.class.getResourceAsStream(APF_JUNGE_FILE_NAME);
        
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        for (int i = 0; i < apfJunge.length; i++) {
            String line = bufferedReader.readLine();
            line = line.trim();
            apfJunge[i] = Float.parseFloat(line);
        }
        inputStream.close();
        
    }
    
    /**
     * This method reads the O2 Rayleigh transmittance
     * 
     * @throws IOException
     */
    private void readO2RayleighTransmittances() throws IOException {
        final InputStream inputStream = LisePressureOp.class.getResourceAsStream(O2_RAYLEIGH_TRANSMITTANCES_FILE_NAME);
        
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        StringTokenizer st;
        String token;
        try {
			for (int i = 0; i < NFILTER; i++) {
			    bufferedReader.readLine();
		    	for (int k = 0; k < 24; k++) {
		    		String line = bufferedReader.readLine();
		            line = line.trim();
		    		st = new StringTokenizer(line, " ", false);
		    		int mIndex = 0;
		    		while (st.hasMoreTokens() && mIndex < 24) {
		                token = st.nextToken();
		                to2Ray[i][k][mIndex] = Double.parseDouble(token);
		                mIndex++;
		            }
		    	}
			}
		} catch (IOException e) {
			throw new OperatorException("Failed to load O2 Rayleigh Transmittances:\n" + e.getMessage(), e);
		} catch (NumberFormatException e) {
			throw new OperatorException("Failed to load O2 Rayleigh Transmittances:\n" + e.getMessage(), e);
		} finally {
			inputStream.close();
		}
    }
    
    /**
     * This method reads the O2 Atmospheric transmittance
     * 
     * @throws IOException
     */
    private void readO2AtmTransmittances() throws IOException {
        final InputStream inputStream = LisePressureOp.class.getResourceAsStream(O2_ATM_TRANSMITTANCES_FILE_NAME);
        
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        StringTokenizer st;
        String token;
        try {
			for (int i = 0; i < NLAYER; i++) {
			    String line = bufferedReader.readLine();
			    line = line.trim();
			    pressureLevels[i] = Double.parseDouble(line);
			}
			bufferedReader.readLine();
			for (int i = 0; i < NLAYER; i++) {
			    bufferedReader.readLine();
			    for (int j = 0; j < NFILTER; j++) {
			    	for (int k = 0; k < 24; k++) {
			    		String line = bufferedReader.readLine();
			            line = line.trim();
			    		st = new StringTokenizer(line, " ", false);
			    		int mIndex = 0;
			    		while (st.hasMoreTokens() && mIndex < 24) {
			                token = st.nextToken();
			                to2Atm[i][j][k][mIndex] = Double.parseDouble(token);
			                mIndex++;
			            }
			    	}
			    }
			}
		} catch (IOException e) {
			throw new OperatorException("Failed to load O2 Atmospheric Transmittances:\n" + e.getMessage(), e);
		} catch (NumberFormatException e) {
			throw new OperatorException("Failed to load O2 Atmospheric Transmittances:\n" + e.getMessage(), e);
		} finally {
			inputStream.close();
		}
    }
    
    /**
     * This method reads the O2 Aerosol Fresnel transmittance
     * 
     * @throws IOException
     */
    private void readO2FresnelTransmittances() throws IOException {
        final InputStream inputStream = LisePressureOp.class.getResourceAsStream(O2_FRESNEL_TRANSMITTANCES_FILE_NAME);
        
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        StringTokenizer st;
        String token;
        try {
			for (int i = 0; i < NFILTER; i++) {
			    bufferedReader.readLine();
		    	for (int k = 0; k < 24; k++) {
		    		String line = bufferedReader.readLine();
		            line = line.trim();
		    		st = new StringTokenizer(line, " ", false);
		    		int mIndex = 0;
		    		while (st.hasMoreTokens() && mIndex < 24) {
		                token = st.nextToken();
		                to2Fresnel[i][k][mIndex] = Double.parseDouble(token);
		                mIndex++;
		            }
		    	}
			}
		} catch (IOException e) {
			throw new OperatorException("Failed to load O2 Fresnel Transmittances:\n" + e.getMessage(), e);
		} catch (NumberFormatException e) {
			throw new OperatorException("Failed to load O2 Fresnel Transmittances:\n" + e.getMessage(), e);
		} finally {
			inputStream.close();
		}
    }
    
    /**
     * This method reads the O2 aerosol atmospheric transmittance for Ha=2km
     * 
     * @throws IOException
     */
    private void readO2AtmAerosolTransmittances() throws IOException {
        final InputStream inputStream = LisePressureOp.class.getResourceAsStream(O2_ATM_AEROSOL_TRANSMITTANCES_FILE_NAME);
        
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        StringTokenizer st;
        String token;
        try {
			for (int i = 0; i < NFILTER; i++) {
			    bufferedReader.readLine();
		    	for (int k = 0; k < 24; k++) {
		    		String line = bufferedReader.readLine();
		            line = line.trim();
		    		st = new StringTokenizer(line, " ", false);
		    		int mIndex = 0;
		    		while (st.hasMoreTokens() && mIndex < 24) {
		                token = st.nextToken();
		                to2AtmAerosol[i][k][mIndex] = Double.parseDouble(token);
		                mIndex++;
		            }
		    	}
			}
		} catch (IOException e) {
			throw new OperatorException("Failed to load O2 Atmospheric Aerosol Transmittances:\n" + e.getMessage(), e);
		} catch (NumberFormatException e) {
			throw new OperatorException("Failed to load O2 Atmospheric Aerosol Transmittances:\n" + e.getMessage(), e);
		} finally {
			inputStream.close();
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

    
    /**
     * This method returns the index of the nearest Gauss angle
     * 
     * @param thetaS
     *
     * @return index
     */
    private int getNearestGaussIndex(double thetaS) {
    	int index = -1;
    	
    	if (thetaS <= gaussianAngles[0]) {
    		index = 0;  // bug fixed: was 1 before (LB, 01.10.09) !!
    	} else if (thetaS >= gaussianAngles[23]) {
    		index = 22;
    	} else {
	    	double a = 1000.0d;
	    	for (int i=0; i<24; i++) {
	    		double d = Math.abs(thetaS - gaussianAngles[i]);
	    		if (d < a) {
	    			a = d;
	    			index = i;
	    		}
	    	}
    	}
    	
    	return index;
    }

    /**
     * This method returns the index of the nearest O2 filter wavelength
     * 
     * @param w0
     * @return index
     */
    private int getNearestFilterIndex(double w0) {
    	int index = -1;
    	
    	if (w0 <= o2FilterWavelengths[0]) {
            index = 0;  // bug fixed: was 1 before (LB, 01.10.09) !!
    	} else if (w0 >= o2FilterWavelengths[NFILTER-1]) {
    		index = NFILTER-2;
    	} else {
//    		for (int i=0; i<NFILTER-1; i++) {
    		for (int i=1; i<NFILTER; i++) { // bug fixed  (LB, 01.10.09)
	    		if (o2FilterWavelengths[i] >= w0) {
	    			index = i-1;
	    			break;
	    		}
	    	}
    	}
    	
    	return index;
    }
    
    /**
     * This method computes the pressure for:
     * 		1) The geometrical conditions (SZA, VZA) by interpolation on the 24 Gaussian angles
     * 		2) at the central wavelength of the detector by interpolation on 21 filters
     * 
     * @param w0
     * @param thetaS - sun zenith angle
     * @param thetaV - view zenith angle
     * @param filterIndex - index of nearest filter
     * @param isza - index of nearest gaussian angle for thetaS
     * @param ivza - index of nearest gaussian angle for thetaV
     * @param ratio - toa11/toa10
     * @return
     */
    private double getPressure(double centralWvl1, double centralWvl2,
            double w0, double thetaS, double thetaV, int filterIndex, int isza, int ivza, double ratio) {
    	double pressure = 0.0; // pressure to compute
    	
    	double x0, x1, x2;
    	double s1, s2;
    	double z1, z2;
    	
    	double y1, y2;
    	
    	// first filter
    	x0 = thetaV;
    	x1 = gaussianAngles[ivza];
    	x2 = gaussianAngles[ivza+1];
    	
    	y1 = computePressure(filterIndex, isza, ivza, ratio);
    	y2 = computePressure(filterIndex, isza, ivza+1, ratio);
    	z1 = linearInterpol(x0, x1, x2, y1, y2);
    	y1 = computePressure(filterIndex, isza+1, ivza, ratio);
    	y2 = computePressure(filterIndex, isza+1, ivza+1, ratio);
    	z2 = linearInterpol(x0, x1, x2, y1, y2);
    	
    	x0 = thetaS;
    	x1 = gaussianAngles[isza];
    	x2 = gaussianAngles[isza+1];
    	s1 = linearInterpol(x0, x1, x2, z1, z2);
    	
    	// second filter
    	x0 = thetaV;
    	x1 = gaussianAngles[ivza];
    	x2 = gaussianAngles[ivza+1];
    	
    	y1 = computePressure(filterIndex+1, isza, ivza, ratio);
    	y2 = computePressure(filterIndex+1, isza, ivza+1, ratio);
    	z1 = linearInterpol(x0, x1, x2, y1, y2);
    	y1 = computePressure(filterIndex+1, isza+1, ivza, ratio);
    	y2 = computePressure(filterIndex+1, isza+1, ivza+1, ratio);
    	z2 = linearInterpol(x0, x1, x2, y1, y2);
    	
    	x0 = thetaS;
    	x1 = gaussianAngles[isza];
    	x2 = gaussianAngles[isza+1];
    	s2 = linearInterpol(x0, x1, x2, z1, z2);
    	
    	pressure = linearInterpol(w0,
				centralWvl1, centralWvl2, s1, s2);
    	
    	return pressure;
    }
    
    /**
     * This method returns the value of the pressure from the 761/753 ratio
     * 
     * @param filterIndex
     * @param isza2
     * @param ivza2
     * @param ratio
     * @return surfacePressure
     */
    private double computePressure(int filterIndex, int isza2, int ivza2, double ratio) {
    	double surfacePressure = 0.0; // surface pressure to compute
    	
    	double slope;
    	double t1, t2, t;
    	double p1, p2;
    	
    	t1 = Math.log(to2Atm[filterIndex][0][isza2][ivza2]);
    	p1 = pressureLevels[0];
    	t = Math.log(ratio);
    	
    	for (int i=1; i<NLAYER; i++) {
    		p2 = pressureLevels[i];
    		t2 = Math.log(to2Atm[filterIndex][i][isza2][ivza2]);
    		if (t >= t2) {
    			slope = (p2 - p1)/(t2 - t1);
    			surfacePressure = p2 + slope*(t - t2);
    			return surfacePressure;
    		} else {
    			t1 = t2;
    			p1 = p2;
    		}
    	}
    	p1 = pressureLevels[NLAYER-2];
    	p2 = pressureLevels[NLAYER-1];
    	t1 = Math.log(to2Atm[filterIndex][NLAYER-2][isza2][ivza2]);
    	t2 = Math.log(to2Atm[filterIndex][NLAYER-1][isza2][ivza2]);
    	
    	slope = (p2 - p1)/(t2 - t1);
    	surfacePressure = p2 + slope*(t - t2);
    	
    	return surfacePressure;
    }
    
    private double computeRayleighReflectance(double thetas, double thetav, double theta, double pressure) {
    	double rayleighReflectance = 0.0d;
    	
    	final double conv = Math.acos(-1.0d)/180.0;
    	final double xx = 4.0*Math.cos(thetas*conv) * Math.cos(thetav*conv);
    	final double nPressure = pressure/1013.25;
    	final double l1 = 0.0246*0.75*(1.0 + Math.pow(Math.cos(theta*conv), 2.0));
    	
    	rayleighReflectance = nPressure * l1 / xx;
    	
    	return rayleighReflectance;
    }
    
    private double computeRayleighReflectanceCh11(RayleighCorrection rayleighCorrection, double szaDeg, double vzaDeg, double azimDiff, double pressure) {
    	double rayleighReflectance;

    	final double sza = szaDeg * MathUtils.DTOR;
    	final double vza = vzaDeg * MathUtils.DTOR;
    	final double sins = Math.sin(sza);
		final double sinv = Math.sin(vza);
	    final double mus = Math.cos(sza);
	    final double muv = Math.cos(vza);
	    
	    final double azimDiffDeg = azimDiff * MathUtils.RTOD;
	    
	    final double airMass = HelperFunctions.calculateAirMassMusMuv(muv, mus);
	    
	    // rayleigh phase function coefficients, PR in DPM
	    double[] phaseR = new double[3];
	    // rayleigh optical thickness, tauR0 in DPM
	    double tauRayleighCh11 = rayleighCorrection.getAuxdata().tau_R[11];
    	
	    /* Rayleigh phase function Fourier decomposition */
	    rayleighCorrection.phase_rayleigh(mus, muv, sins, sinv, phaseR);
	
	    /* Rayleigh reflectance*/
	    rayleighReflectance = ref_rayleigh_ch11(rayleighCorrection, azimDiffDeg, sza, vza, mus, muv,
	                                    airMass, phaseR, tauRayleighCh11);
	    
    	return rayleighReflectance;
    }
    
    private double ref_rayleigh_ch11(RayleighCorrection rayleighCorrection, double delta_azimuth, double sun_zenith,
			double view_zenith, double mus, double muv, double airMass,
			double[] phaseRayl, double tauRayl) {
    	
    	double rayleighReflectanceCh11;;
    	
		FractIndex tsi = rayleighCorrection.getLh().getRef_rayleigh_i()[0]; /*
												 * interp coordinates for thetas
												 * in LUT scale
												 */
		FractIndex tvi = rayleighCorrection.getLh().getRef_rayleigh_i()[1]; /*
												 * interp coordinates for thetav
												 * in LUT scale
												 */

		double mud = Math.cos(Math.PI/180.0 * delta_azimuth); /*
													 * used for all bands,
													 * compute once
													 */
		double mu2d = 2. * mud * mud - 1.;

		/* angle interpolation coordinates */
		Interp.interpCoord(sun_zenith, rayleighCorrection.getAuxdata().Rayscatt_coeff_s.getTab(2), tsi); /*
																					 * fm
																					 * 15/5/97
																					 */
		Interp
				.interpCoord(view_zenith, rayleighCorrection.getAuxdata().Rayscatt_coeff_s.getTab(3),
						tvi);

		float[][][][] Rayscatt_coeff_s = (float[][][][]) rayleighCorrection.getAuxdata().Rayscatt_coeff_s
				.getJavaArray();
		/*
		 * pre-computation of multiple scatt coefficients, wavelength
		 * independent
		 */
		for (int is = 0; is < RAYSCATT_NUM_SER; is++) {
			/* DPM #2.1.17-4 to 2.1.17-7 */
			for (int ik = 0; ik < RAYSCATT_NUM_ORD; ik++) {
				rayleighCorrection.getLh().getAbcd()[is][ik] = Interp.interpolate(Rayscatt_coeff_s[ik][is],
						rayleighCorrection.getLh().getRef_rayleigh_i());
			}
		}

		double constTerm = (1. - Math.exp(-tauRayl * airMass))
				/ (4. * (mus + muv));
		for (int is = 0; is < RAYSCATT_NUM_SER; is++) {
			/* primary scattering reflectance */
			rayleighCorrection.getLh().getRhoRayl()[is] = phaseRayl[is] * constTerm; /*
														 * DPM #2.1.17-8
														 * CORRECTED
														 */

			/* coefficient for multiple scattering correction */
			double multiScatteringCoeff = 0.;
			for (int ik = RAYSCATT_NUM_ORD - 1; ik >= 0; ik--) {
				multiScatteringCoeff = 0.0246
						* multiScatteringCoeff + rayleighCorrection.getLh().getAbcd()[is][ik]; /*
																	 * DPM
																	 * #2.1.17.9
																	 */
			}

			/* Fourier component of Rayleigh reflectance */
			rayleighCorrection.getLh().getRhoRayl()[is] *= multiScatteringCoeff; /* DPM #2.1.17-10 */
		}

		/* Rayleigh reflectance */
		rayleighReflectanceCh11 = rayleighCorrection.getLh().getRhoRayl()[0] + 2. * mud * rayleighCorrection.getLh().getRhoRayl()[1] + 2.
				* mu2d * rayleighCorrection.getLh().getRhoRayl()[2]; /* DPM #2.1.17-11 */

		return rayleighReflectanceCh11;
	}
    
    /**
     *  This method computes the O2 transmittance for:
     *  (i) The geometrical conditions (SZA and VZA) by interpolation
     *  on the 24 Gaussian angles
     *  (ii) at the central wavelength of the detector
     *  by interpolation on 21 filters
 	 * 
	 * 
     *  This method applies to:
     *  (i) the O2 Rayleigh transmittance
     *  (ii) the O2 aerosol-reflection transmittance
     *  (iii) the O2 aerosol transmittance      
     * 
     * @param to2
     * @param w0
     * @param thetaS
     * @param thetaV
     * @param filterIndex
     * @param isza
     * @param ivza
     * @return
     */
    private double computeO2Transmittance(double centralWvl1, double centralWvl2,
            double[][][] to2, double w0, double thetaS, double thetaV, int filterIndex, int isza, int ivza) {
    	double o2Transmittance = 0.0d;
    	
    	double x0, x1, x2;
    	double s1, s2;
    	double z1, z2;
    	
    	double y1, y2;
    	
    	// first filter
    	
    	x0 = thetaV;
    	x1 = gaussianAngles[ivza];
    	x2 = gaussianAngles[ivza+1];
    	
    	// first SZA
    	y1 = to2[filterIndex][isza][ivza];
    	y2 = to2[filterIndex][isza][ivza+1];
    	z1 = linearInterpol(x0, x1, x2, y1, y2);
    	// second SZA
    	y1 = to2[filterIndex][isza+1][ivza];
    	y2 = to2[filterIndex][isza+1][ivza+1];
    	z2 = linearInterpol(x0, x1, x2, y1, y2);
    	
    	// between the two SZAs
    	x0 = thetaS;
    	x1 = gaussianAngles[isza];
    	x2 = gaussianAngles[isza+1];
    	s1 = linearInterpol(x0, x1, x2, z1, z2);
    	
    	// second filter
    	x0 = thetaV;
    	x1 = gaussianAngles[ivza];
    	x2 = gaussianAngles[ivza+1];
    	
    	// first SZA
    	y1 = to2[filterIndex+1][isza][ivza];
    	y2 = to2[filterIndex+1][isza][ivza+1];
    	z1 = linearInterpol(x0, x1, x2, y1, y2);
    	// second SZA
    	y1 = to2[filterIndex+1][isza+1][ivza];
    	y2 = to2[filterIndex+1][isza+1][ivza+1];
    	z2 = linearInterpol(x0, x1, x2, y1, y2);
    	
    	x0 = thetaS;
    	x1 = gaussianAngles[isza];
    	x2 = gaussianAngles[isza+1];
    	s2 = linearInterpol(x0, x1, x2, z1, z2);
    	
    	o2Transmittance = linearInterpol(w0,
				centralWvl1, centralWvl2, s1, s2);
    	
    	return o2Transmittance;
    }
    
    /**
     * This method provides a simple linear interpolation
     * 
     * @param x
     * @param x1
     * @param x2
     * @param y1
     * @param y2
     * @return double
     */
    private double linearInterpol(double x, double x1, double x2, double y1, double y2) {
    	double z = 0.0;
    	
    	if (x1 == x2) {
    		z = y1;
    	} else {
    		final double slope = (y2-y1)/(x2-x1);
    		z = y1 + slope*(x-x1);
    	}
    	
    	return z;
    }
    
	/**
	 * This method computes the different Lise pressures for a given pixel
	 * 
	 * @param pressureIndex:
	 * 		- 0: press_toa
	 * 		- 1: press_surface
	 * 		- 2: press_bottom_rayleigh
	 * 		- 3: press_bottom_fresnel
	 * @param szaDeg - sun zenith angle (degrees)
	 * @param vzaDeg - view zenith angle (degrees)
	 * @param csza - cosine of sza
	 * @param cvza - cosine of vza
	 * @param ssza - sine of sza
	 * @param svza - sine of vza
	 * @param azimDiff - difference sun-view azimuth
	 * @param rhoToa10 - reflectance band 10
	 * @param rhoToa11 - reflectance band 11
	 * @param rhoToa12 - reflectance band 12
	 * @param w0
	 * @return
	 */
	public double computeLisePressures(RayleighCorrection rayleighCorrection,
			final int pressureIndex,
			final double szaDeg, final double vzaDeg, final double csza, final double cvza, 
			final double ssza, final double svza, final double azimDiff,
			final double rhoToa10, final double rhoToa11, final double rhoToa12, final double w0,
			final float altitude, final float ecmwfPressure, double pressureScaleHeight, final double airMass) {
		
		// Determine nearest filter
		int filterIndex = getNearestFilterIndex(w0);
		
		// Compute the geometric conditions
		final double cosphi = Math.cos(azimDiff);
		
		// scattering angle
		final double theta = MathUtils.RTOD * Math.acos(-csza*cvza - ssza*svza*cosphi);
		// scattering angle for the coupling scattering-Fresnel reflection 
		final double xsi = MathUtils.RTOD * Math.acos(csza*cvza - ssza*svza*cosphi);
		
		final int indsza = (int) Math.round(szaDeg);
		final int indvza = (int) Math.round(vzaDeg);
		final int indtheta = (int) Math.round(theta);
		final int indxsi = (int) Math.round(xsi);
		
		// Determine nearest angle for SZA & VZA
		final int gaussIndexS = getNearestGaussIndex(szaDeg);
		final int gaussIndexV = getNearestGaussIndex(vzaDeg);
		
		// Compute the reference RO_TOA at 761
		final double rhoRef = linearInterpol(761.0d, 753.0d,
				778.0d, rhoToa10, rhoToa12);

		// Ratio of the two bands
		double to2Ratio = rhoToa11/rhoRef;

		// Computation of the apparent pressure P1
		// (an intermediate result - not needed to proceed)
		if (pressureIndex == 0) {
			final double appPressure1 = getPressure(o2FilterWavelengths[filterIndex],
                                                    o2FilterWavelengths[filterIndex+1],
				w0, szaDeg, vzaDeg,
				filterIndex, gaussIndexS, gaussIndexV, to2Ratio);
		
			return appPressure1;
		}

		// get pSurf using 21x6x6 C coefficients:
		if (pressureIndex == 1) {
			final FractIndex[] cIndex = FractIndex.createArray(2);
			Interp.interpCoord(airMass, coeffLUT.getTab(2), cIndex[1]);
	        Interp.interpCoord(rhoToa10, coeffLUT.getTab(1), cIndex[0]);
	        double[][][] cLut = (double[][][]) coeffLUT.getJavaArray();
			double cCoeffResult = Interp.interpolate(cLut[filterIndex], cIndex);
			double eta = rhoToa11/rhoToa10;
			eta *= cCoeffResult;
			
			// Computation of the surface pressure pSurf
			final double pSurf = getPressure(o2FilterWavelengths[filterIndex],
                                             o2FilterWavelengths[filterIndex+1],
					w0, szaDeg, vzaDeg,
					filterIndex, gaussIndexS, gaussIndexV, eta);
		
			return pSurf;
		}
		
		// Compute Rayleigh reflectance at 761
		double ray761 = 0.0d;
		if (rayleighCorrection != null && rayleighCorrection.getAuxdata() != null) {			
			final double press = HelperFunctions.correctEcmwfPressure(
					ecmwfPressure, altitude, pressureScaleHeight); /* DPM #2.6.15.1-3 */
			ray761 = computeRayleighReflectanceCh11(rayleighCorrection,
					szaDeg, vzaDeg, azimDiff, press);
		} else {
			ray761 = computeRayleighReflectance(
				szaDeg, vzaDeg, theta,
				standardSeaSurfacePressure);
		}

		// Compute the Rayleigh O2 transmittance
		final double trO2 = computeO2Transmittance(o2FilterWavelengths[filterIndex],
                                                   o2FilterWavelengths[filterIndex+1], to2Ray,
				w0, szaDeg, vzaDeg,
				filterIndex, gaussIndexS, gaussIndexV);

		// // Rayleigh correction on the O2 transmittance
		final double to2RCorrected = (rhoToa11 - ray761 * trO2)
				/ (rhoRef - ray761);

		// Determination of the aerosol apparent pressure
		// after Rayleigh correction
		// (an intermediate result - not needed to proceed)
		if (pressureIndex == 2) {
			final double appPressure2 = getPressure(o2FilterWavelengths[filterIndex],
                                                    o2FilterWavelengths[filterIndex+1],
					w0, szaDeg, vzaDeg,
					filterIndex, gaussIndexS, gaussIndexV,
					to2RCorrected);
		
			return appPressure2;
		}

		// Determination of the aerosol pressure after surface
		// correction:

		// Compute the aerosol O2 transmittance
		final double trAerosol = computeO2Transmittance(o2FilterWavelengths[filterIndex],
                                                   o2FilterWavelengths[filterIndex+1],
				to2AtmAerosol, w0,
				szaDeg, vzaDeg, filterIndex, gaussIndexS,
				gaussIndexV);
		
		// Compute the aerosol fresnel O2 transmittance for direct to diffuse
		final double trFresnel1 = computeO2Transmittance(o2FilterWavelengths[filterIndex],
                                                   o2FilterWavelengths[filterIndex+1],
				to2Fresnel, w0,
				szaDeg, vzaDeg, filterIndex, gaussIndexS,
				gaussIndexV);
		
		// Compute the aerosol fresnel O2 transmittance for diffuse to direct
		final double trFresnel2 = computeO2Transmittance(o2FilterWavelengths[filterIndex],
                                                   o2FilterWavelengths[filterIndex+1],
				to2Fresnel, w0,
//				vzaDeg, szaDeg, filterIndex, gaussIndexS,     // LB, 02.10.09 
				szaDeg, vzaDeg, filterIndex, gaussIndexS,
				gaussIndexV);
		
		// Compute the APF ratio between forward and backward scattering
		final double pfb = apfJunge[indxsi]/apfJunge[indtheta];
		
		// Compute the contribution of the aerosol-Fresnel 
		// This contribution is an output for further flag
		final double caf = 1.0 + pfb*(fresnelCoefficients[indsza] + fresnelCoefficients[indvza]);
		
		// Correction of the O2 transmittance by the coupling
		//   aerosol-Fresnel
		final double xx = (trAerosol + pfb*
							(trFresnel2*fresnelCoefficients[indvza] + trFresnel1*fresnelCoefficients[indsza]))/caf;
		
		final double to2Rf = to2RCorrected * trAerosol / xx;
		
		final double appPressure3 = getPressure(o2FilterWavelengths[filterIndex],
                                                o2FilterWavelengths[filterIndex+1],
				w0, szaDeg, vzaDeg,
				filterIndex, gaussIndexS, gaussIndexV,
				to2Rf);
		return appPressure3;
	}

	
	

	private void computeP1(RayleighCorrection rayleighCorrection, Band band, Tile targetTile,
			ProgressMonitor pm, int pressureResultIndex,
			Rectangle rectangle, Tile detector, Tile sza,
			Tile vza, Tile saa, Tile vaa, Tile altitudeTile,
			Tile ecmwfPressureTile, Tile[] rhoToa, Tile isInvalid,
			Tile isInvalidOcean, Tile isInvalidLand) {
		for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
			for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
				if (pm.isCanceled()) {
					break;
				}
				final int detectorIndex = detector.getSampleInt(x, y);
				
				isInvalidPixel(band, isInvalid, isInvalidOcean,
						isInvalidLand, y, x);
				
				if (isInvalidPixel(band, isInvalid, isInvalidOcean, isInvalidLand, y, x)) {
					targetTile.setSample(x, y, 0);
				} else {
					// Compute the geometric conditions
					final double pressureResult = getPressureResult(rayleighCorrection,
							pressureResultIndex, sza, vza, saa, vaa,
							altitudeTile, ecmwfPressureTile, rhoToa, y, x,
							detectorIndex);
					
					targetTile.setSample(x, y, pressureResult);
				}
			}
			pm.worked(1);
		}
	}

	private double getPressureResult(RayleighCorrection rayleighCorrection, int pressureResultIndex, Tile sza,
			Tile vza, Tile saa, Tile vaa, Tile altitudeTile,
			Tile ecmwfPressureTile, Tile[] rhoToa, int y, int x,
			final int detectorIndex) {
		final float szaDeg = sza.getSampleFloat(x, y);
		final float vzaDeg = vza.getSampleFloat(x, y);
		
		final double csza = Math.cos(MathUtils.DTOR *szaDeg);
		final double cvza = Math.cos(MathUtils.DTOR *vzaDeg);
		final double ssza = Math.sin(MathUtils.DTOR *szaDeg);
		final double svza = Math.sin(MathUtils.DTOR *vzaDeg);
		final double azimDiff = MathUtils.DTOR * (vaa.getSampleFloat(x, y) - saa.getSampleFloat(x, y));
		
		final double rhoToa10 = rhoToa[9].getSampleDouble(x, y);
		double rhoToa11 = rhoToa[10].getSampleDouble(x, y);
		final double rhoToa12 = rhoToa[11].getSampleDouble(x, y);
		
		final float altitude = altitudeTile.getSampleFloat(x, y);
		final float ecmwfPressure = ecmwfPressureTile.getSampleFloat(x, y);
		final double airMass = HelperFunctions.calculateAirMass(vzaDeg, szaDeg);
		
		double centralWvl760 = auxData.central_wavelength[BB760][detectorIndex];
		
		rhoToa11 = applyStraylightCorr(detectorIndex, rhoToa10, rhoToa11);
		
		final double pressureResult = computeLisePressures(rayleighCorrection, pressureResultIndex,
				szaDeg, vzaDeg, csza, cvza, ssza, svza, azimDiff, rhoToa10, rhoToa11, rhoToa12, 
				centralWvl760, altitude, ecmwfPressure, auxData.press_scale_height, airMass);
		return pressureResult;
	}
	
	private void computePSurf(RayleighCorrection rayleighCorrection, Band band, Tile targetTile,
			ProgressMonitor pm, int pressureResultIndex, 
			Rectangle rectangle, Tile detector, Tile sza,
			Tile vza, Tile saa, Tile vaa, Tile altitudeTile,
			Tile ecmwfPressureTile, Tile[] rhoToa, Tile isInvalid,
			Tile isInvalidOcean, Tile isInvalidLand) {
		for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
			for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
				if (pm.isCanceled()) {
					break;
				}
				final int detectorIndex = detector.getSampleInt(x, y);
				
				isInvalidPixel(band, isInvalid, isInvalidOcean,
						isInvalidLand, y, x);
				
				if (isInvalidPixel(band, isInvalid, isInvalidOcean, isInvalidLand, y, x)) {
					targetTile.setSample(x, y, 0);
				} else {
					// Compute the geometric conditions
					final double pressureResult = getPressureResult(rayleighCorrection,
							pressureResultIndex, sza, vza, saa, vaa,
							altitudeTile, ecmwfPressureTile, rhoToa, y, x,
							detectorIndex);
					
					targetTile.setSample(x, y, pressureResult);
				}
			}
			pm.worked(2);
		}
	}

	private void computeP2(RayleighCorrection rayleighCorrection, Band band, Tile targetTile,
			ProgressMonitor pm, int pressureResultIndex, 
			Rectangle rectangle, Tile detector, Tile sza,
			Tile vza, Tile saa, Tile vaa, Tile altitudeTile,
			Tile ecmwfPressureTile, Tile[] rhoToa, Tile isInvalid,
			Tile isInvalidOcean, Tile isInvalidLand) {
		for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
			for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
				if (pm.isCanceled()) {
					break;
				}
				final int detectorIndex = detector.getSampleInt(x, y);
				
				isInvalidPixel(band, isInvalid, isInvalidOcean,
						isInvalidLand, y, x);
				
				if (isInvalidPixel(band, isInvalid, isInvalidOcean, isInvalidLand, y, x)) {
					targetTile.setSample(x, y, 0);
				} else {
					final double pressureResult = getPressureResult(rayleighCorrection,
							pressureResultIndex, sza, vza, saa, vaa,
							altitudeTile, ecmwfPressureTile, rhoToa, y, x,
							detectorIndex);
					
					targetTile.setSample(x, y, pressureResult);
				}
			}
			pm.worked(3);
		}
	}
	
	private void computePScatt(RayleighCorrection rayleighCorrection, Band band, Tile targetTile,
			ProgressMonitor pm, int pressureResultIndex, 
			Rectangle rectangle, Tile detector, Tile sza,
			Tile vza, Tile saa, Tile vaa, Tile altitudeTile,
			Tile ecmwfPressureTile, Tile[] rhoToa, Tile isInvalid,
			Tile isInvalidOcean, Tile isInvalidLand) {
		for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
			for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
				if (pm.isCanceled()) {
					break;
				}
				final int detectorIndex = detector.getSampleInt(x, y);
				
				isInvalidPixel(band, isInvalid, isInvalidOcean,
						isInvalidLand, y, x);
				
				if (isInvalidPixel(band, isInvalid, isInvalidOcean, isInvalidLand, y, x)) {
					targetTile.setSample(x, y, 0);
				} else {
					final double pressureResult = getPressureResult(rayleighCorrection,
							pressureResultIndex, sza, vza, saa, vaa,
							altitudeTile, ecmwfPressureTile, rhoToa, y, x,
							detectorIndex);
					
					targetTile.setSample(x, y, pressureResult);
				}
			}
			pm.worked(4);
		}
	}
	
	private void createTargetProduct() throws OperatorException {
        targetProduct = createCompatibleProduct(sourceProduct, "MER_CTP", "MER_L2");
        // Surface pressure, land:
        if (outputPressureSurface)
        	targetProduct.addBand("surface_press_lise", ProductData.TYPE_FLOAT32); 
        // TOA pressure, land+ocean:
        if (outputP1)
        	targetProduct.addBand("p1_lise", ProductData.TYPE_FLOAT32);  
        // Bottom pressure, ocean, Rayleigh multiple scattering at 761nm considered:
        if (outputP2)
        	targetProduct.addBand("p2_lise", ProductData.TYPE_FLOAT32); 
        // Bottom pressure, ocean, Rayleigh multiple scattering at 761nm and Fresnel transmittance considered:
        if (outputPScatt || l2CloudDetection)
        	targetProduct.addBand("pscatt_lise", ProductData.TYPE_FLOAT32);

        BandMathsOp bandArithmeticOpInvalid =
            BandMathsOp.createBooleanExpressionBand(INVALID_EXPRESSION, sourceProduct);
        invalidBand = bandArithmeticOpInvalid.getTargetProduct().getBandAt(0);
        
        BandMathsOp bandArithmeticOpInvalidOcean =
            BandMathsOp.createBooleanExpressionBand(INVALID_EXPRESSION_OCEAN, sourceProduct);
        invalidBandOcean = bandArithmeticOpInvalidOcean.getTargetProduct().getBandAt(0);
        
        BandMathsOp bandArithmeticOpInvalidLand =
            BandMathsOp.createBooleanExpressionBand(INVALID_EXPRESSION_LAND, sourceProduct);
        invalidBandLand = bandArithmeticOpInvalidLand.getTargetProduct().getBandAt(0);
    }
	
	private double applyStraylightCorr(final int detectorIndex,
			final double rhoToa10, double rhoToa11) {
		double stray = 0.0;
		if (straylightCorr) {
			// apply FUB straylight correction...
			stray = straylightCoefficients[detectorIndex] * rhoToa10;
			rhoToa11 += stray;
		}
		return rhoToa11;
	}

	private boolean isInvalidPixel(Band band, Tile isInvalid, Tile isInvalidOcean,
			Tile isInvalidLand, int y, int x) {
		return ((band.getName().equals("p1_lise") && isInvalid.getSampleBoolean(x, y)) ||
						(band.getName().equals("surface_press_lise") && isInvalidLand.getSampleBoolean(x, y)) ||
						(((band.getName().equals("p2_lise")) || 
//								(band.getName().equals("pscatt_lise"))) && isInvalidOcean.getSampleBoolean(x, y)));
		(band.getName().equals("pscatt_lise"))) && isInvalid.getSampleBoolean(x, y)));
	}
    
	@Override
    public void computeTile(Band band, Tile targetTile, ProgressMonitor pm) throws OperatorException {
    	
    	Rectangle rectangle = targetTile.getRectangle();
        pm.beginTask("Processing frame...", rectangle.height);

        try {
            RayleighCorrection rayleighCorrection = new RayleighCorrection(auxData);

        	Tile detector = getSourceTile(sourceProduct.getBand(EnvisatConstants.MERIS_DETECTOR_INDEX_DS_NAME), rectangle, pm);
        	Tile sza = getSourceTile(sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME), rectangle, pm);
			Tile vza = getSourceTile(sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_ZENITH_DS_NAME), rectangle, pm);
			Tile saa = getSourceTile(sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_AZIMUTH_DS_NAME), rectangle, pm);
			Tile vaa = getSourceTile(sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_AZIMUTH_DS_NAME), rectangle, pm);
			Tile altitudeTile = getSourceTile(sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_DEM_ALTITUDE_DS_NAME), rectangle, pm);
            Tile ecmwfPressureTile = getSourceTile(sourceProduct.getTiePointGrid("atm_press"), rectangle, pm);
			
			Tile[] rhoToa = new Tile[EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS];
			for (int i1 = 0; i1 < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i1++) {
			    rhoToa[i1] = getSourceTile(rhoToaProduct.getBand(Rad2ReflOp.RHO_TOA_BAND_PREFIX + "_" + (i1 + 1)), rectangle, pm);
			}
			
			Tile isInvalid = getSourceTile(invalidBand, rectangle, pm);
			Tile isInvalidOcean = getSourceTile(invalidBandOcean, rectangle, pm);
			Tile isInvalidLand = getSourceTile(invalidBandLand, rectangle, pm);
			
			// TODO: set band names as constants
			if (band.getName().equals("p1_lise") && outputP1 ) {
				computeP1(rayleighCorrection, band, targetTile, pm, 0, rectangle, detector, sza,
						vza, saa, vaa, altitudeTile, ecmwfPressureTile, rhoToa,
						isInvalid, isInvalidOcean, isInvalidLand);
			}
			if (band.getName().equals("surface_press_lise") && outputPressureSurface ) {
				computePSurf(rayleighCorrection, band, targetTile, pm, 1, rectangle, detector, sza,
						vza, saa, vaa, altitudeTile, ecmwfPressureTile, rhoToa,
						isInvalid, isInvalidOcean, isInvalidLand);
			}
			if (band.getName().equals("p2_lise") && outputP2 ) {
				computeP2(rayleighCorrection, band, targetTile, pm, 2, rectangle, detector, sza,
						vza, saa, vaa, altitudeTile, ecmwfPressureTile, rhoToa,
						isInvalid, isInvalidOcean, isInvalidLand);
			}
			if (band.getName().equals("pscatt_lise") && (outputPScatt || l2CloudDetection)) {
				computePScatt(rayleighCorrection, band, targetTile, pm, 3, rectangle, detector, sza,
						vza, saa, vaa, altitudeTile, ecmwfPressureTile, rhoToa,
						isInvalid, isInvalidOcean, isInvalidLand);
			}
        } catch (RuntimeException e) {
        	if ((straylightCorr) && (!sourceProduct.getProductType().equals(EnvisatConstants.MERIS_RR_L1B_PRODUCT_TYPE_NAME))) {
        		throw new OperatorException
        		("Straylight correction not possible for full resolution products.");
        	} else {
        		throw new OperatorException("Failed to process Surface Pressure LISE:\n" + e.getMessage(), e);
        	}
		}finally {
            pm.done();
        }
    }
	
    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {
        public Spi() {
            super(LisePressureOp.class, "Meris.LisePressure");
        }
    }
}