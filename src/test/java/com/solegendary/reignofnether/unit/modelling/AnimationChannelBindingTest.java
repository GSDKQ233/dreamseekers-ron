package com.solegendary.reignofnether.unit.modelling;

import com.solegendary.reignofnether.unit.modelling.animations.*;
import com.solegendary.reignofnether.unit.modelling.models.*;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.*;

/** Real layer baking, name lookup and vanilla keyframe application. No Minecraft instance, level, GUI or GPU. */
class AnimationChannelBindingTest {
    private static void allBindings(HierarchicalModel<?> model, Class<?> animations) throws Exception {
        List<Executable> checks = new ArrayList<>();
        for (Field field : animations.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == AnimationDefinition.class) {
                AnimationDefinition definition = (AnimationDefinition)field.get(null);
                assertTrue(definition.lengthInSeconds() > 0 && Float.isFinite(definition.lengthInSeconds()), field.getName());
                for (String bone : definition.boneAnimations().keySet())
                    checks.add(() -> assertTrue(model.getAnyDescendantWithName(bone).isPresent(),
                            animations.getSimpleName()+"."+field.getName()+" unresolved bone: "+bone));
            }
        }
        assertFalse(checks.isEmpty(), "actual public animation definitions were inspected");
        assertAll(checks);
    }

    @Test void enchanter() throws Exception { allBindings(new EnchanterModel<Entity>(EnchanterModel.createBodyLayer().bakeRoot()), EnchanterAnimations.class); }
    @Test void marauder() throws Exception { allBindings(new MarauderModel<Entity>(MarauderModel.createBodyLayer().bakeRoot()), MarauderAnimations.class); }
    @Test void necromancer() throws Exception { allBindings(new NecromancerModel<Entity>(NecromancerModel.createBodyLayer().bakeRoot()), NecromancerAnimations.class); }
    @Test void piglinMerchant() throws Exception { allBindings(new PiglinMerchantModel<Entity>(PiglinMerchantModel.createBodyLayer().bakeRoot()), PiglinMerchantAnimations.class); }
    @Test void royalGuard() throws Exception { allBindings(new RoyalGuardModel<Entity>(RoyalGuardModel.createBodyLayer().bakeRoot()), RoyalGuardAnimations.class); }
    @Test void wildfire() throws Exception { allBindings(new WildfireModel<Entity>(WildfireModel.createBodyLayer().bakeRoot()), WildfireAnimations.class); }
    @Test void windcaller() throws Exception { allBindings(new WindcallerModel<Entity>(WindcallerModel.createBodyLayer().bakeRoot()), WindcallerAnimations.class); }
    @Test void wraith() throws Exception { allBindings(new WraithModel<Entity>(WraithModel.createBodyLayer().bakeRoot()), WraithAnimations.class); }
    @Test void wretchedWraith() throws Exception { allBindings(new WretchedWraithModel<Entity>(WretchedWraithModel.createBodyLayer().bakeRoot()), WretchedWraithAnimations.class); }

    @Test void declaredRootChannelsActuallyMoveTheirRealModelRoot() {
        var enchanter = new EnchanterModel<Entity>(EnchanterModel.createBodyLayer().bakeRoot());
        var marauder = new MarauderModel<Entity>(MarauderModel.createBodyLayer().bakeRoot());
        var wildfire = new WildfireModel<Entity>(WildfireModel.createBodyLayer().bakeRoot());
        assertAll(
                () -> rootMoves(enchanter,EnchanterAnimations.ULTIMATE,1166),
                () -> rootMoves(marauder,MarauderAnimations.RUN,0),
                () -> rootMoves(wildfire,WildfireAnimations.WALK,1000));
    }

    private static void rootMoves(HierarchicalModel<?> model, AnimationDefinition animation, long millis) {
        float before = model.root().y;
        KeyframeAnimations.animate(model,animation,millis,1,new Vector3f());
        assertTrue(Float.isFinite(model.root().y));
        assertNotEquals(before,model.root().y,"declared nonzero root translation must not be silently discarded");
    }

    @Test void wildfireIdleAndWalkMoveAllThreeUpperBodyRods() {
        List<Executable> checks = new ArrayList<>();
        for (AnimationDefinition definition : List.of(WildfireAnimations.IDLE,WildfireAnimations.WALK)) {
            for (int i=0;i<3;i++) {
                int rod = i;
                checks.add(() -> {
                    var model = new WildfireModel<Entity>(WildfireModel.createBodyLayer().bakeRoot());
                    ModelPart part = model.getAnyDescendantWithName("upperBodyParts"+rod).orElseThrow();
                    float before = part.y;
                    long millis = new long[]{1333,750,833}[rod];
                    KeyframeAnimations.animate(model,definition,millis,1,new Vector3f());
                    assertTrue(Float.isFinite(part.y));
                    assertNotEquals(before,part.y,"actual rod "+rod+" has a nonzero keyframe at this time");
                });
            }
        }
        assertAll(checks);
    }
}
