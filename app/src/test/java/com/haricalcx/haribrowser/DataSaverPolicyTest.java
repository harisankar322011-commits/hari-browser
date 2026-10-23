package com.haricalcx.haribrowser;

import org.junit.Test;

import static org.junit.Assert.*;

public class DataSaverPolicyTest {
    @Test
    public void homeDashboardKeepsItsLocalImagesAvailable() {
        assertFalse(DataSaverPolicy.shouldBlockNetworkImages(true, true, true));
        assertFalse(DataSaverPolicy.shouldBlockNetworkImages(true, false, true));
    }

    @Test
    public void dataSaverBlocksRemoteImagesEvenWhenImageSettingIsEnabled() {
        assertTrue(DataSaverPolicy.shouldBlockNetworkImages(false, true, true));
    }

    @Test
    public void userImagePreferenceStillWorksWhenDataSaverIsOff() {
        assertFalse(DataSaverPolicy.shouldBlockNetworkImages(false, true, false));
        assertTrue(DataSaverPolicy.shouldBlockNetworkImages(false, false, false));
    }

    @Test
    public void statusExplainsTheScopeWithoutClaimingMeasuredSavings() {
        String on = DataSaverPolicy.description(true);
        assertTrue(on.contains("webpage images are blocked"));
        assertTrue(on.contains("may still use data"));
        assertFalse(on.toLowerCase().contains("saved "));
        assertTrue(DataSaverPolicy.description(false).contains("separate image setting"));
    }
}
