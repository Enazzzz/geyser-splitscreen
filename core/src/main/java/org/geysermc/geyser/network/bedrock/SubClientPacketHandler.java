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

import org.cloudburstmc.protocol.bedrock.packet.PlayStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackClientResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.SubClientLoginPacket;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.event.bedrock.SessionInitializeEvent;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.auth.AuthData;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

/**
 * Logs the second splitscreen controller in as its own Java player.
 * The console does not send an Xbox identity for that controller, so this
 * household's second player is always UnicornKiddy.
 * The 2022 splitscreen change did the same job by mapping a console profile
 * to a Bedrock name and opening a second session on the shared connection.
 */
public class SubClientPacketHandler extends UpstreamPacketHandler {
	/** Bedrock name. Floodgate adds its usual prefix on the Java side. */
	static final String PLAYER_NAME = "UnicornKiddy";
	/**
	 * The Xbox id already used by .UnicornKiddy. Keeping it means this controller
	 * is that same player, and Floodgate builds the same Java UUID.
	 */
	static final long PLAYER_XUID = 2535448473593441L;

	private boolean started;

	public SubClientPacketHandler(GeyserImpl geyser, GeyserSession session) {
		super(geyser, session);
	}

	@Override
	public PacketSignal handle(SubClientLoginPacket packet) {
		if (started || session.isClosed()) {
			return PacketSignal.HANDLED;
		}

		GeyserBedrockPeer peer = (GeyserBedrockPeer) session.getUpstream().getSession().getPeer();
		GeyserSession primary = peer.primarySession();
		if (primary == null || primary.getClientData() == null || primary.getBlockMappings() == null || primary.getItemMappings() == null) {
			session.disconnect("The first player is still joining. Try the second controller again in a moment.");
			return PacketSignal.HANDLED;
		}

		started = true;
		String name = splitscreenName();
		long xuidValue = splitscreenXuid(name);
		String xuid = Long.toString(xuidValue);
		session.setAuthData(new AuthData(name, new UUID(0, xuidValue), xuid, -1, ""));
		// Language, device, and input mode are read from the main controller.
		// A guest controller does not send its own copy.
		session.setClientData(primary.getClientData());
		session.setBlockMappings(primary.getBlockMappings());
		session.setItemMappings(primary.getItemMappings());

		if (geyser.getSessionManager().isXuidAlreadyPending(xuid) || geyser.getSessionManager().sessionByXuid(xuid) != null) {
			session.disconnect(name + " is already on the server.");
			return PacketSignal.HANDLED;
		}

		geyser.getSessionManager().addPendingSession(session);
		geyser.eventBus().fire(new SessionInitializeEvent(session));
		if (session.isClosed()) {
			return PacketSignal.HANDLED;
		}

		PlayStatusPacket playStatus = new PlayStatusPacket();
		playStatus.setStatus(PlayStatusPacket.Status.LOGIN_SUCCESS);
		session.sendUpstreamPacket(playStatus);

		int controller = session.getUpstream().getSession() instanceof SplitscreenServerSession splitscreen
			? splitscreen.controllerId()
			: -1;
		geyser.getLogger().info("Splitscreen controller " + controller + " is joining as " + name);
		// Packs were already accepted by the first controller on this connection.
		session.authenticate(name);
		return PacketSignal.HANDLED;
	}

	/**
	 * The name chosen on the house desk. Unicornkiddy is the default.
	 */
	private static String splitscreenName() {
		Path file = Path.of("plugins", "FamilyDesk", "splitscreen-name.txt");
		try {
			String name = Files.readString(file).trim();
			if (name.matches("[A-Za-z0-9_]{3,16}")) {
				return name;
			}
		} catch (IOException ignored) {
			// The desk has not written a name yet.
		}
		return PLAYER_NAME;
	}

	/**
	 * A stable Xbox id. UnicornKiddy keeps the account that already plays
	 * under that name. Other names get their own.
	 */
	private static long splitscreenXuid(String name) {
		if (PLAYER_NAME.equalsIgnoreCase(name)) {
			return PLAYER_XUID;
		}
		long hash = name.toLowerCase(Locale.ROOT).hashCode() & 0xffffffffL;
		return 9100000000000000L + (hash % 1000000L);
	}

	/**
	 * The second controller does not download packs of its own. Accepting one of
	 * these after login would open a second Java connection.
	 */
	@Override
	public PacketSignal handle(ResourcePackClientResponsePacket packet) {
		return PacketSignal.HANDLED;
	}
}
