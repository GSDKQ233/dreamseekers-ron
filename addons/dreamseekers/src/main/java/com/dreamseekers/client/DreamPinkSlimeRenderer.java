// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.pasterdream.client.renderer.PinkSlimeRenderer;
import net.pasterdream.entity.PinkSlimeEntity;

/** Paid RTS slime only. Keep the installed native model/texture and normal visibility policy.
 * The original renderer answers !isAlive(), sending healthy units through the faint-body path.
 * Never change native wild slimes, entity invisibility, viewer rules or fog/distance culling.
 */
public final class DreamPinkSlimeRenderer extends PinkSlimeRenderer {
    public DreamPinkSlimeRenderer(EntityRendererProvider.Context context) { super(context); }

    @Override
    protected boolean isBodyVisible(PinkSlimeEntity entity) {
        // Exact LivingEntityRenderer policy; do not suppress ordinary death rendering either.
        return !entity.isInvisible();
    }
}
