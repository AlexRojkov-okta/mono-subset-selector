package com.okta.mono.ij.plug;

import java.util.regex.Pattern;

public class ModuleNameMatcher {
    private static Pattern runtimePattern = Pattern.compile("^runtimes\\.[a-z-A-Z]*\\z");

    boolean isIncluded(String name) {
        boolean isIncluded = runtimePattern.matcher(name).matches();

        // below three are for test project cause okta-core is too big
        isIncluded = isIncluded || "foo".equals(name);
        isIncluded = isIncluded || "bar".equals(name);

        return isIncluded;
    }

    public static void main(String[] args) {
        ModuleNameMatcher matcher = new ModuleNameMatcher();
        System.out.println("SelectSubsetDialog.main " + matcher.isIncluded("runtimes.mobile"));
        System.out.println("SelectSubsetDialog.main " + matcher.isIncluded("runtimes.mobile.api"));
    }
}
