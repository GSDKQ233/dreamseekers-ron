package com.solegendary.reignofnether.resources;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
/** Bounded optional namespaced account attachments. Unknown namespaces are retained verbatim.
 * Malformed attachment data is quarantined separately, never erasing money/paid refund receipts. */
public final class ResourceAddonData {
 public static final int MAX_NAMESPACES=16,MAX_DEPTH=8,MAX_TAGS=512,MAX_BYTES=65536;
 private ResourceAddonData(){}
 public static boolean valid(CompoundTag root) {
  if(root.size()>MAX_NAMESPACES)return false;
  for(String key:root.getAllKeys())if(key.length()>128||ResourceLocation.tryParse(key)==null||!ResourceLocation.tryParse(key).toString().equals(key)||!root.contains(key,Tag.TAG_COMPOUND))return false;
  try {int[] budget={MAX_TAGS,MAX_BYTES};if(!visit(root,0,budget))return false;
   // Count actual NBT binary bytes (including modified UTF-8), without allocating an unbounded byte array.
   var counter=new java.io.OutputStream(){int count;private void add(int n)throws java.io.IOException{if(n<0||count>MAX_BYTES-n)throw new java.io.IOException("Attachment size limit");count+=n;}public void write(int b)throws java.io.IOException{add(1);}public void write(byte[] b,int offset,int length)throws java.io.IOException{add(length);}};
   NbtIo.write(root,new java.io.DataOutputStream(counter));return true;
  }catch(java.io.IOException|RuntimeException invalid){return false;}
 }
 private static boolean visit(Tag tag,int depth,int[] budget) {
  if(depth>MAX_DEPTH||--budget[0]<0)return false;
  budget[1]-=8;if(budget[1]<0)return false;
  if(tag instanceof CompoundTag compound){for(String key:compound.getAllKeys()){budget[1]-=key.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;if(budget[1]<0||!visit(compound.get(key),depth+1,budget))return false;}}
  else if(tag instanceof ListTag list){for(Tag child:list)if(!visit(child,depth+1,budget))return false;}
  else if(tag instanceof StringTag str)budget[1]-=str.getAsString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
  else if(tag instanceof ByteArrayTag array)budget[1]-=array.getAsByteArray().length;
  else if(tag instanceof IntArrayTag array){if(array.getAsIntArray().length>MAX_BYTES/4)return false;budget[1]-=array.getAsIntArray().length*4;}
  else if(tag instanceof LongArrayTag array){if(array.getAsLongArray().length>MAX_BYTES/8)return false;budget[1]-=array.getAsLongArray().length*8;}
  else if(!(tag instanceof NumericTag))return false;
  return budget[1]>=0;
 }
 static void read(Resources account,CompoundTag parent) {
  account.addonData=new CompoundTag();account.opaqueAddonData=null;
  if(!parent.contains("addonData"))return;
  Tag raw=parent.get("addonData");
  if(!(raw instanceof CompoundTag compound)||!valid(compound)){account.opaqueAddonData=raw.copy();return;}
  account.addonData=compound.copy();
 }
 static void write(Resources account,CompoundTag parent) {
  if(account.opaqueAddonData!=null){parent.put("addonData",account.opaqueAddonData.copy());return;}
  if(!valid(account.addonData))throw new IllegalStateException("Invalid bounded account addon payload");
  if(!account.addonData.isEmpty())parent.put("addonData",account.addonData.copy());
 }
}
