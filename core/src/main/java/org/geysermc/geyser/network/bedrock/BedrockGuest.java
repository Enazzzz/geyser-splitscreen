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

import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.auth.AuthData;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Lets one Bedrock login play as a name chosen with /guest.
 * The splitscreen controller is left alone. Java players never reach this.
 */
public final class BedrockGuest {
	private static final Path FILE = Path.of("plugins", "FamilyDesk", "bedrock-guest.txt");

	private BedrockGuest() {
	}

	/**
	 * Replace this login's Xbox name when a guest name is waiting and that
	 * player is not already on the server.
	 */
	public static void apply(GeyserSession session) {
		if (session.getUpstream().getSession() instanceof SplitscreenServerSession splitscreen && splitscreen.controllerId() != 0) {
			return;
		}
		AuthData current = session.getAuthData();
		if (current == null) {
			return;
		}
		String saved;
		try {
			saved = Files.readString(FILE).trim();
		} catch (IOException ignored) {
			return;
		}
		if (saved.isEmpty()) {
			return;
		}
		String[] parts = saved.split("\t");
		if (parts.length != 2 || !parts[0].matches("[A-Za-z0-9_]{3,16}")) {
			return;
		}
		long xuid;
		try {
			xuid = Long.parseLong(parts[1]);
		} catch (NumberFormatException ignored) {
			return;
		}
		String xuidText = Long.toString(xuid);
		GeyserImpl geyser = GeyserImpl.getInstance();
		if (geyser.getSessionManager().isXuidAlreadyPending(xuidText) || geyser.getSessionManager().sessionByXuid(xuidText) != null) {
			geyser.getLogger().info(current.name() + " joined as themselves because " + parts[0] + " is already on");
			return;
		}
		session.setAuthData(new AuthData(parts[0], new UUID(0, xuid), xuidText, current.issuedAt(), current.playFabId()));
		geyser.getLogger().info(current.name() + " is joining as the Bedrock guest " + parts[0]);
	}
}
