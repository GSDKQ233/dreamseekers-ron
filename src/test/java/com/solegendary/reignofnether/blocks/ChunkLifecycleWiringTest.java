package com.solegendary.reignofnether.blocks;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ChunkLifecycleWiringTest {
    private String read(String path)throws Exception{return Files.readString(Path.of("src/main/java/com/solegendary/reignofnether/"+path));}
    @Test void snowUsesDedicatedLifetimeKeyAndCapturedRelease()throws Exception {
        String s=read("blocks/WraithSnowBlockEntity.java");
        assertTrue(s.contains("TicketType<UUID> SNOW_TICKET"));assertFalse(s.contains("TicketType.FORCED"));
        assertTrue(s.contains("level.getBlockEntity(worldPosition) != this"));
        assertTrue(s.contains("ticketLevel == level && chunkPos.equals(ticketChunk)"));
        assertTrue(s.contains("removeRegionTicket(SNOW_TICKET, oldChunk, 1, ticketOwner)"));
    }
    @Test void startupSnapshotDoesNotDeleteUnresolvedOwners()throws Exception {
        String s=read("unit/UnitServerEvents.java");
        String callback=s.substring(s.indexOf("public static void registerChunkTicketValidation()"),s.indexOf("private static void reconcileRestoredUnitTickets"));
        assertTrue(callback.contains("helper.getEntityTickets()"));assertFalse(callback.contains("removeTicket"));assertFalse(callback.contains("removeAllTickets"));
        assertTrue(s.contains("level.getEntity(entity.getUUID()) != entity"));
        assertTrue(s.contains("if (tickets.persistent) reconcileRestoredUnitTickets(entity, tickets)"));
        assertTrue(s.contains("restoredUnitTickets.clear()"));
        assertTrue(read("ReignOfNether.java").contains("event.enqueueWork(com.solegendary.reignofnether.unit.UnitServerEvents::registerChunkTicketValidation)"));
    }
}
