package org.iodine.phone;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import static org.iodine.phone.MSISDNScheme.PartCode;

/**
 * Creates MSISDN numbers based on schemes, loaded from a resource named <code>MSISDNScheme.properties</code>
 * <p/>
 * The file format consists of a unique key (usually the ISO country code, and optionally, MNO name and/or a
 * discriminator), followed by a specification of the the number scheme, for example:
 * <pre>
 *   EG.9=2,2,7;CC=20;NDC=10,11,12,14,16,17,18,19
 * </pre>
 * Describes a MSISDN scheme where the country code part is "20", the NDC part is two digits long and in the
 * specified set, and the subscriber number is seven digits long
 * <p/>
 * This is the 'companion class' (public) that Josh Bloch recommends when working with immutable value objects,
 * to perform factory type operations, such as value construction
 *
 * @author roy.phiilips
 */
public class MSISDNFactory {

  // ... loads MSISDN specifications from file named by this property
  private static final String MSISDN_PROPERTIES_DEFAULT = "/MSISDNScheme.properties";
  // get known MSISDN schemes from property file
  private static final Map<Integer, MSISDNScheme> schemes = loadDefaultScheme();
  // exception message prefixes (public for testability)
  public static final String UNRECOGNIZED_SCHEME = "MSISDN Unrecognized scheme";


  /**
   * MSISDN factory method to match the input string against the known
   * set of MSISDN schemes and instantiate an MSISDN object
   *
   * @param msisdnString from which to initialize the result
   * @return MSISDN create from the input string
   * @throws IllegalArgumentException if the parameter is not a valid and known MSISDN
   */
  static MSISDN createMSISDN(String msisdnString) {
    if (msisdnString == null || msisdnString.trim().length() == 0) {
      throw new IllegalArgumentException(
          "MSISDN candidate string must be non-null and non-empty: " + msisdnString);
    }
    if ( schemes.size() == 0) {
      throw new IllegalStateException ( "No MSISDN schemes registered");
    }
    String candidate = normalize(msisdnString);
    MSISDN result = null;
    for (int ccSize = 3; ccSize > 0 && result == null; ccSize--) {
      result = lookupByCC(ccSize, candidate);
    }
    if (result == null) {
      throw new IllegalArgumentException(
          UNRECOGNIZED_SCHEME + " for: " + msisdnString
              + " (known schemes=" + schemes.values() + ")");
    }
    return result;
  }

  public static MSISDNScheme getSchemeForCC(int cc, int length) {
    return schemes.get(createKey(cc, length));
  }

  /**
   * Look-up the scheme for the country code represented by the first <code>ccSize</code>
   * digits of the supplied string, continuing to match the NDC and SN if found, and
   * creating the appropriate MSISDN
   *
   * @param ccSize    assumed size of country code prefix
   * @param candidate the normalized MSISDN string
   * @return a MSISDN version of the candidate string, if valid, else null
   */
  private static MSISDN lookupByCC(int ccSize, String candidate) {
    if (candidate.length() < ccSize) {
      return null;
    }
    int tryCC = Integer.valueOf(candidate.substring(0, ccSize));
    MSISDNScheme scheme = schemes.get(createKey(tryCC, candidate.length()));
    if (scheme == null) {
      return null;
    }
    MSISDNRule ccRule = scheme.rules.get(PartCode.CC);
    if (ccRule.isValid(tryCC) == false) {
      return null;
    }
    MSISDNRule ndcRule = scheme.rules.get(PartCode.NDC);
    int tryNDC = Integer.valueOf(candidate.substring(ccSize, ccSize + ndcRule.length));
    if (ndcRule.isValid(tryNDC) == false) {
      return null;
    }
    String snString = candidate.substring(ccSize + ndcRule.length);
    if (snString.length() != scheme.rules.get(PartCode.SN).length) {
      return null;
    }
    int sn = Integer.valueOf(snString);
    return MSISDN.create(tryCC, tryNDC, sn, scheme);
  }

  /**
   * process a external MSISDN string into a normalized string, removing
   * non-digits, and country code indicators ('+', '00') if present
   *
   * @param msisdnString possibly non-normalized
   * @return normalized copy of the input string
   */
  private static String normalize(String msisdnString) {
    String result = msisdnString.trim().replaceAll("[^\\d]", "");
    result = result.replaceFirst("^+", "");
    result = result.replaceFirst("^00", "");
    return result;
  }


  /**
   * @param value a numeric representation of an MSISDN
   * @return a MSISDN created from the supplied number
   */
  public static MSISDN fromLong(long value) {
    return createMSISDN(value + "");
  }

  /**
   * Iterate over the schemes in the supplied properties map, parsing each
   * definition and storing it against the scheme key in the resulting
   * map
   *
   * @param schemes defined as property key/value pairs
   * @return map of scheme keys (hash of country code + length) to definitions
   */
  private static Map<Integer, MSISDNScheme> getSchemeMap(Properties schemes) {
    assert schemes.size() > 0;
    Map<Integer, MSISDNScheme> result = new HashMap<>();
    for (Entry<Object, Object> entry : schemes.entrySet()) {
      MSISDNScheme scheme = MSISDNScheme.create((String) entry.getValue(), (String) entry.getKey());
      Integer cc = (Integer) scheme.rules.get(PartCode.CC).values.toArray()[0];
      result.put(createKey(cc, scheme.length), scheme);
    }
    assert result.size() == schemes.size();
    return result;
  }

  /**
   * Key to the scheme map is CC left-shifted by four bits plus the MSISDN length-1, this
   * allows up to 15 digits lengths to be specified with an arbitrarily long country code
   * E.g., 353 + 11 => 0x161b, or 1 + 14 (max US number):  0x001e
   */
  private static Integer createKey(int countryCode, int length) {
    return (countryCode << 4) + Math.abs(length - 1);
  }

  /**
   * Clear the currently registered MSISDN schemes in this singleton,
   * and reload the scheme definitions from the resource supplied
   *
   * @param schemeResource location (e.g., filename, URL) of MSISDN scheme definitions
   */
  private static void loadScheme(String schemeResource, Map<Integer, MSISDNScheme> schemes) throws IOException {
    schemes.clear();
    final Properties properties = new Properties();
    InputStream input = schemes.getClass().getResourceAsStream(schemeResource);
    if ( input == null) {
      throw new IOException("could not open " + schemeResource);
    }
    properties.load(input);
    schemes.putAll(getSchemeMap(properties));
  }


  public static Map<Integer, MSISDNScheme> loadDefaultScheme() {
    HashMap<Integer, MSISDNScheme> result = new HashMap<>();
    try {
      loadScheme(MSISDN_PROPERTIES_DEFAULT, result);
    } catch (IOException e) {
      // scheme left empty, not necessarily an error
    }
    return result;
  }

  public static void loadSchemesFromResource(String schemeResource) {
    schemes.clear();
    try {
      loadScheme(schemeResource, schemes);
    } catch (IOException e) {
      // scheme left empty, not necessarily an error
    }
  }

  public static void clearSchemes() {
    schemes.clear();
  }

  public static void addSchemes(List<MSISDNScheme> schemes) {
    for ( MSISDNScheme scheme : schemes) {
      addScheme ( scheme);
    }
  }

  public static void addScheme(MSISDNScheme scheme) {
    schemes.put(scheme.getKey(), scheme);
  }

}