package org.iodine.phone;

import org.junit.Assert;
import org.junit.Test;

public class TestBuilder {

  @Test
  public void testBuild() {
    MSISDNFactory.addScheme(MSISDNScheme.create("3,2,7;CC=353;NDC=82,83,85,86,87,88,89", "IE"));

    MSISDN number = MSISDN.Builder().cc(353).ndc(87).subscriber(3538080).build();
    Assert.assertEquals(353873538080L, number.longValue());
    Assert.assertEquals(353, number.getCC());
    Assert.assertEquals(87, number.getNDC());
    Assert.assertEquals(3538080, number.getSN());
  }

  @Test
  public void testSchemeBuilder() {
   // MSISDNScheme.clearSchemes();
    MSISDNScheme newScheme = MSISDNScheme.create("2,2,6;CC=99;NDC=22;SN=111111", "XX");
    MSISDNFactory.addScheme(newScheme);
    MSISDN number = MSISDN.valueOf(9922111111L);
    Assert.assertTrue ( newScheme.isValid(number));
    Assert.assertEquals(99, number.getCC());
    Assert.assertEquals(22, number.getNDC());
    Assert.assertEquals(111111, number.getSN());
  }

  @Test(expected = IllegalArgumentException.class)
  public void failsWithBadRange() {
    MSISDNScheme.create("2,2,6;CC=99;NDC=19-11;SN=111111", "XX");
  }

  @Test(expected = IllegalArgumentException.class)
  public void failsWithEmptyString() {
    MSISDNFactory.createMSISDN("");
  }
}
