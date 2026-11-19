package com.solegendary.reignofnether.research.economy;
import java.util.UUID;
public class EconomyRequestWindowTest {
    static int assertions;
    static void check(boolean b){assertions++;if(!b)throw new AssertionError("assertion "+assertions);}
    record Request(String kind,String payload){}
    public static void main(String[]args){
        var window=new EconomyRequestWindow<Request>();var research=new Request("research","A");
        check(window.admit(1,research).execute());window.finish(1,"insufficient_resources");
        check(!window.admit(1,research).execute());check(window.admit(1,research).status().equals("insufficient_resources"));
        check(window.admit(1,new Request("exchange","A")).status().equals("replay_mismatch"));
        check(window.admit(1,new Request("research","B")).status().equals("replay_mismatch"));
        for(long i=2;i<=65;i++){check(window.admit(i,research).execute());window.finish(i,"applied");}
        check(window.cachedResults()==64);check(window.admit(1,research).status().equals("stale_request"));
        check(!window.admit(1,research).execute());check(!window.admit(0,research).execute());
        check(!window.admit(-1,research).execute());check(!window.admit(Long.MAX_VALUE,research).execute());
        check(window.highWater()==65);check(window.admit(66,research).execute());
        check(window.admit(66,research).status().equals("failed_closed"));
        var sessions=new EconomyRequestSessions<Object,Request>();Object conn=new Object();UUID epoch=UUID.randomUUID();
        var original=sessions.current(conn,"owner",epoch);UUID session=original.session;
        check(sessions.current(conn,"owner",epoch)==original); // QUERY never rotates
        check(original.admit(session,epoch,1,research).execute());original.window.finish(1,"applied");
        UUID nextEpoch=UUID.randomUUID();var reset=sessions.current(conn,"owner",nextEpoch);
        check(reset!=original);check(!reset.admit(session,epoch,1,research).execute());
        check(!reset.admit(session,nextEpoch,1,research).execute());check(reset.window.highWater()==0);
        var changedOwner=sessions.current(conn,"other",nextEpoch);check(changedOwner!=reset);
        sessions.remove(conn);Object newConnection=new Object();var reconnected=sessions.current(newConnection,"owner",epoch);
        check(!reconnected.admit(session,epoch,1,research).execute());
        check(sessions.size()==1);sessions.removeIf(c->true);check(sessions.size()==0);
        var almost=new EconomyRequestWindow<Request>();check(almost.admit(Long.MAX_VALUE-1,research).execute());
        check(!almost.admit(Long.MAX_VALUE,research).execute());check(almost.highWater()==Long.MAX_VALUE-1);
        // Continuous ledger changes are not replay tokens: admitted action obtains current CAS afterwards.
        int revision=0,debits=0;var incomeWindow=new EconomyRequestWindow<Request>();
        for(int tick=1;tick<=20;tick++)revision++;
        if(incomeWindow.admit(1,research).execute()){int expected=revision;check(expected==20);debits++;revision++;incomeWindow.finish(1,"applied");}
        for(int tick=0;tick<20;tick++)revision++;
        if(incomeWindow.admit(1,research).execute())debits++;
        check(debits==1);check(revision==41);
        System.out.println("PASS "+assertions+" assertions; pure replay/session helper only, no Forge handler or GUI execution");
    }
}
