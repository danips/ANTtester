package com.quantrity.anttester;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BuildConfigTest {
    @Test
    public void releaseVersionIsDefined() {
        assertEquals("2.01", BuildConfig.VERSION_NAME);
        assertTrue(BuildConfig.VERSION_CODE > 0);
    }
}
