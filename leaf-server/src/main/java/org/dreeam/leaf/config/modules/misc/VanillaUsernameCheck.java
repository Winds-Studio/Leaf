package org.dreeam.leaf.config.modules.misc;

import org.dreeam.leaf.config.*;
import org.dreeam.leaf.config.annotations.*;

import java.util.regex.Pattern;

@ConfigClassInfo(category = ConfigCategory.MISC, name = "vanilla-username-check")
public class VanillaUsernameCheck implements ConfigModule {

    @ConfigInfo(name = "remove-all-check", comments = {
        """
            Remove Vanilla username check,
            allowing all characters as username.
            WARNING: UNSAFE, USE AT YOUR OWN RISK!""",
        """
            移除原版的用户名验证,
            让所有字符均可作为玩家名.
            警告: 完全移除验证非常不安全, 使用风险自负!"""
    })
    public static @Deprecated boolean removeAllCheck = false;

    @ConfigInfo(name = "enforce-skull-validation", comments = {
        """
            Enforce skull validation,
            preventing skulls with invalid names from disconnecting the client.""",
        """
            强制启用头颅验证,
            避免所有者带有特殊字符的头颅导致客户端掉线."""
    })
    public static boolean enforceSkullValidation = true;

    @ConfigInfo(name = "allow-old-players-join", comments = {
        """
            Allow old players to join the server after the username regex is changed,
            even if their names don't meet the new requirements.""",
        """
            允许老玩家加入修改用户名验证正则后的服务器,
            即使他们的用户名不满足修改后的正则."""
    })
    public static @Experimental boolean allowOldPlayersJoin = false;

    @ConfigInfo(name = "use-username-regex", comments = {
        """
            Use username regex to validate usernames,
            allowing only characters specified in the regex.""",
        """
            使用用户名正则来验证用户名,
            只允许正则指定的字符."""
    })
    public static boolean useUsernameRegex = false;

    @ConfigInfo(name = "username-regex", comments = {
        """
            Username regex,
            specifying the characters allowed in usernames.
            Default: ^[a-zA-Z0-9_.]*$""",
        """
            用户名正则,
            指定允许在用户名中使用的字符.
            默认: ^[a-zA-Z0-9_.]*$"""
    })
    private static String usernameRegexString = "^[a-zA-Z0-9_.]*$";

    public static @DoNotLoad Pattern usernameRegex;

    public static boolean shouldSkipNonPlayerNameCheck() { // helper
        return removeAllCheck || useUsernameRegex;
    }

    @Override
    public void onLoaded() {
        if (!usernameRegexString.isBlank()) {
            try {
                usernameRegex = Pattern.compile(usernameRegexString);
            } catch (Exception e) {
                LeafConfig.LOGGER.error("Invalid username regex {} found, falling back to default.", usernameRegexString, e);
            }
        }

        if (useUsernameRegex && removeAllCheck) {
            LeafConfig.LOGGER.warn("Found conflicting configuration, remove-all-check and use-username-regex cannot be enabled at same time, ignoring remove-all-check...");
            removeAllCheck = false;
        }
    }
}
