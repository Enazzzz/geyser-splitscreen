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

import io.netty.channel.Channel;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.BedrockSession;
import org.cloudburstmc.protocol.bedrock.BedrockSessionFactory;
import org.cloudburstmc.protocol.bedrock.netty.BedrockPacketWrapper;
import org.cloudburstmc.protocol.bedrock.packet.SubClientLoginPacket;
import org.geysermc.geyser.session.GeyserSession;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;

public class GeyserBedrockPeer extends BedrockPeer {
    private SocketAddress proxiedAddress;
    /**
     * The controller that opened the connection. Splitscreen sessions copy its
     * version and client data from here.
     */
    private GeyserSession primarySession;

    public GeyserBedrockPeer(Channel channel, BedrockSessionFactory sessionFactory) {
        super(channel, sessionFactory);
    }

    /**
     * Records the first controller on this connection.
     */
    public void setPrimarySession(GeyserSession primarySession) {
        this.primarySession = primarySession;
    }

    /**
     * @return the first controller, or null before its session exists
     */
    public GeyserSession primarySession() {
        return this.primarySession;
    }

    /**
     * Routes a packet to the controller that sent it.
     * The stock peer picks a session by the target id. A PlayStation controller
     * sends its own id as the sender and addresses the server as the target, so
     * the second controller would otherwise land on the first player's session.
     */
    @Override
    protected void onBedrockPacket(BedrockPacketWrapper wrapper) {
        int sender = Byte.toUnsignedInt(wrapper.getSenderSubClientId());
        int target = Byte.toUnsignedInt(wrapper.getTargetSubClientId());
        // Any packet from a guest controller belongs to that controller, even when
        // the target id is not zero. Dropping those was swallowing tame and saddle clicks.
        if (sender != 0) {
            BedrockSession existing = this.sessions.get(sender);
            if (existing == null) {
                if (!(wrapper.getPacket() instanceof SubClientLoginPacket)) {
                    return;
                }
                existing = this.onSessionCreated(sender);
                this.sessions.put(sender, existing);
            }
            // onPacket is not visible from this package. The handler is already installed.
            if (existing.getPacketHandler() != null && wrapper.getPacket() != null) {
                existing.getPacketHandler().handlePacket(wrapper.getPacket());
            }
            return;
        }
        super.onBedrockPacket(wrapper);
    }

    /**
     * The Java sessions riding on this one console connection.
     */
    public List<GeyserSession> controllerSessions() {
        List<GeyserSession> found = new ArrayList<>();
        for (BedrockSession bedrock : this.sessions.values()) {
            if (bedrock.getPacketHandler() instanceof LoggingPacketHandler handler) {
                found.add(handler.session);
            }
        }
        return found;
    }

    public SocketAddress getRealAddress() {
        SocketAddress proxied = this.proxiedAddress;
        return proxied == null ? this.getSocketAddress() : proxied;
    }

    public void setProxiedAddress(SocketAddress proxiedAddress) {
        this.proxiedAddress = proxiedAddress;
    }
}
