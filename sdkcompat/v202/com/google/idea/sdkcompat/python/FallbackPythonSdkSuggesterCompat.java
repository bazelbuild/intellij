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
          return Integer.compare(getSdkLanguageLevel(o1).getVersion(), getSdkLanguageLevel(o2).getVersion());
      }
    }
}
