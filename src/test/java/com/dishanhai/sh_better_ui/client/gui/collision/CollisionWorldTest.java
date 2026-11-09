package com.dishanhai.sh_better_ui.client.gui.collision;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static com.dishanhai.sh_better_ui.client.gui.collision.CollisionBody.Anchor.*;
import static com.dishanhai.sh_better_ui.client.gui.collision.CollisionBody.Strategy.*;
import static org.junit.jupiter.api.Assertions.*;

/** 山海：共享二维布局和降级回归。 */
class CollisionWorldTest {
    @Test
    void expandsOnBothSidesAndRestoresOriginalPositions() {
        var world = lane(200);
        var left = world.add(rect(40, 30));
        var center = world.add(rect(70, 60)).anchor(CENTER, START).priority(10);
        var right = world.add(rect(130, 30));
        center.resize(100, 20);
        assertEquals(0, world.update(0, true));
        assertEquals(rect(20, 30), left.target());
        assertEquals(rect(50, 100), center.target());
        assertEquals(rect(150, 30), right.target());
        center.resize(60, 20);
        world.update(1, true);
        assertEquals(rect(40, 30), left.target());
        assertEquals(rect(130, 30), right.target());
    }

    @Test
    void propagatesThroughChainAndPushesBackAtViewportEdge() {
        var world = lane(100);
        var driver = world.add(rect(10, 20)).priority(10);
        var middle = world.add(rect(30, 25)).priority(2);
        var end = world.add(rect(55, 25));
        driver.moveTo(35, 0);
        assertEquals(0, world.update(0, true));
        assertEquals(30, driver.target().x(), 0.002);
        assertEquals(50, middle.target().x(), 0.002);
        assertEquals(75, end.target().x(), 0.002);
        assertEquals(100, end.target().right(), 0.002);
    }

    @Test
    void reservesSpaceForAnEntireDenseLaneBeforeYieldingTheDriver() {
        int count = 30;
        var world = lane(count * 20 + 10);
        var bodies = new ArrayList<CollisionBody>();
        for (int i = 0; i < count; i++) {
            bodies.add(world.add(rect(i * 20, 20)).priority(count - i));
        }
        world.update(0, true);
        bodies.get(0).moveTo(30, 0);
        assertEquals(0, world.update(1, true));
        for (int i = 0; i < count; i++) {
            assertEquals(rect(10 + i * 20, 20), bodies.get(i).target());
        }
    }

    @Test
    void distributesCompressionByResistanceAndHonorsHigherPriority() {
        var world = lane(100);
        var left = world.add(rect(0, 60)).strategy(COMPRESS).anchor(START, START)
                .compression(new CompressionModel(10, 20, 90, 20, 1));
        var right = world.add(rect(40, 60)).strategy(COMPRESS).anchor(END, START)
                .compression(new CompressionModel(10, 20, 90, 20, 3));
        assertEquals(0, world.update(0, true));
        assertEquals(45, left.target().width(), 0.002);
        assertEquals(55, right.target().width(), 0.002);
        right.priority(4);
        assertEquals(0, world.update(1, true));
        assertEquals(rect(0, 40), left.target());
        assertEquals(rect(40, 60), right.target());
    }

    @Test
    void compressesBothNeighborsOfAnExpandingControl() {
        var world = lane(90);
        var limits = new CompressionModel(15, 20, 60, 20, 1);
        var left = world.add(rect(0, 30)).strategy(COMPRESS).compression(limits);
        var middle = world.add(rect(30, 30)).strategy(RESIST).anchor(CENTER, START).priority(3);
        var right = world.add(rect(60, 30)).strategy(COMPRESS).anchor(END, START).compression(limits);
        world.update(0, true);
        middle.resize(50, 20);
        assertEquals(0, world.update(16, true));
        assertEquals(rect(0, 20), left.target());
        assertEquals(rect(20, 50), middle.target());
        assertEquals(rect(70, 20), right.target());
    }

    @Test
    void reportsViewportClampSeparatelyFromImpossibleConstraints() {
        var world = lane(100);
        var events = new ArrayList<CollisionEvent>();
        world.onCollision(events::add);
        var body = world.add(rect(90, 30)).compression(new CompressionModel(10, 20, 25, 20, 1));
        assertEquals(0, world.update(0, true));
        assertEquals(rect(75, 25), body.target());
        assertEquals(1, events.size());
        assertEquals(CollisionEvent.Type.BOUNDARY_CLAMPED, events.get(0).type());
        assertSame(body, events.get(0).first());
        assertNull(events.get(0).second());
    }

    @Test
    void canEscapeToTheOtherSideWhenAnObstaclePinsThePreferredDirection() {
        var world = lane(100);
        var fixed = world.add(rect(0, 30)).strategy(RESIST);
        var moving = world.add(rect(-10, 20)).priority(10);
        assertEquals(0, world.update(0, true));
        assertEquals(fixed.desired(), fixed.target());
        assertEquals(rect(30, 20), moving.target());
    }

    @Test
    void avoidUsesSideAxisAndResistKeepsItsPosition() {
        var world = new CollisionWorld(new CollisionShape(0, 0, 100, 100));
        var obstacle = world.add(new CollisionShape(20, 30, 30, 20)).strategy(RESIST);
        var moving = world.add(new CollisionShape(45, 31, 20, 20)).strategy(AVOID);
        assertEquals(0, world.update(0, true));
        assertEquals(obstacle.desired(), obstacle.target());
        assertEquals(new CollisionShape(45, 50, 20, 20), moving.target());
    }

    @Test
    void elasticCompressesAndThenPushesAroundAnObstacle() {
        var world = lane(100);
        world.add(rect(0, 50)).strategy(RESIST).priority(10);
        var elastic = world.add(rect(35, 25)).strategy(ELASTIC).anchor(END, START)
                .compression(new CompressionModel(20, 20, 30, 20, 1));
        assertEquals(0, world.update(0, true));
        assertEquals(rect(50, 20), elastic.target());
    }

    @Test
    void elasticCombinesBothCorrectionsWhenItsAnchorFacesAnObstacleAtTheEdge() {
        var world = lane(100);
        var elastic = world.add(rect(0, 40)).strategy(ELASTIC).anchor(END, START)
                .compression(new CompressionModel(20, 20, 40, 20, 1));
        var fixed = world.add(rect(35, 65)).strategy(RESIST);
        assertTrue(CollisionResolver.collides(elastic, elastic.desired(), fixed, fixed.desired()));
        assertEquals(0, world.update(0, true));
        assertEquals(rect(0, 35), elastic.target());
        assertEquals(fixed.desired(), fixed.target());
    }

    @Test
    void skipsUnchangedGroupsAndHandlesOverlayMigrationAndRemoval() {
        var world = lane(100);
        var events = new ArrayList<CollisionEvent>();
        world.onCollision(events::add);
        var obstacle = world.add(rect(0, 30)).strategy(RESIST);
        var overlay = world.add(rect(20, 30)).strategy(OVERLAY);
        var other = world.add(rect(20, 30)).group(1);
        assertEquals(0, world.update(0, true));
        assertTrue(events.isEmpty());
        other.group(0);
        world.update(1, true);
        assertEquals(rect(30, 30), other.target());
        assertEquals(rect(20, 30), overlay.target());
        assertEquals(1, events.size());
        world.add(rect(30, 30)).group(2);
        world.update(2, true);
        assertEquals(1, events.size());
        world.remove(obstacle);
        world.update(3, true);
        assertEquals(rect(20, 30), other.target());
    }

    @Test
    void impossibleLayoutPreservesHighestPriorityAndReportsRemainingConflicts() {
        var world = lane(50);
        var events = new ArrayList<CollisionEvent>();
        world.onCollision(events::add);
        var high = world.add(rect(0, 40)).priority(2);
        var low = world.add(rect(10, 40));
        assertEquals(1, world.update(0, true));
        assertEquals(high.desired(), high.target());
        assertEquals(low.desired(), low.target());
        assertEquals(1, events.stream().filter(e -> e.type() == CollisionEvent.Type.UNRESOLVED).count());
        low.compression(new CompressionModel(40, 20, 40, 20, 1));
        world.viewport(rect(0, 25));
        assertEquals(2, world.update(1, true));
        assertEquals(25, low.target().width());
        assertTrue(events.stream().anyMatch(e -> e.type() == CollisionEvent.Type.MINIMUM_CLIPPED));
    }

    @Test
    void simultaneousIntentChangesAreIndependentOfSetterOrder() {
        assertEquals(simultaneous(true), simultaneous(false));
    }

    @Test
    void emptyShapesAndTouchingEdgesDoNotIntersect() {
        assertFalse(rect(0, 10).intersects(rect(10, 20)));
        assertFalse(rect(0, 10).intersects(rect(4, 0)));
        assertTrue(rect(0, 10).contains(0, 0));
        assertFalse(rect(0, 10).contains(10, 0));
        assertThrows(IllegalArgumentException.class, () -> rect(Float.NaN, 10));
    }

    private static CollisionShape simultaneous(boolean firstLeft) {
        var world = lane(120);
        var left = world.add(rect(0, 30));
        var right = world.add(rect(90, 30));
        if (firstLeft) {
            left.moveTo(40, 0);
            right.moveTo(45, 0);
        } else {
            right.moveTo(45, 0);
            left.moveTo(40, 0);
        }
        assertEquals(0, world.update(0, true));
        assertEquals(rect(40, 30), left.target());
        return right.target();
    }

    private static CollisionWorld lane(float width) {
        return new CollisionWorld(rect(0, width)).axis(CollisionWorld.Axis.HORIZONTAL);
    }

    private static CollisionShape rect(float x, float width) {
        return new CollisionShape(x, 0, width, 20);
    }
}
