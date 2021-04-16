package com.google.idea.sdkcompat.python;

import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.sdk.PyDetectedSdk;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;

public class FallbackPythonSdkSuggesterCompat {

    /** #api202: getVersion changed to getMinor/getMajor in 2021.1 */
    public interface FallbackPythonSdkSuggesterAdapter {
        // PyDetectedSdk does not have a proper version/language level, so go via PythonSdkFlavor
        static LanguageLevel getSdkLanguageLevel(PyDetectedSdk sdk) {
          String sdkHomepath = sdk.getHomePath();
          PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(sdkHomepath);
          if (flavor == null) {
            return LanguageLevel.getDefault();
          }
          return flavor.getLanguageLevel(sdkHomepath);
        }

        static int sdkVersionComparator(PyDetectedSdk o1, PyDetectedSdk o2) {
          final int majorVersionComparison = Integer.compare(
                  getSdkLanguageLevel(o1).getMajorVersion(), getSdkLanguageLevel(o2).getMajorVersion());
          final int minorVersionComparison = Integer.compare(
                  getSdkLanguageLevel(o1).getMinorVersion(), getSdkLanguageLevel(o2).getMinorVersion());
          return majorVersionComparison != 0 ? majorVersionComparison : minorVersionComparison;
          }
    }
}
