package com.dishanhai.sh_better_ui.client.gui.collision;

import static com.dishanhai.sh_better_ui.client.gui.collision.CollisionBody.Strategy.*;
import static com.dishanhai.sh_better_ui.client.gui.collision.CollisionShape.SOLVER_EPSILON;

/** 山海：成对选择分离轴和法向，按策略、优先级和剩余容量投影。 */
public final class CollisionResolver {
    private CollisionResolver() {}

    /** 检查指定 body 策略下两份几何是否参与成对碰撞。 */
    public static boolean collides(CollisionBody first, CollisionShape firstShape,
                                   CollisionBody second, CollisionShape secondShape) {
        return first != second && first.sharesWorldWith(second) && first.group() == second.group()
                && participates(first, second)
                && firstShape.intersects(secondShape);
    }

    static boolean overlaps(CollisionBody first, CollisionBody second) {
        return participates(first, second) && first.working.overlaps(second.working);
    }

    private static boolean participates(CollisionBody first, CollisionBody second) {
        return first.strategy() != OVERLAY && second.strategy() != OVERLAY;
    }

    // first precedes second in priority order. During fallback first is already placed.
    static boolean separate(CollisionBody first, CollisionBody second, CollisionShape viewport,
                            CollisionWorld.Axis axis, boolean fixedFirst, boolean relax) {
        boolean beforeX = compareOrder(first, second, true) < 0;
        boolean beforeY = compareOrder(first, second, false) < 0;
        if (relax || fixedFirst) {
            beforeX = viableDirection(first, second, viewport, true, beforeX, fixedFirst);
            beforeY = viableDirection(first, second, viewport, false, beforeY, fixedFirst);
        }
        boolean horizontal = switch (axis) {
            case HORIZONTAL -> true;
            case VERTICAL -> false;
            case AUTO -> chooseHorizontal(first, second, viewport, beforeX, beforeY, fixedFirst);
        };
        boolean firstBefore = horizontal ? beforeX : beforeY;
        float need = penetration(first, second, horizontal, firstBefore);
        float remaining = need;

        if (!fixedFirst && first.priority() == second.priority() && compresses(first) && compresses(second)) {
            float secondCapacity = shrinkCapacity(second, horizontal, !firstBefore);
            float firstCapacity = shrinkCapacity(first, horizontal, firstBefore);
            if (firstCapacity > 0 && secondCapacity > 0) {
                double firstResistance = first.compression().resistance();
                double secondResistance = second.compression().resistance();
                float firstShare = (float) (need * (secondResistance / (firstResistance + secondResistance)));
                remaining -= shrink(first, horizontal, firstBefore, Math.min(firstCapacity, firstShare));
                remaining -= shrink(second, horizontal, !firstBefore, Math.min(secondCapacity, remaining));
                remaining -= shrink(first, horizontal, firstBefore, remaining);
            }
        }

        if (relax && !fixedFirst && translates(first) && translates(second)) {
            remaining -= giveSpace(first, viewport, horizontal, firstBefore, remaining * 0.25f);
        }
        remaining -= giveSpace(second, viewport, horizontal, !firstBefore, remaining);
        if (!fixedFirst) remaining -= giveSpace(first, viewport, horizontal, firstBefore, remaining);
        return need - remaining > 0;
    }

    private static boolean chooseHorizontal(CollisionBody first, CollisionBody second, CollisionShape viewport,
                                            boolean beforeX, boolean beforeY, boolean fixedFirst) {
        float xNeed = penetration(first, second, true, beforeX);
        float yNeed = penetration(first, second, false, beforeY);
        float xSpace = pairCapacity(first, second, viewport, true, beforeX, fixedFirst);
        float ySpace = pairCapacity(first, second, viewport, false, beforeY, fixedFirst);
        boolean canX = xSpace + SOLVER_EPSILON >= xNeed;
        boolean canY = ySpace + SOLVER_EPSILON >= yNeed;
        if (canX != canY) return canX;
        if (canX && (first.strategy() == AVOID || second.strategy() == AVOID)) {
            float centerDx = Math.abs(first.solved.center(true) - second.solved.center(true));
            float centerDy = Math.abs(first.solved.center(false) - second.solved.center(false));
            if (centerDx != centerDy) return centerDy > centerDx;
        }
        if (canX) return xNeed <= yNeed;
        float xProgress = Math.min(xSpace, xNeed);
        float yProgress = Math.min(ySpace, yNeed);
        if (xProgress == 0 || yProgress == 0) return xProgress >= yProgress;
        return xNeed - xProgress <= yNeed - yProgress;
    }

    private static boolean viableDirection(CollisionBody first, CollisionBody second, CollisionShape viewport,
                                           boolean horizontal, boolean firstBefore, boolean fixedFirst) {
        float preferred = pairCapacity(first, second, viewport, horizontal, firstBefore, fixedFirst);
        if (preferred + SOLVER_EPSILON >= penetration(first, second, horizontal, firstBefore)) return firstBefore;
        float opposite = pairCapacity(first, second, viewport, horizontal, !firstBefore, fixedFirst);
        return opposite + SOLVER_EPSILON >= penetration(first, second, horizontal, !firstBefore)
                ? !firstBefore : firstBefore;
    }

    private static float pairCapacity(CollisionBody first, CollisionBody second, CollisionShape viewport,
                                      boolean horizontal, boolean firstBefore, boolean fixedFirst) {
        return capacity(second, viewport, horizontal, !firstBefore)
                + (fixedFirst ? 0 : capacity(first, viewport, horizontal, firstBefore));
    }

    static int compareOrder(CollisionBody first, CollisionBody second, boolean horizontal) {
        int previous = Float.compare(first.solved.center(horizontal), second.solved.center(horizontal));
        if (previous != 0) return previous;
        int desired = Float.compare(center(first.desired(), horizontal), center(second.desired(), horizontal));
        return desired != 0 ? desired : Long.compare(first.order, second.order);
    }

    private static float center(CollisionShape shape, boolean horizontal) {
        return horizontal ? shape.x() + shape.width() * 0.5f : shape.y() + shape.height() * 0.5f;
    }

    private static float penetration(CollisionBody first, CollisionBody second, boolean horizontal,
                                     boolean firstBefore) {
        return firstBefore ? first.working.end(horizontal) - second.working.start(horizontal)
                : second.working.end(horizontal) - first.working.start(horizontal);
    }

    private static float capacity(CollisionBody body, CollisionShape viewport, boolean horizontal, boolean negative) {
        float movement = translates(body) ? moveCapacity(body.working, viewport, horizontal, negative) : 0;
        if (body.strategy() == ELASTIC) return movement + shrinkRoom(body, horizontal);
        return compresses(body) ? shrinkCapacity(body, horizontal, negative) : movement;
    }

    private static float giveSpace(CollisionBody body, CollisionShape viewport, boolean horizontal, boolean negative,
                                   float need) {
        if (need <= 0) return 0;
        float shrunk = compresses(body) ? shrink(body, horizontal, negative, need) : 0;
        if (!translates(body) || need <= shrunk) return shrunk;
        var box = body.working;
        float edge = negative ? box.end(horizontal) : box.start(horizontal);
        float moved = Math.min(need - shrunk, moveCapacity(box, viewport, horizontal, negative));
        box.setAxis(horizontal, box.start(horizontal) + (negative ? -moved : moved), box.size(horizontal));
        if (body.strategy() == ELASTIC) {
            float progress = negative ? edge - box.end(horizontal) : box.start(horizontal) - edge;
            float extra = Math.min(need - shrunk - progress, shrinkRoom(body, horizontal));
            if (extra > 0) {
                // The viewport blocks further translation. Shrink and translate together
                // so that the boundary-side edge remains fixed.
                box.setAxis(horizontal, box.start(horizontal) + (negative ? 0 : extra), box.size(horizontal) - extra);
            }
        }
        return shrunk + (negative ? edge - box.end(horizontal) : box.start(horizontal) - edge);
    }

    private static boolean compresses(CollisionBody body) {
        return body.strategy() == COMPRESS || body.strategy() == ELASTIC;
    }

    private static boolean translates(CollisionBody body) {
        return body.strategy() == PUSH || body.strategy() == AVOID || body.strategy() == ELASTIC;
    }

    private static float moveCapacity(MutableCollisionBounds box, CollisionShape viewport, boolean horizontal,
                                      boolean negative) {
        float viewportStart = horizontal ? viewport.x() : viewport.y();
        float viewportEnd = horizontal ? viewport.right() : viewport.bottom();
        return Math.max(0, negative ? box.start(horizontal) - viewportStart : viewportEnd - box.end(horizontal));
    }

    private static float shrinkCapacity(CollisionBody body, boolean horizontal, boolean negative) {
        float anchor = body.anchorFraction(horizontal);
        return shrinkRoom(body, horizontal) * (negative ? 1 - anchor : anchor);
    }

    private static float shrinkRoom(CollisionBody body, boolean horizontal) {
        float minimum = horizontal ? body.compression().minWidth() : body.compression().minHeight();
        return Math.max(0, body.working.size(horizontal) - minimum);
    }

    private static float shrink(CollisionBody body, boolean horizontal, boolean negative, float need) {
        float allowed = Math.min(need, shrinkCapacity(body, horizontal, negative));
        if (allowed <= 0) return 0;
        var box = body.working;
        float edge = negative ? box.end(horizontal) : box.start(horizontal);
        float anchor = body.anchorFraction(horizontal);
        float minimum = horizontal ? body.compression().minWidth() : body.compression().minHeight();
        float size = Math.max(minimum, box.size(horizontal) - allowed / (negative ? 1 - anchor : anchor));
        float amount = box.size(horizontal) - size;
        box.setAxis(horizontal, box.start(horizontal) + amount * anchor, size);
        return negative ? edge - box.end(horizontal) : box.start(horizontal) - edge;
    }
}
