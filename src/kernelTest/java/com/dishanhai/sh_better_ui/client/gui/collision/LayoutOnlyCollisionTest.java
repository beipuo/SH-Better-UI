// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.collision;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LayoutOnlyCollisionTest {
    @Test void layoutOnlyModeReusesTheSolverWithoutAllocatingLiveAnimationChannels() {
        var viewport = new CollisionShape(0, 0, 400, 240);
        var normal = new CollisionWorld(viewport);
        var analysis = CollisionWorld.layoutOnly(viewport, new LayoutSolver(64));
        var a = normal.add(new CollisionShape(100, 20, 120, 40)).strategy(CollisionBody.Strategy.ELASTIC);
        var b = analysis.add(new CollisionShape(100, 20, 120, 40)).strategy(CollisionBody.Strategy.ELASTIC);
        assertNotNull(a.transition);
        assertNull(b.transition);
        normal.update(0, true); analysis.update(0, true);
        assertEquals(a.target(), b.target());
        a.moveTo(240, 50); b.moveTo(240, 50);
        normal.update(16, false); analysis.update(16, false);
        assertEquals(a.target(), b.target());
        assertEquals(b.target(), b.visual());
        assertEquals(b.target().x(), b.visualX());
        assertEquals(b.target().y(), b.visualY());
        assertEquals(b.target().right(), b.visualRight());
        assertEquals(b.target().bottom(), b.visualBottom());
        assertFalse(analysis.isAnimating());
        assertNotEquals(a.visual(), b.visual());
    }
    @Test void bothModesReturnTheSameProtectedObstacleSolution() {
        var viewport = new CollisionShape(0, 0, 400, 240);
        var normal = new CollisionWorld(viewport);
        var analysis = CollisionWorld.layoutOnly(viewport, new LayoutSolver(64));
        normal.add(new CollisionShape(100, 0, 200, 45)).strategy(CollisionBody.Strategy.RESIST).priority(Integer.MAX_VALUE);
        analysis.add(new CollisionShape(100, 0, 200, 45)).strategy(CollisionBody.Strategy.RESIST).priority(Integer.MAX_VALUE);
        var a = normal.add(new CollisionShape(150, 4, 100, 30)).strategy(CollisionBody.Strategy.ELASTIC).priority(Integer.MIN_VALUE);
        var b = analysis.add(new CollisionShape(150, 4, 100, 30)).strategy(CollisionBody.Strategy.ELASTIC).priority(Integer.MIN_VALUE);
        assertEquals(normal.update(0, true), analysis.update(0, true));
        assertEquals(a.target(), b.target());
        normal.clear(); analysis.clear();
        assertFalse(normal.contains(a)); assertFalse(analysis.contains(b));
        assertEquals(a.target(), b.visual());
    }
}
