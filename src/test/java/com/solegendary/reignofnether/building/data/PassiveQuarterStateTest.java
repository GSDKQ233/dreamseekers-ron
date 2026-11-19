package com.solegendary.reignofnether.building.data;

public final class PassiveQuarterStateTest {
    private static int assertions;
    private static void eq(long got,long expected,String message){assertions++;if(got!=expected)throw new AssertionError(message+" got="+got+" expected="+expected);}
    private static void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){rates();migration();overload();capture();restart();bounds();System.out.println("PASSIVE_QUARTER_STATE_PASS scenarios=6 assertions="+assertions);}
    private static void rates(){
        int[] rates={4,6,8,9,10,11,12};
        for(int workers=0;workers<=6;workers++){
            OreProductionState production=new OreProductionState();OreMineTraitState trait=new OreMineTraitState();trait.observe("A",0);long produced=0;
            for(int t=1;t<=80000;t++){
                int credit=trait.tick(true,workers,false);eq(credit,rates[workers],"credit by active count not index");
                produced+=production.advance("A",true,credit);eq(produced,(long)t*rates[workers]/160,"exact long-run ratio");eq(production.getQuarterTicks(),(long)t*rates[workers]%160,"exact remainder");
            }
        }
        OreProductionState base=new OreProductionState();for(int t=1;t<=40;t++)eq(base.tick("A",true),t==40?1:0,"legacy tick unchanged");
    }
    private static void migration(){
        for(int t=0;t<40;t++)eq(OreProductionState.fromSaved(t,"A").getQuarterTicks(),t*4,"legacy whole progress retained");
        for(int t=0;t<80;t++)eq(OreProductionState.fromSavedHalfTicks(t,"A").getQuarterTicks(),t*2,"legacy half retained");
        for(int t=0;t<160;t++)eq(OreProductionState.fromSavedQuarterTicks(t,"A").getQuarterTicks(),t,"quarter retained");
        OreMineTraitState fresh=new OreMineTraitState();check(!fresh.observe("A",0),"absent trait in old save must not reset old production");
        OreProductionState old=OreProductionState.fromSaved(39,"A");eq(old.advance("A",true,4),1,"old last partial period pays once");
    }
    private static void overload(){
        OreMineTraitState s=new OreMineTraitState();check(!s.activateOverload(),"unbound cannot activate");s.observe("A",0);check(s.canActivateOverload()&&s.activateOverload(),"bound start");check(!s.activateOverload(),"duplicate state activation denied");
        OreProductionState production=new OreProductionState();int income=0;
        for(int i=0;i<400;i++){eq(s.tick(true,6,true),8,"piglin no worker stacking");income+=production.advance("A",true,8);}
        eq(income,20,"400 doubled ticks yield20 total ore");eq(s.getOverloadTicks(),0,"boost consumed exactly");eq(s.getCooldownTicks(),800,"start-based cooldown");check(!s.activateOverload(),"remaining cooldown blocks");
        for(int i=0;i<800;i++)eq(s.tick(false,0,true),0,"paused no credit");check(s.canActivateOverload(),"inactive running ticks finish cooldown");
        s.activateOverload();for(int i=0;i<1200;i++)s.tick(false,0,true);eq(s.getOverloadTicks(),400,"pause/full/incomplete retain paid boost");eq(s.getCooldownTicks(),0,"pause still advances cooldown");check(!s.canActivateOverload(),"unconsumed boost blocks even if cooldown elapsed");
    }
    private static void capture(){
        OreMineTraitState restored=OreMineTraitState.fromSaved("A",399,1001);check(!restored.observe("A",0),"ordinary JVM reload first revision0 preserves boost");eq(restored.getOverloadTicks(),399,"reload budget kept");
        check(restored.observe("B",1),"capture seen");eq(restored.getOverloadTicks(),0,"capture removes boost");eq(restored.getCooldownTicks(),1001,"capture preserves cooldown");check(restored.observe("A",2),"runtime ABA seen");
        OreMineTraitState early=OreMineTraitState.fromSaved("A",399,1001);check(early.observe("A",2),"pre-firstbind ABA revision proves capture");eq(early.getOverloadTicks(),0,"early ABA clears boost");eq(early.getCooldownTicks(),1001,"early ABA preserves cooldown");
        OreMineTraitState exhausted=OreMineTraitState.fromSaved("A",399,1001);check(exhausted.observe("A",Long.MIN_VALUE),"negative revision invalidates");eq(exhausted.tick(true,6,true),0,"exhausted revision fail closed");check(!exhausted.activateOverload(),"exhausted cannot activate");eq(exhausted.getCooldownTicks(),1000,"exhausted still runs cooldown");
        OreProductionState p=OreProductionState.fromSavedQuarterTicks(159,"A");eq(p.advance("B",false,12),0,"inactive capture no reward");eq(p.getQuarterTicks(),0,"inactive capture clears credit");
    }
    private static void restart(){
        OreProductionState uninterrupted=new OreProductionState(),restored=new OreProductionState();OreMineTraitState a=new OreMineTraitState(),b=new OreMineTraitState();a.observe("A",0);b.observe("A",0);a.activateOverload();b.activateOverload();
        int sumA=0,sumB=0;
        for(int tick=0;tick<2400;tick++){
            boolean active=tick%7!=0;
            sumA+=uninterrupted.advance("A",active,a.tick(active,0,true));sumB+=restored.advance("A",active,b.tick(active,0,true));
            if(tick==113||tick==407||tick==1100){restored=OreProductionState.fromSavedQuarterTicks(restored.getQuarterTicks(),restored.getOwner());b=OreMineTraitState.fromSaved(b.getOwner(),b.getOverloadTicks(),b.getCooldownTicks());check(!b.observe("A",0),"normal restarted binding");}
            eq(sumB,sumA,"same income through restarts");eq(restored.getQuarterTicks(),uninterrupted.getQuarterTicks(),"saved partial credit conserved");eq(b.getOverloadTicks(),a.getOverloadTicks(),"saved paid boost conserved");eq(b.getCooldownTicks(),a.getCooldownTicks(),"saved cooldown conserved");
        }
    }
    private static void bounds(){
        eq(OreProductionState.fromSaved(Integer.MAX_VALUE,"A").getQuarterTicks(),156,"legacy bound before multiply");eq(OreProductionState.fromSavedHalfTicks(Integer.MAX_VALUE,"A").getQuarterTicks(),158,"half bound before multiply");eq(OreProductionState.fromSavedQuarterTicks(Integer.MAX_VALUE,"A").getQuarterTicks(),159,"quarter bound");
        eq(OreProductionState.fromSavedQuarterTicks(159," ").getQuarterTicks(),0,"empty owner can't hold credit");eq(OreProductionState.fromSaved(-1,"A").getQuarterTicks(),0,"negative legacy zero");
        OreMineTraitState s=OreMineTraitState.fromSaved("",Integer.MAX_VALUE,Integer.MAX_VALUE);eq(s.getOverloadTicks(),0,"unowned boost zero");eq(s.getCooldownTicks(),1200,"unowned cooldown retained bounded");
        eq(OreMineTraitState.workerQuarterCredit(-1),4,"negative workers base only");eq(OreMineTraitState.workerQuarterCredit(Integer.MAX_VALUE),12,"worker upper bound");
        OreProductionState p=new OreProductionState();eq(p.advance("A",true,Integer.MAX_VALUE),0,"one tick cannot mint a full cycle");eq(p.getQuarterTicks(),12,"credit clamped");eq(p.advance("A",false,12),0,"inactive no income");eq(p.getQuarterTicks(),12,"inactive no progress");
    }
}
