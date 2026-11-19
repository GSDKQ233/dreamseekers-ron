package com.solegendary.reignofnether.resources.kill;

import com.solegendary.reignofnether.resources.Resources;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Transfers an existing real-hit witness only inside the synchronous final health write.
 * This is not a damage-source registration API. Missing authority never prevents health damage. */
public final class EconomicSharedDamageHooks {
    private EconomicSharedDamageHooks() {}
    private static final ThreadLocal<Write> WRITE=new ThreadLocal<>();
    private static final ThreadLocal<Share> SHARE=new ThreadLocal<>();
    private static final class Write {
        final LivingEntity victim; final DamageSource source; boolean captured, closed;
        Write(LivingEntity victim,DamageSource source){this.victim=victim;this.source=source;}
    }
    private static final class Identity {
        final EconomicUnitIdentity.Stamp stamp; final Resources account; final java.util.UUID epoch;
        Identity(LivingEntity entity) {
            stamp=EconomicUnitIdentity.capture(entity);
            account=RtsProjectileProvenance.account(stamp.owner());
            epoch=account==null?null:account.accountEpoch;
        }
        boolean valid() {
            return stamp.sameOwner()&&RtsProjectileProvenance.registered(stamp.entity())
                    &&account!=null&&account.extendedStateSupported()
                    &&RtsProjectileProvenance.account(stamp.owner())==account&&epoch.equals(account.accountEpoch);
        }
    }
    private static Identity identity(LivingEntity entity) {
        if(!(entity instanceof com.solegendary.reignofnether.unit.interfaces.Unit unit)
                ||unit.getOwnerName()==null||unit.getOwnerName().isBlank()
                ||!(entity.level() instanceof ServerLevel))return null;
        return new Identity(entity);
    }
    /** Called only by the final-health-write dispatcher, never by damage-source adapters. */
    public static void write(LivingEntity victim,DamageSource source,Runnable write) {
        Share sharing=SHARE.get();
        if(sharing!=null){sharing.tainted=true;write.run();return;}
        Write previous=WRITE.get(),frame=new Write(victim,source);
        WRITE.set(frame);
        try {write.run();}
        finally {frame.closed=true;if(previous==null)WRITE.remove();else WRITE.set(previous);}
    }
    /** Read-only combat attribution for the original or shared final write; grants no kill claim. */
    public static String combatOwner(LivingEntity victim,DamageSource source) {
        if(source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL)
                ||source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD))return null;
        RtsProjectileProvenance.DamageEvidence evidence;
        Share share=SHARE.get();
        if(share!=null) {
            if(share.tainted||share.partner!=victim||share.token.source!=source||share.identity==null
                    ||!share.identity.valid()||!share.token.valid())return null;
            evidence=share.token.evidence;
        } else {
            Write frame=WRITE.get();
            if(frame==null||frame.closed||frame.victim!=victim||frame.source!=source
                    ||!(victim.level() instanceof ServerLevel level)||!level.getServer().isSameThread())return null;
            evidence=RtsProjectileProvenance.captureDamage(source,victim);
        }
        return evidence!=null&&evidence.valid()
                &&evidence.shooter() instanceof com.solegendary.reignofnether.unit.interfaces.Unit unit
                ?unit.getOwnerName():null;
    }
    public static Token capture(LivingEntity victim,DamageSource source,LivingEntity partner) {
        Write frame=WRITE.get();
        if(SHARE.get()!=null||frame==null||frame.closed||frame.captured
                ||frame.victim!=victim||frame.source!=source
                ||!(victim.level() instanceof ServerLevel level)||!level.getServer().isSameThread())
            return new Token(null,victim,source,null,null,null,0,partner,null);
        frame.captured=true;
        Identity original=identity(victim);
        RtsProjectileProvenance.DamageEvidence evidence=original!=null&&original.valid()
                ?RtsProjectileProvenance.captureDamage(source,victim):null;
        Identity partnerIdentity=partner!=victim&&partner.level()==level&&partner.isAlive()&&!partner.isRemoved()
                ?identity(partner):null;
        return new Token(frame,victim,source,original,evidence,level,level.getServer().getTickCount(),partner,partnerIdentity);
    }
    public static final class Token {
        private final Write frame; private final LivingEntity original; private final DamageSource source;
        private final Identity originalIdentity; private final RtsProjectileProvenance.DamageEvidence evidence;
        private final ServerLevel level; private final int tick; private boolean used;
        private final LivingEntity boundPartner; private final Identity boundIdentity;
        private Token(Write frame,LivingEntity original,DamageSource source,Identity originalIdentity,
                      RtsProjectileProvenance.DamageEvidence evidence,ServerLevel level,int tick,
                      LivingEntity boundPartner,Identity boundIdentity) {
            this.frame=frame;this.original=original;this.source=source;this.originalIdentity=originalIdentity;
            this.evidence=evidence;this.level=level;this.tick=tick;
            this.boundPartner=boundPartner;this.boundIdentity=boundIdentity;
        }
        /** One-shot: even an unqualified hit executes its write, under a deny-evidence scope. */
        public void run(LivingEntity partner,Runnable writeAndDie) {
            if(used)return;
            used=true;
            Share previous=SHARE.get();
            if(previous!=null)previous.tainted=true;
            Identity partnerIdentity=level!=null&&level.getServer().isSameThread()
                    &&partner==boundPartner&&partner!=original&&partner.level()==level&&!partner.isRemoved()
                    ?boundIdentity:null;
            Share share=new Share(this,partner,partnerIdentity);
            if(previous!=null)share.tainted=true;
            SHARE.set(share);
            try {writeAndDie.run();}
            finally {if(previous==null)SHARE.remove();else SHARE.set(previous);}
        }
        private boolean valid() {
            return frame!=null&&!frame.closed&&WRITE.get()==frame&&frame.victim==original&&frame.source==source
                    &&level!=null&&level.getServer().isSameThread()&&level.getServer().getTickCount()==tick
                    &&original.level()==level&&originalIdentity!=null&&originalIdentity.valid()
                    &&evidence!=null&&evidence.valid();
        }
    }
    private static final class Share {
        final Token token; final LivingEntity partner; final Identity identity; boolean captured,tainted;
        Share(Token token,LivingEntity partner,Identity identity){this.token=token;this.partner=partner;this.identity=identity;}
    }
    static boolean active(){return SHARE.get()!=null;}
    static RtsProjectileProvenance.DamageEvidence captureShared(LivingEntity victim,DamageSource source) {
        Share share=SHARE.get();
        if(share==null)return null;
        if(share.captured||share.tainted||share.partner!=victim||share.token.source!=source) {
            share.tainted=true;return null;
        }
        share.captured=true;
        // Scope gates capture only. Pending deaths retain the original witness for next-tick validation.
        return share.identity!=null&&share.identity.valid()&&share.token.valid()?share.token.evidence:null;
    }
    static void clear(){WRITE.remove();SHARE.remove();}
}
