package org.dreeam.leaf.config.modules.misc;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class VanillaUsernameCheck extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.MISC.getBaseKeyName() + ".vanilla-username-check";
    }

    public static boolean removeAllCheck = false;
    public static boolean enforceSkullValidation = true;

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
    }
}
