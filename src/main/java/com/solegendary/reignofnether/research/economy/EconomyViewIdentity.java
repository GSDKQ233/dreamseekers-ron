package com.solegendary.reignofnether.research.economy;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/** Echo correlation only, never payment authority. A replacement placement gets a fresh view token. */
public final class EconomyViewIdentity<T> {
    private final Map<T,UUID> tokens=new WeakHashMap<>();
    public UUID token(T placement){return tokens.computeIfAbsent(placement,p->UUID.randomUUID());}
    public boolean matches(T placement,UUID echoed){
        UUID current=tokens.get(placement);
        return current!=null&&current.equals(echoed);
    }
    public void retain(Predicate<T> valid){tokens.keySet().removeIf(p->!valid.test(p));}
    public void clear(){tokens.clear();}
}
