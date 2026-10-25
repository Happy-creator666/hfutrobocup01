package hfutinvincible.tool.rescue;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RescuePolicyTest {
    @Test void shortLifeCanBeRescuedAndLongLifeCanStillBeTooLate() {
        assertTrue(RescuePolicy.evaluate(15, 0, 0, 6, 0, 3, 100, 1).feasible());
        assertFalse(RescuePolicy.evaluate(40, 15, 25, 20, 0, 3, 100, 1).feasible());
        assertFalse(RescuePolicy.evaluate(100, 0, 0, 6, 0, 3, 7, 1).feasible());
    }

    @Test void urgencyAndFreshnessAffectOtherwiseEqualTasks() {
        var urgent = RescuePolicy.evaluate(15, 0, 0, 6, 0, 3, 100, 1);
        var stable = RescuePolicy.evaluate(80, 0, 0, 6, 0, 3, 100, 1);
        var stale = RescuePolicy.evaluate(15, 0, 0, 6, 0, 3, 100, .3);
        assertTrue(urgent.score() < stable.score());
        assertTrue(stale.score() > urgent.score());
        assertFalse(RescuePolicy.evaluate(15, 0, 0, 6, 0, 3, 100, Double.NaN).feasible());
    }

    @Test void refugeQueueAndStaleInformationArePenalized() {
        assertEquals(5, RescuePolicy.refugeCost(5, 10, 0, true, 3, 4, 15));
        assertEquals(26, RescuePolicy.refugeCost(5, 0, 2, true, 3, 4, 15));
        assertEquals(9, RescuePolicy.refugeCost(5, 10, 0, false, 3, 4, 15));
        assertEquals(Double.POSITIVE_INFINITY, RescuePolicy.refugeCost(-1, 10, 0, true, 3, 4, 15));
    }

    @Test void commitmentHasImprovementThresholdAndExpires() {
        assertTrue(RescuePolicy.keepTarget(10, 9, 2, 10, .2));
        assertFalse(RescuePolicy.keepTarget(10, 7, 2, 10, .2));
        assertFalse(RescuePolicy.keepTarget(10, 9, 10, 10, .2));
        assertFalse(RescuePolicy.keepTarget(Double.POSITIVE_INFINITY, 9, 2, 10, .2));
    }

    @Test void diggingAccountsForStaggeredArrivalsInsteadOfCountingDistantWorkersImmediately() {
        assertEquals(10, RescuePolicy.diggingCompletion(10, List.of(0.0, 20.0)));
        assertEquals(5, RescuePolicy.diggingCompletion(10, List.of(0.0, 0.0)));
        assertEquals(7, RescuePolicy.diggingCompletion(10, List.of(0.0, 4.0)));
        assertEquals(Double.POSITIVE_INFINITY, RescuePolicy.diggingCompletion(10, List.of()));
    }
}
