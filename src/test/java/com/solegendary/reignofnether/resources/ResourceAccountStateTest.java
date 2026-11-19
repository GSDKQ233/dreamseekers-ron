package com.solegendary.reignofnether.resources;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Standalone account/NBT codec checks; does not start a Minecraft server or world. */
public final class ResourceAccountStateTest {
    private static int assertions;
    private static final ResourceLocation SHADOW=ResourceLocation.fromNamespaceAndPath("test_economy","shadow");
    private static final ResourceLocation UNKNOWN=ResourceLocation.fromNamespaceAndPath("missing_addon","unloaded_balance");
    static void check(boolean ok,String message){assertions++;if(!ok)throw new AssertionError(message);}
    static void equal(Object actual,Object expected,String message){check(java.util.Objects.equals(actual,expected),message+" expected="+expected+" actual="+actual);}
    private static Resources account(){return new Resources("OriginalPayer",100,200,300,4);}
    private static CompoundTag save(Resources r){CompoundTag n=new CompoundTag();r.writeExtended(n);return n;}
    private static Resources restore(CompoundTag n){Resources r=account();r.readExtended(n);return r;}
    public static void main(String[] args){
        legacyAndRoundtrip();unknownBalanceAndMalformedBounds();futureAndOversizeQuarantine();receiptImmutabilityAndRoundtrip();resetAndCopy();
        System.out.println("RESOURCE_ACCOUNT_STATE_PASS scenarios=5 assertions="+assertions);
    }
    private static void legacyAndRoundtrip(){
        Resources r=restore(new CompoundTag());check(r.extendedStateSupported(),"legacy account supported");check(r.accountEpoch!=null,"legacy epoch generated");equal(r.revision,0L,"legacy revision zero");check(r.extra.isEmpty(),"legacy extension balances zero");equal(r.food,100,"ordinary balance not mutated by extension migration");
        UUID epoch=r.accountEpoch;r.extra.put(SHADOW,123);r.conversionRemainders.put("test/shadow",9876L);r.revision=53;
        Resources restored=restore(save(r));equal(restored.accountEpoch,epoch,"ordinary reload retains account epoch");equal(restored.revision,53L,"revision roundtrip");equal(restored.extra.get(SHADOW),123,"extension balance roundtrip");equal(restored.conversionRemainders.get("test/shadow"),9876L,"conversion remainder roundtrip");equal(save(restored),save(r),"canonical snapshot stable on second encode");
    }
    private static void unknownBalanceAndMalformedBounds(){
        Resources r=account();r.extra.put(UNKNOWN,174);Resources restored=restore(save(r));equal(restored.extra.get(UNKNOWN),174,"valid unknown resource preserved");check(ResourceExtensionRegistry.get(UNKNOWN)==null,"unknown saved ID does not self-register trade permission");
        CompoundTag n=save(r),ext=n.getCompound("resourceExtensions");CompoundTag extra=ext.getCompound("extra");extra.putInt("test_economy:negative",-6);extra.putLong("test_economy:overflow",Long.MAX_VALUE);extra.putString("test_economy:wrong_type","123");ext.put("extra",extra);ext.putLong("revision",-4);
        CompoundTag remainders=ext.getCompound("remainders");remainders.putLong("negative",-1);remainders.putLong("large",Long.MAX_VALUE);ext.put("remainders",remainders);
        Resources bad=restore(n);equal(bad.revision,0L,"negative revision clamps safely");equal(bad.extra.getOrDefault(ResourceLocation.parse("test_economy:negative"),0),0,"negative extra cannot mint or create debt");equal(bad.extra.getOrDefault(ResourceLocation.parse("test_economy:overflow"),0),Integer.MAX_VALUE,"long value bounds checked before narrowing");equal(bad.extra.getOrDefault(ResourceLocation.parse("test_economy:wrong_type"),0),0,"nonnumeric extension does not become valid money");equal(bad.conversionRemainders.getOrDefault("negative",0L),0L,"negative remainder zero");equal(bad.conversionRemainders.get("large"),9999L,"remainder bounded below divisor");
    }
    private static void futureAndOversizeQuarantine(){
        CompoundTag future=save(account());future.getCompound("resourceExtensions").putInt("version",999);future.getCompound("resourceExtensions").putString("futureData","must survive");Resources f=restore(future);check(!f.extendedStateSupported(),"unknown future schema fails closed");equal(save(f),future,"future payload retained without destructive downgrade");
        CompoundTag invalid=save(account());CompoundTag invalidIds=new CompoundTag();invalidIds.putInt("INVALID ID!",42);invalid.getCompound("resourceExtensions").put("extra",invalidIds);Resources invalidAccount=restore(invalid);check(!invalidAccount.extendedStateSupported(),"invalid resource syntax quarantines account");equal(save(invalidAccount),invalid,"invalid ID evidence preserved");
        CompoundTag badVersion=save(account());badVersion.getCompound("resourceExtensions").putLong("version",4294967297L);Resources bv=restore(badVersion);check(!bv.extendedStateSupported(),"wide schema version cannot truncate to supported version");equal(save(bv),badVersion,"wide version original payload preserved");
        CompoundTag outer=new CompoundTag();outer.putString("resourceExtensions","not an account compound");Resources o=restore(outer);check(!o.extendedStateSupported(),"wrong outer tag type rejected");equal(save(o),outer,"wrong outer type is preserved exactly");
        CompoundTag duplicateAliases=save(account());CompoundTag aliases=new CompoundTag();aliases.putInt("a",1);aliases.putInt("minecraft:a",2);duplicateAliases.getCompound("resourceExtensions").put("extra",aliases);Resources alias=restore(duplicateAliases);check(!alias.extendedStateSupported(),"normalized duplicate resource keys quarantined");equal(save(alias),duplicateAliases,"duplicate alias evidence preserved");
        CompoundTag large=save(account()),extra=new CompoundTag();for(int i=0;i<=ResourceAccountCodec.MAX_EXTRA;i++)extra.putInt("oversize:r"+i,i);large.getCompound("resourceExtensions").put("extra",extra);Resources l=restore(large);check(!l.extendedStateSupported(),"oversize extension account quarantined");equal(save(l),large,"oversize payload retained rather than truncated");
    }
    private static void receiptImmutabilityAndRoundtrip(){
        Resources r=account();Map<ResourceLocation,Integer> quote=new HashMap<>();quote.put(SHADOW,70);UUID id=UUID.randomUUID();ResourcePaidReceipt paid=new ResourcePaidReceipt(id,r.accountEpoch,"research/test",1,2,3,4,quote);quote.put(SHADOW,999);equal(paid.extra().get(SHADOW),70,"receipt snapshots paid price not mutable quote");
        boolean immutable=false;try{paid.extra().put(SHADOW,999);}catch(UnsupportedOperationException expected){immutable=true;}check(immutable,"receipt paid components immutable");r.paidReceipts.put(id,paid);Resources restored=restore(save(r));equal(restored.paidReceipts.get(id),paid,"receipt exact amounts/payer/identity roundtrip");
        CompoundTag duplicate=save(r);ListTag receipts=duplicate.getCompound("resourceExtensions").getList("receipts",10);receipts.add(receipts.getCompound(0).copy());Resources d=restore(duplicate);check(!d.extendedStateSupported(),"duplicate receipt IDs quarantine rather than double-refund");equal(save(d),duplicate,"duplicate receipt evidence preserved");
    }
    private static void resetAndCopy(){
        Resources r=account();r.extra.put(SHADOW,1200);r.revision=47;r.conversionRemainders.put("fraction",17L);UUID previous=r.accountEpoch;
        Resources copy=account();copy.copyExtendedFrom(r);equal(copy.accountEpoch,previous,"snapshot copy retains epoch");equal(copy.extra.get(SHADOW),1200,"legitimate refunds over normal cap persist");r.extra.put(SHADOW,1);equal(copy.extra.get(SHADOW),1200,"copy has independent mutable map");
        r.resetExtendedAccount();check(!r.accountEpoch.equals(previous),"new match reset rotates account epoch");equal(r.revision,0L,"reset revision zero");check(r.extra.isEmpty()&&r.paidReceipts.isEmpty()&&r.conversionRemainders.isEmpty(),"new account clears only its old extended ledger");
    }
}
