package org.esa.beam.mepix.operators;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.dataop.dem.ElevationModel;
import org.esa.beam.framework.dataop.dem.ElevationModelDescriptor;
import org.esa.beam.framework.dataop.dem.ElevationModelRegistry;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.gpf.operators.meris.MerisBasisOp;
import org.esa.beam.gpf.operators.standard.BandMathsOp;

import java.awt.Rectangle;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.StringTokenizer;

/**
 * Operator for computing barometric pressure assuming US standard atmosphere.
 *
 * @author Olaf Danne
 * @version $Revision: 6824 $ $Date: 2009-11-03 16:02:02 +0100 (Di, 03 Nov 2009) $
 */
@OperatorMetadata(alias = "mepix.BarometricPressure",
        version = "1.0",
        authors = "Olaf Danne",
        copyright = "(c) 2008 by Brockmann Consult",
        description = "This operator computes barometric pressure assuming US standard atmosphere.")
public class BarometricPressureOp extends MerisBasisOp {
    @SourceProduct(alias="l1b", description = "The source product.")
    Product sourceProduct;
    @TargetProduct(description = "The target product.")
    Product targetProduct;
    @Parameter(description="If 'true' the algorithm will use altitudes from GETASSE30 DEM.", defaultValue="false")
    public boolean useGetasseDem = false;

    public static final String PRESSURE_BAROMETRIC = "barometric_press";

    private static final String INVALID_EXPRESSION = "l1_flags.INVALID";
    private Band invalidBand;

    private static final String WATER_VAPOUR_PRESSURE_TABLE_FILE_NAME = "water_vapour_pressure.d";
    private static final int WATER_VAPOUR_PRESSURE_TABLE_LENGTH = 22;
    private static final int WATER_VAPOUR_PRESSURE_TABLE_HEADER_LINES = 3;

    WaterVapourPressureTable waterVapourPressureTable;
    private ElevationModel getasseElevationModel;

    @Override
    public void initialize() throws OperatorException {

        if (sourceProduct != null) {
            createTargetProduct();
        }

        try {
            readWaterVapourPressureTable();
        } catch (Exception e) {
            throw new OperatorException("Failed to load water vapour pressure table:\n" + e.getMessage());
        }

        if (useGetasseDem) {
            final String demName = "GETASSE30";
            final ElevationModelDescriptor demDescriptor = ElevationModelRegistry.getInstance().getDescriptor(
                    demName);
            if (demDescriptor == null || !demDescriptor.isDemInstalled()) {
                throw new OperatorException("DEM not installed: " + demName + ". Please install with Module Manager.");
            }
            getasseElevationModel = demDescriptor.createDem();
        }
    }

    private void createTargetProduct() throws OperatorException {
        targetProduct = createCompatibleProduct(sourceProduct, "MER_PBARO", "MER_L2");
        targetProduct.addBand(PRESSURE_BAROMETRIC, ProductData.TYPE_FLOAT32);
        if (useGetasseDem)
            targetProduct.addBand("getasse_alt", ProductData.TYPE_FLOAT32);

        BandMathsOp bandArithmeticOp =
            BandMathsOp.createBooleanExpressionBand(INVALID_EXPRESSION, sourceProduct);
        invalidBand = bandArithmeticOp.getTargetProduct().getBandAt(0);
    }

    private void readWaterVapourPressureTable() throws IOException {
        final InputStream inputStream = BarometricPressureOp.class.getResourceAsStream(WATER_VAPOUR_PRESSURE_TABLE_FILE_NAME);

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        StringTokenizer st;
        try {
            waterVapourPressureTable = new WaterVapourPressureTable();

            // skip header lines
            for (int i = 0; i < WATER_VAPOUR_PRESSURE_TABLE_HEADER_LINES; i++) {
                bufferedReader.readLine();
            }

            for (int i = 0; i < WATER_VAPOUR_PRESSURE_TABLE_LENGTH; i++) {
                String line = bufferedReader.readLine();
                line = line.trim();
                st = new StringTokenizer(line, " ", false);

                if (st.hasMoreTokens()) {
                    // temperature
                    waterVapourPressureTable.setTemperature(i, Double.parseDouble(st.nextToken()));
                }
                if (st.hasMoreTokens()) {
                    // pressure
                    waterVapourPressureTable.setPressure(i, Double.parseDouble(st.nextToken()));
                }
            }
        } catch (IOException e) {
            throw new OperatorException("Failed to load Water Vapour Pressure Table: \n" + e.getMessage(), e);
        } catch (NumberFormatException e) {
            throw new OperatorException("Failed to load Water Vapour Pressure Table: \n" + e.getMessage(), e);
        } finally {
            inputStream.close();
        }
    }


    private int getSurfaceTemperatureIndex(double temperature) {
        int surfaceTemperatureIndex = WATER_VAPOUR_PRESSURE_TABLE_LENGTH-2;

        for (int i = 0; i < WATER_VAPOUR_PRESSURE_TABLE_LENGTH-1; i++) {
            if (temperature < waterVapourPressureTable.getTemperature()[i]) {
                surfaceTemperatureIndex = i;
                break;
            }
        }

        return surfaceTemperatureIndex;
    }


    /**
     * This method provides a simple linear interpolation
     *
     * @param x  , position in [x1,x2] to interpolate at
     * @param x1 , left neighbour of x
     * @param x2 , right neighbour of x
     * @param y1 , y(x1)
     * @param y2 , y(x2)
     *
     * @return double z = y(x), the interpolated value
     */
    private double linearInterpol(double x, double x1, double x2, double y1, double y2) {
        double z;

        if (x1 == x2) {
            z = y1;
        } else {
            final double slope = (y2 - y1) / (x2 - x1);
            z = y1 + slope * (x - x1);
        }

        return z;
    }

    @Override
    public void computeTile(Band band, Tile targetTile, ProgressMonitor pm) throws OperatorException {

    	Rectangle rectangle = targetTile.getRectangle();
        pm.beginTask("Processing frame...", rectangle.height);

        try {
        	Tile detector = getSourceTile(sourceProduct.getBand(EnvisatConstants.MERIS_DETECTOR_INDEX_DS_NAME), rectangle, pm);
        	Tile slpTile = getSourceTile(sourceProduct.getTiePointGrid("atm_press"), rectangle, pm);    // MSLP
            Tile longitudeTile = getSourceTile(sourceProduct.getTiePointGrid("longitude"), rectangle, pm);
            Tile latitudeTile = getSourceTile(sourceProduct.getTiePointGrid("latitude"), rectangle, pm);
            Tile altitudeTile = getSourceTile(sourceProduct.getTiePointGrid("dem_alt"), rectangle, pm);

            Tile isInvalid = getSourceTile(invalidBand, rectangle, pm);

            // implement computation as follows:
            //                p_surf = p_sea / exp(gn*h/(R*(t+C*e+gam*h/2)))
            //                    with
            //                gn = 9.80665 m/s2 (acceleration of gravity)
            //                gam = 0.0065 K/gpm (temperature lapse rate)
            //                R = 287.05 J/kgK (gas constant)
            //                C = 0.11 K/hPa (coefficient accounting for humidity, assumed to be constant)
            //                t = temperature at surface (not at sea level) (take US standard: T = 288 - 6.5h/1km)
            //                e = partial pressure of water vapour at surface (e = e(T), interpolate from LUT)
            //                h = surface elevation
            //
            // as used by German Weather Service (Rasmus Lindstrot, FU Berlin, PN 2009/02/20)

            final float g =  9.80665f; // 9.80665 m/s2 (acceleration of gravity)
            final float gamma = 0.0065f; // gamma = 0.0065 K/gpm (temperature lapse rate in U.S. standard)
            final float R = 287.05f;     // gas constant
            final float C = 0.11f;       // 0.11 K/hPa (coefficient accounting for humidity, assumed to be constant)
            final float seaLevelTemp = 288.15f; // mean sea level temperature in U.S. standard, in deg. centigrade!

            int i = 0;
			for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
				for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
					if (pm.isCanceled()) {
						break;
					}
					if (isInvalid.getSampleBoolean(x, y)) {
						targetTile.setSample(x, y, 0);
					} else {
						final float slp = slpTile.getSampleFloat(x, y);
                        float alt;
                        if (useGetasseDem) {
                            // get altitude from GETASSE DEM
                            final float lat = latitudeTile.getSampleFloat(x, y);
                            final float lon = longitudeTile.getSampleFloat(x, y);
                            GeoPos geoPos = new GeoPos(lat, lon);
                            alt = getasseElevationModel.getElevation(geoPos);
                        } else {
                            // get altitude from tie point DEM
                            alt = altitudeTile.getSampleFloat(x, y);
                        }

                        double surfaceTemp = seaLevelTemp - gamma*alt;
                        int surfaceTempIndex = getSurfaceTemperatureIndex(surfaceTemp-273.15);
                        double t1 = waterVapourPressureTable.getTemperature()[surfaceTempIndex];
                        double t2 = waterVapourPressureTable.getTemperature()[surfaceTempIndex+1];
                        double p1 = waterVapourPressureTable.getPressure()[surfaceTempIndex];
                        double p2 = waterVapourPressureTable.getPressure()[surfaceTempIndex+1];
                        final double e = linearInterpol(surfaceTemp, t1, t2, p1, p2);

                        final double pbaro = slp / Math.exp(g*alt/(R*(surfaceTemp + C*e + gamma*alt/2.0)));

                        // if GETASSE DEM is used, write altitude in a separate band
                        if (band.getName().equals("getasse_alt")) {
                             targetTile.setSample(x, y, alt);
                        } else {
						    targetTile.setSample(x, y, pbaro);
                        }
					}
					i++;
				}
				pm.worked(1);
			}
        } catch (Exception e) {
        	throw new OperatorException("Failed to process Barometric Pressure:\n" + e.getMessage(), e);
		} finally {
            pm.done();
        }
    }

    private class WaterVapourPressureTable {
        private double[] temperature = new double[WATER_VAPOUR_PRESSURE_TABLE_LENGTH];
        private double[] pressure = new double[WATER_VAPOUR_PRESSURE_TABLE_LENGTH];

        public double[] getTemperature() {
            return temperature;
        }

        public void setTemperature(int index, double temperature) {
            this.temperature[index] = temperature;
        }

        public double[] getPressure() {
            return pressure;
        }

        public void setPressure(int index, double pressure) {
            this.pressure[index] = pressure;
        }
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {
        public Spi() {
            super(BarometricPressureOp.class, "Meris.BarometricPressure");
        }
    }
}
