package com.solegendary.reignofnether.alliance;

import java.util.*;
import java.util.function.BiConsumer;

/** Recipient <- requester is the only storage/wire orientation, including opposite requests in flight. */
final class DirectedInvitations {
    private DirectedInvitations() {}
    static boolean add(Map<String, Set<String>> inbox, String to, String from) {
        return !from.equals(to) && inbox.computeIfAbsent(to, k -> new HashSet<>()).add(from);
    }
    static boolean remove(Map<String, Set<String>> inbox, String to, String from) {
        var senders = inbox.get(to);
        if (senders == null || !senders.remove(from)) return false;
        if (senders.isEmpty()) inbox.remove(to);
        return true;
    }
    static void forViewer(Map<String, Set<String>> inbox, String viewer, BiConsumer<String, String> send) {
        inbox.forEach((to, senders) -> {
            if (viewer.equals(to)) senders.forEach(from -> send.accept(to, from));
            else if (senders.contains(viewer)) send.accept(to, viewer);
        });
    }
}
