package org.dreeam.leaf.config.modules.misc;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class OpLock extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.MISC.getBaseKeyName() + ".op-system-protection";
    }

    public static boolean preventOpChanges = false;

    @Override
    public void onLoaded() {
        config.addCommentRegionBased(getBasePath(), """
                When enabled, prevents plugins from programmatically changing player operator status.
                This helps maintain server security by blocking unauthorized op modifications.
                Server administrators can still manually manage ops through console/commands.""",
            """
                启用后，防止插件以编程方式更改玩家操作员状态。
                这有助于通过阻止未经授权的op修改来维护服务器安全性。
                服务器管理员仍可通过控制台/命令手动管理ops。""");

        preventOpChanges = config.getBoolean(getBasePath() + ".prevent-op-changes", preventOpChanges);
    }
}
