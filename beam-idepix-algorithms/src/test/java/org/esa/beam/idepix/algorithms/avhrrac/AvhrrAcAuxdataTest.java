package org.esa.beam.idepix.algorithms.avhrrac;

import org.junit.Test;

import java.io.IOException;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertNotNull;

public class AvhrrAcAuxdataTest {

    @Test
    public void testReadVzaFromFile() {

        try {
            AvhrrAcAuxdata.Line2ViewZenithTable vzaTable = AvhrrAcAuxdata.getInstance().createLine2ViewZenithTable();

            assertNotNull(vzaTable);
            assertEquals(2048, vzaTable.getVza().length);
            assertEquals(-67.03, vzaTable.getVza()[20]);
            assertEquals(-42.7, vzaTable.getVza()[345]);
            assertEquals(-7.88, vzaTable.getVza()[896]);
            assertEquals(47.17, vzaTable.getVza()[1768]);
            assertEquals(68.91, vzaTable.getVza()[2047]);
        } catch (IOException e) {
            // todo
            fail();
        }
    }
}
