/*
 * Copyright (c) 2019-2026 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package org.geysermc.geyser.network.bedrock;

import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.session.GeyserSession;

/**
 * Looks up an entity for a splitscreen controller.
 * The guest and the first player share a connection, so a click can name an id
 * that was spawned on the other controller. The Java entity is what the server uses.
 */
public final class SplitscreenEntities {
	private SplitscreenEntities() {
	}

	/**
	 * The entity in this controller's own cache, or the same Java entity if the
	 * id belonged to the other controller.
	 */
	public static Entity find(GeyserSession session, long runtimeId) {
		if (runtimeId == session.getPlayerEntity().geyserId()) {
			return session.getPlayerEntity();
		}
		Entity local = session.getEntityCache().getEntityByGeyserId(runtimeId);
		if (local != null) {
			return local;
		}
		Entity other = sibling(session, runtimeId);
		if (other == null || other.getEntityId() < 0) {
			return null;
		}
		return session.getEntityCache().getEntityByJavaId(other.getEntityId());
	}

	/**
	 * The Java entity id for a click, even when this controller has not spawned
	 * its own copy of that entity yet.
	 */
	public static int javaId(GeyserSession session, long runtimeId) {
		Entity found = find(session, runtimeId);
		if (found != null && found.getEntityId() >= 0) {
			return found.getEntityId();
		}
		Entity other = sibling(session, runtimeId);
		if (other == null) {
			return -1;
		}
		return other.getEntityId();
	}

	/**
	 * An entity with this runtime id on another controller of the same console.
	 */
	private static Entity sibling(GeyserSession session, long runtimeId) {
		if (!(session.getUpstream().getSession().getPeer() instanceof GeyserBedrockPeer peer)) {
			return null;
		}
		for (GeyserSession other : peer.controllerSessions()) {
			if (other == session) {
				continue;
			}
			if (runtimeId == other.getPlayerEntity().geyserId()) {
				return other.getPlayerEntity();
			}
			Entity entity = other.getEntityCache().getEntityByGeyserId(runtimeId);
			if (entity != null) {
				return entity;
			}
		}
		return null;
	}
}
