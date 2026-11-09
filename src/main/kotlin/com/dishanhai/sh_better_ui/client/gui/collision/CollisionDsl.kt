package com.dishanhai.sh_better_ui.client.gui.collision

/** 山海：可选的 Kotlin 接收者式配置，核心调用者可直接使用 Java API。 */
fun CollisionWorld.collisionBody(
    shape: CollisionShape,
    configure: CollisionBody.() -> Unit = {}
): CollisionBody {
    val body = add(shape)
    try {
        body.configure()
    } catch (error: Throwable) {
        remove(body)
        throw error
    }
    return body
}
