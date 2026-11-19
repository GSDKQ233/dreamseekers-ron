package com.dreamseekers.production;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

/** Actual pure composition plus source wiring; this does not claim rendered-client GUI coverage. */
class DreamProductionTooltipTest {
    @Test void everyHoverResolvesCurrentQuotePrerequisitesPageAndLanguage() {
        var contents = new AtomicReference<>(List.of("unit", "cost=100", "requires anchor"));
        int[] page = {0};
        String[] locale = {"Page"};
        var live = DreamUnitProduction.decorateLiveTooltip(contents::get,
                lines -> lines.add(locale[0] + " " + (page[0] + 1) + "/3"));
        assertEquals(List.of("unit", "cost=100", "requires anchor", "Page 1/3"), live.get());
        contents.set(List.of("unit", "cost=70", "requires anchor", "shadow insufficient"));
        page[0] = 1;
        locale[0] = "页";
        assertEquals(List.of("unit", "cost=70", "requires anchor", "shadow insufficient", "页 2/3"), live.get());
    }

    @Test void repeatedHoverDecoratesFreshCopyWithoutMutatingOrAccumulatingInBase() {
        var base = new ArrayList<>(List.of("unit", "cost", "prerequisite", "refund"));
        var live = DreamUnitProduction.decorateLiveTooltip(() -> base, lines -> lines.add("page"));
        var first = live.get();
        for (int i = 0; i < 100; i++) {
            var next = live.get();
            assertNotSame(first, next);
            assertEquals(List.of("unit", "cost", "prerequisite", "refund", "page"), next);
        }
        first.add("external mutation");
        assertEquals(List.of("unit", "cost", "prerequisite", "refund"), base);
        assertEquals(List.of("unit", "cost", "prerequisite", "refund", "page"), live.get());
    }

    @Test void decoratorsComposeAfterLiveLinesAndOrdinaryButtonCanRemainUndecorated() {
        Consumer<List<String>> page = lines -> lines.add("page");
        var decorated = DreamUnitProduction.decorateLiveTooltip(() -> List.of("current quote"),
                page.andThen(lines -> lines.add("context")));
        assertEquals(List.of("current quote", "page", "context"), decorated.get());
        var ordinary = DreamUnitProduction.decorateLiveTooltip(() -> List.of("current quote"), lines -> {});
        assertEquals(List.of("current quote"), ordinary.get());
    }

    @Test void hallUsesTheSameDecoratorAtCreationAndBeforeEveryRealRender() throws Exception {
        String root = "addons/dreamseekers/src/main/java/com/dreamseekers/";
        String recipe = Files.readString(Path.of(root + "production/DreamUnitProduction.java"));
        String pages = Files.readString(Path.of(root + "client/DreamProductionPages.java"));
        assertTrue(recipe.contains("return getStartButton(placement, key, lines -> {});"));
        assertTrue(recipe.contains("decorateLiveTooltip(() -> tooltip(placement), tooltipDecorator)"));
        assertTrue(recipe.contains("() -> true, liveTooltip.get(), this"),
                "A stale preview cannot prevent the player from requesting a fresh server decision");
        assertTrue(recipe.contains("if(!canAffordExtra(placement))text.add"), "Preview still warns on hover");
        int refresh = recipe.indexOf("this.tooltipLines = liveTooltip.get();");
        assertTrue(refresh >= 0);
        assertTrue(recipe.indexOf("super.renderTooltip(graphics, mouseX, mouseY);", refresh) > refresh);
        assertTrue(pages.contains("dream.getStartButton(placement,slots().get(index%6),pageTooltip)"));
        assertTrue(pages.contains("pageTooltip=lines->lines.add("));
        assertTrue(pages.contains("Component.translatable(\"dreamseekers.production.page\",state.page()+1,state.pages()).getVisualOrderText()"));
        assertTrue(pages.contains("if(!(recipe instanceof DreamUnitProduction))"),
                "creation-only fallback must not decorate live Dream buttons again");
        for (String liveField : List.of("var quote=getCost(true,placement.ownerName)",
                "ProductionWorkRates.percent(placement,this)", "dreamseekers.production.extra_cost",
                "dreamseekers.production.extra_insufficient", "research.economy.refund_notice",
                "Component.translatable(prerequisiteError)")) assertTrue(recipe.contains(liveField), liveField);
    }
}
