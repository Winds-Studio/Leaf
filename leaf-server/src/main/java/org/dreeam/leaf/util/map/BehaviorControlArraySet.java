package org.dreeam.leaf.util.map;

import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;

public class BehaviorControlArraySet<E extends LivingEntity> extends ObjectArraySet<BehaviorControl<? super E>> {
    private int running;

    public Object[] raw() {
        return a;
    }

    public void inc() {
        running++;
    }

    public void dec() {
        running--;
    }

    public boolean running() {
        return running != 0;
    }
}
