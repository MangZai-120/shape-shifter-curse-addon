package net.jackcooper.shapeShifterCurseAddon.client;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Inserts the fear branch without rewriting a shader pack's ordinary blindness formula. */
public final class FearShaderPatch {
    private FearShaderPatch() {}

    private static final Pattern BLINDNESS_FOG = Pattern.compile(
            "(void\\s+DoBlindnessFog\\s*\\(inout\\s+vec3\\s+color\\s*,\\s*float\\s+lViewPos\\s*\\)\\s*\\{)"
                    + "([^{}]*mix\\s*\\(\\s*color\\s*,\\s*vec3\\s*\\(\\s*0\\.0\\s*\\)\\s*,\\s*fog\\s*\\)\\s*;[^{}]*)(\\})",
            Pattern.DOTALL);
    private static final String FEAR_BRANCH = "\n"
            + "    // [SSCA Fear] preserve the original non-fear body below\n"
            + "    if (abs(fogColor.r - 1.0) < 0.02 && abs(fogColor.g - 0.4314) < 0.02 && abs(fogColor.b - 0.7804) < 0.02) {\n"
            + "        float sscaFearFog = clamp(smoothstep(12.0, 16.0, lViewPos) * blindness, 0.0, 1.0);\n"
            + "        color = mix(color, fogColor, sscaFearFog);\n"
            + "        return;\n"
            + "    }\n";

    public static String patch(String source) {
        if (!source.contains("DoBlindnessFog") || source.contains("// [SSCA Fear]")) return source;
        Matcher matcher = BLINDNESS_FOG.matcher(source);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(
                    matcher.group(1) + FEAR_BRANCH + matcher.group(2) + matcher.group(3)));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
