package org.dreeam.leaf.config.modules.misc;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;
import org.dreeam.leaf.config.LeafConfig;
import org.dreeam.leaf.config.annotations.Experimental;

import java.util.regex.Pattern;

public class VanillaUsernameCheck extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.MISC.getBaseKeyName() + ".vanilla-username-check";
    }

    @Deprecated
    public static boolean removeAllCheck = false;
    public static boolean enforceSkullValidation = true;
    @Experimental
    public static boolean allowOldPlayersJoin = false;
    public static boolean useUsernameRegex = false;
    private static final String defaultRegexString = "^[a-zA-Z0-9_.]*$";
    public static Pattern usernameRegex = Pattern.compile(defaultRegexString);
    public static boolean shouldSkipNonPlayerNameCheck() { // helper
        return removeAllCheck || useUsernameRegex;
    }

    @Override
    public void onLoaded() {
        removeAllCheck = config.getBoolean(getBasePath() + ".remove-all-check", removeAllCheck, config.pickStringRegionBased("""
                Remove Vanilla username check,
                allowing all characters as username.
                WARNING: UNSAFE, USE AT YOUR OWN RISK!""",
            """
                移除原版的用户名验证,
                让所有字符均可作为玩家名.
                警告: 完全移除验证非常不安全, 使用风险自负!"""));
        enforceSkullValidation = config.getBoolean(getBasePath() + ".enforce-skull-validation", enforceSkullValidation, config.pickStringRegionBased("""
                Enforce skull validation,
                preventing skulls with invalid names from disconnecting the client.""",
            """
                强制启用头颅验证,
                避免所有者带有特殊字符的头颅导致客户端掉线."""));
        allowOldPlayersJoin = config.getBoolean(getBasePath() + ".allow-old-players-join", allowOldPlayersJoin, config.pickStringRegionBased("""
                Allow old players to join the server after the username regex is changed,
                even if their names don't meet the new requirements.""",
            """
                允许老玩家加入修改用户名验证正则后的服务器,
                即使他们的用户名不满足修改后的正则."""));
        useUsernameRegex = config.getBoolean(getBasePath() + ".use-username-regex", useUsernameRegex, config.pickStringRegionBased("""
                Use username regex to validate usernames,
                allowing only characters specified in the regex.""",
            """
                使用用户名正则来验证用户名,
                只允许正则指定的字符."""));
        String regexString = config.getString(getBasePath() + ".username-regex", defaultRegexString, config.pickStringRegionBased(
            """
            Username regex,
            specifying the characters allowed in usernames.
            Default: %s""".formatted(defaultRegexString),
            """
            用户名正则,
            指定允许在用户名中使用的字符.
            默认: %s""".formatted(defaultRegexString)));
        if (!regexString.isBlank()) {
            try {
                usernameRegex = Pattern.compile(regexString);
            } catch (Exception e) {
                LeafConfig.LOGGER.error("Invalid username regex {} found, falling back to default.", regexString, e);
            }
        }
        if (useUsernameRegex && removeAllCheck) {
            LeafConfig.LOGGER.warn("Found conflicting configuration, remove-all-check and use-username-regex cannot be enabled at same time, ignoring remove-all-check...");
            removeAllCheck = false;
        }
    }
}
