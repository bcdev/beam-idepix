/*
 * Copyright (C) 2010 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package org.esa.beam.idepix.algorithms.avhrrac;

import org.esa.beam.idepix.util.SunAngles;
import org.esa.beam.idepix.util.SunAnglesCalculator;
import org.esa.beam.idepix.util.SunPosition;
import org.esa.beam.util.math.MathUtils;
import org.junit.*;

import java.util.Calendar;

import static junit.framework.TestCase.assertEquals;

public class AvhrrAcClassificationOpTest {


    private double latPoint;
    private double lonPoint;
    private double sza;
    private String ddmmyy;
    private double latSat;
    private double lonSat;
    private double relAziExpected;

    @BeforeClass
    public static void beforeClass() throws Exception {
    }

    @Before
    public void setUp() throws Exception {
        latPoint = 41.981514;   // product 9507011153_pr, pixel 1520/3600
        lonPoint = 23.374292;
        latSat = 41.278893;     // product 9507011153_pr, pixel 1024/3600
        lonSat = 18.130579;
        sza = 26.382229;
        relAziExpected = 28.90293;
        ddmmyy = "010795";
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
//    @Ignore
    public void testSaaFromSunAnglesCalculation() {
        final Calendar dateAsCalendar = AvhrrAcClassification2Op.getDateAsCalendar(ddmmyy);
        final SunAngles sunAngles = SunAnglesCalculator.calculate(dateAsCalendar, latPoint, lonPoint);
        assertEquals(sza, sunAngles.getZenithAngle(), 2.E-1);
    }

    @Test
//    @Ignore
    public void testSunPositionCalculation() {
        final SunPosition sunPosition = AvhrrAcClassification2Op.computeSunPosition("210697");
        assertEquals(23.5, sunPosition.getLat(), 1.E-1);
        assertEquals(0.5, sunPosition.getLon(), 1.E-1);
    }


    @Test
//    @Ignore
    public void testSunAzimuthAngleCalculation() {
        final Calendar dateAsCalendar = AvhrrAcClassification2Op.getDateAsCalendar(ddmmyy);
        final SunAngles sunAngles = SunAnglesCalculator.calculate(dateAsCalendar, latPoint, lonPoint);

        final double latPointRad = latPoint * MathUtils.DTOR;
        final double lonPointRad = lonPoint * MathUtils.DTOR;
        final SunPosition sunPosition = AvhrrAcClassification2Op.computeSunPosition(ddmmyy);
        final double latSunRad = sunPosition.getLat() * MathUtils.DTOR;
        final double lonSunRad = sunPosition.getLon() * MathUtils.DTOR;
        final double saaRad = AvhrrAcClassification2Op.computeSaa(sza, latPointRad, lonPointRad, latSunRad, lonSunRad);

        assertEquals(saaRad*MathUtils.RTOD, sunAngles.getAzimuthAngle(), 1.0);
    }

    @Test
//    @Ignore
    public void testRelativeAzimuthAngleCalculation() {
        // todo: further investigate
        final double latPointRad = latPoint * MathUtils.DTOR;
        final double lonPointRad = lonPoint * MathUtils.DTOR;
        final SunPosition sunPosition = AvhrrAcClassification2Op.computeSunPosition(ddmmyy);  // this is the unknown we have to fix!!!
        final double latSunRad = sunPosition.getLat() * MathUtils.DTOR;
        final double lonSunRad = sunPosition.getLon() * MathUtils.DTOR;
        final double saaRad = AvhrrAcClassification2Op.computeSaa(sza, latPointRad, lonPointRad, latSunRad, lonSunRad);

        final double latSatRad = latSat * MathUtils.DTOR;
        final double lonSatRad = lonSat * MathUtils.DTOR;
        final double greatCirclePointToSatRad =
                AvhrrAcClassification2Op.computeGreatCircleFromPointToSat(latPointRad, lonPointRad, latSatRad, lonSatRad);
        final double vaaRad = AvhrrAcClassification2Op.computeVaa(latPointRad, lonPointRad, latSatRad, lonSatRad,
                greatCirclePointToSatRad);

        final double relAziRad = AvhrrAcClassification2Op.correctRelAzimuthRange(vaaRad, saaRad);
        final double relAziDeg = relAziRad * MathUtils.RTOD;
        assertEquals(relAziExpected, relAziDeg, 1.E-1);
    }
}
